
import java.io.*;
import java.time.*;
import java.util.*;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.*;
import org.apache.http.impl.client.*;
import org.apache.http.message.BasicHeader;
import org.apache.http.util.EntityUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * query twitch to see who is live
 */
public class TwitchQuery {
	
	private static final String CLIENTID = "clientid", DELAY = "delay", COLS = "cols", STREAMERS = "s.", GAMEIGNORE = "gi.";
	
	private Map<Integer,Integer> responses = new TreeMap<>();
	private Set<String> streamIgnored = new TreeSet<>();
	private Set<String> streamers, gameignore;
	private String clientid;
	private int delayms;
	private int cols;
	
	public static void main (String[] args) throws Exception {
		File propsfile = args.length > 0 ? new File(args[0]) : null;
		
		try (CloseableHttpClient client = HttpClients.createDefault()) {
			String cmd = null;
			while (true) {
				try {
					TwitchQuery q = create(propsfile);
					q.run(client, StringUtils.defaultString(cmd, "streams"));
				} catch (RuntimeException e) {
					throw e;
				} catch (Exception e) {
					Main.println("could not run: " + e);
				}
				cmd = StringUtils.trimToNull(Main.readLine());
			}
		}
	}

	public static TwitchQuery create (File propsfile) throws IOException {
		TwitchQuery q = new TwitchQuery();
		Properties props = Main.loadProps(propsfile != null ? propsfile : new File("twitch.properties"));
		q.clientid = props.getProperty(CLIENTID);
		q.streamers = getset(props, STREAMERS);
		q.gameignore = getset(props, GAMEIGNORE);
		q.cols = Integer.parseInt(props.getProperty(COLS, "80"));
		q.delayms = Integer.parseInt(props.getProperty(DELAY, "100"));
		return q;
	}
	
	public TwitchQuery () {
		//
	}
	
	private static Set<String> getset (Properties p, String pre) {
		Set<String> set = new TreeSet<>();
		for (String k : p.stringPropertyNames()) {
			if (k.startsWith(pre)) {
				String v = StringUtils.trimToNull(p.getProperty(k).toLowerCase());
				if (v != null) {
					set.add(v);
				}
			}
		}
		return set;
	}
	
	public void run (CloseableHttpClient client, String cmd) throws Exception {
		Main.println(cmd);
		for (String user : streamers) {
			switch (cmd) {
				case "streams": printStream(client, user); break;
				case "channels": printChannel(client, user); break;
				default: Main.println("unknown cmd: " + cmd); return;
			}
			Thread.sleep(delayms);
		}
		System.out.println(responses + ", " + streamIgnored);
	}
	
	private void printChannel (CloseableHttpClient client, String user) throws Exception {
		HttpGet get = new HttpGet("https://api.twitch.tv/kraken/channels/" + user);
		get.addHeader(new BasicHeader("client-id", clientid));
		try (CloseableHttpResponse resp = client.execute(get)) {
			String content = EntityUtils.toString(resp.getEntity());
			if (resp.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
				String info = chaninfo(user, content);
				System.out.println(StringUtils.left(info, cols-1));
			} else {
				Main.println("printChannel: " + user + ": " + resp.getStatusLine());
			}
		}
	}
	
	private void printStream (CloseableHttpClient client, String user) throws Exception {
		Stream stream = queryStream(client, user);
		if (stream != null) {
			if (stream.live() && !gameignore.contains(stream.game.toLowerCase())) {
				System.out.println(StringUtils.left(stream.toString(), cols-1));
			} else {
				streamIgnored.add(stream.name);
			}
		} 
	}
	
	public Stream queryStream (CloseableHttpClient client, String user) throws Exception {
		HttpGet get = new HttpGet("https://api.twitch.tv/kraken/streams/" + user);
		get.addHeader(new BasicHeader("client-id", clientid));
		try (CloseableHttpResponse resp = client.execute(get)) {
			addresponse(resp);
			String content = EntityUtils.toString(resp.getEntity());
			if (resp.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
				return parseStream(content);
			} else {
				Main.println("queryStream: " + user + ": " + resp.getStatusLine());
				return null;
			}
		}
	}
	
	private void addresponse (CloseableHttpResponse resp) {
		Integer c = Integer.valueOf(resp.getStatusLine().getStatusCode());
		responses.compute(c, (k,v) -> Integer.valueOf((v != null ? v.intValue() : 0) + 1));
	}
	
	private static String chaninfo (String user, String content) throws IOException {
		ObjectMapper mapper = new ObjectMapper();
		JsonNode node = mapper.readTree(content);
		JsonNode namenode = node.get("display_name");
		return user + " - " + (namenode != null ? namenode.asText() : null);
	}
	
	private static Stream parseStream (String content) throws IOException {
		ObjectMapper mapper = new ObjectMapper();
		JsonNode node = mapper.readTree(content);
		JsonNode stream = node.get("stream");
		return stream != null && !stream.isNull() ? new Stream(stream) : null;
	}
	
	static String formatDuration (Duration dur) {
		long d = dur.toDays(), h = dur.toHours() % 24, m = dur.toMinutes() % 60, s = dur.getSeconds() % 60;
		if (d > 0) {
			return String.format("%dd %dh %dm", d, h, m);
		} else if (h > 0) {
			return String.format("%dh %dm", h, m);
		} else {
			return String.format("%dm", m);
		}
	}
	
}
