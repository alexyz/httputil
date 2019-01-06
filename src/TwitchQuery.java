
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
	
	private static BufferedReader reader;
	private static File propsfile;
	
	private Map<Integer,Integer> responses = new TreeMap<>();
	private Set<String> signored = new TreeSet<>();
	private Set<String> streamers, gameignore;
	private String clientid;
	private int delayms;
	private int cols;
	
	public static void main (String[] args) throws Exception {
		reader = new BufferedReader(new InputStreamReader(System.in));
		propsfile = new File(args.length > 0 ? args[0] : "twitch.properties");
		
		while (true) {
			TwitchQuery q = new TwitchQuery();
			Properties props = loadprops();
			q.clientid = props.getProperty("clientid");
			q.streamers = getset(props, "s.");
			q.gameignore = getset(props, "gi.");
			q.cols = Integer.parseInt(props.getProperty("cols", "80"));
			q.delayms = Integer.parseInt(props.getProperty("delay", "100"));
			
			System.out.println(new Date());
			String cmd = "";
			try (CloseableHttpClient client = HttpClients.createDefault()) {
				q.run(client, cmd);
				cmd = reader.readLine();
			} catch (Exception e) {
				e.printStackTrace(System.out);
			}
		}
		
	}
	
	public TwitchQuery () {
		//
	}
	
	public static Properties loadprops () throws IOException {
		Properties p = new Properties();
		try (FileInputStream is = new FileInputStream(propsfile)) {
			p.load(is);
		}
		return p;
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
	
	private void run (CloseableHttpClient client, String cmd) throws Exception {
		for (String user : streamers) {
			switch (cmd) {
				case "": printstreams(client, user); break;
				case "channels": printchannels(client, user); break;
				default: System.out.println("unknown cmd: " + cmd); return;
			}
			Thread.sleep(delayms);
		}
		System.out.println(responses + ", " + signored);
	}
	
	private void printchannels (CloseableHttpClient client, String user) {
		HttpGet get = new HttpGet("https://api.twitch.tv/kraken/channels/" + user);
		get.addHeader(new BasicHeader("client-id", clientid));
		try (CloseableHttpResponse resp = client.execute(get)) {
			String content = EntityUtils.toString(resp.getEntity());
			if (resp.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
				String info = chaninfo(user, content);
				System.out.println(StringUtils.left(info, cols-1));
			} else {
				System.out.println("channels: " + user + ": " + resp.getStatusLine());
			}
		} catch (Exception e) {
			System.out.println("channels: " + user + ": " + e);
		}
	}
	
	private void printstreams (CloseableHttpClient client, String user) {
		HttpGet get = new HttpGet("https://api.twitch.tv/kraken/streams/" + user);
		get.addHeader(new BasicHeader("client-id", clientid));
		try (CloseableHttpResponse resp = client.execute(get)) {
			addresponse(resp);
			String content = EntityUtils.toString(resp.getEntity());
			if (resp.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
				String info = streaminfo(content);
				if (info != null) {
					System.out.println(StringUtils.left(info, cols-1));
				}
			} else {
				System.out.println("streams: " + user + ": " + resp);
			}
		} catch (Exception e) {
			System.out.println("streams: " + user + ": " + e);
			//throw new RuntimeException("streams: " + user, e);
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
	
	private String streaminfo (String content) throws IOException {
		ObjectMapper mapper = new ObjectMapper();
		JsonNode node = mapper.readTree(content);
		JsonNode stream = node.get("stream");
		if (stream != null && !stream.isNull()) {
			JsonNode channel = stream.get("channel");
			String name = channel.get("display_name").asText();
			String status = StringUtils.normalizeSpace(channel.get("status").asText());
			String game = stream.get("game").asText();
			int view = stream.get("viewers").asInt();
			// "2018-05-27T04:49:48Z"
			Instant created = Instant.parse(stream.get("created_at").asText());
			Duration dur = Duration.between(created, Instant.now());
			String viewStr = String.format("%dv", view);
			String durStr = formatDuration(dur);
			//boolean playlist = stream.get("is_playlist").asBoolean();
			String type = stream.get("stream_type").asText();
			if (type.equals("live") && !gameignore.contains(game.toLowerCase())) {
				return String.format("%s - %s - %s - %s - %s", name, durStr, viewStr, game, status, game);
			} else {
				signored.add(name);
			}
		}
		return null;
	}
	
	private static String formatDuration (Duration dur) {
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
