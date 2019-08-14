
import java.io.*;
import java.util.*;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
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
	
	public static final String CLIENTID = "clientid", DELAY = "delay", COLS = "cols", STREAMER = "s.", GAMEIGNORE = "gi.";
	public static final String STATS = "stats", ASYNC_STREAMER = "as.", ASYNC = "async", ASYNCPERIOD = "asyncperiod", RESENDPERIOD = "resendperiod", MAILTO = "mailto";
	public static final String MAILSTREAMS_CMD = "m", STREAMS_CMD = "s", CHANNELS_CMD = "c";
	private static final Map<String,Long> SEEN = new TreeMap<>();
	
	public static TwitchThread thread;
	
	public static void main (String[] args) throws Exception {
		try (CloseableHttpClient client = HttpClients.createDefault()) {
			// first run - should really verify channels are correct
			String cmd = null;
			while (true) {
				try {
					TwitchQuery q = create();
					q.loadStats();
					q.updateAsync();
					q.run(client, StringUtils.defaultString(cmd, STREAMS_CMD));
					q.saveStats();
				} catch (RuntimeException e) {
					throw e;
				} catch (Exception e) {
					Main.println("main: " + e);
				}
				cmd = StringUtils.trimToNull(Main.readLine());
			}
		}
	}
	
	public static TwitchQuery create () throws IOException {
		return new TwitchQuery(Main.loadProps(new File("twitch.properties")));
	}
	
	private final Map<Integer,Integer> responses = new TreeMap<>();
	private final List<Stream> liveOk = new ArrayList<>(), liveNotOk = new ArrayList<>();
	private final Set<String> streamers = new TreeSet<>();
	private final Set<String> asyncStreamers = new TreeSet<>();
	private final Set<String> gameIgnore = new TreeSet<>();
	private final Properties seenProps = new Properties();
	private String clientId, mailto;
	private int delayMs, asyncPeriod, resendPeriod, cols;
	private File statsFile;
	private boolean seenMod, async;
	
	private TwitchQuery (Properties props) {
		clientId = props.getProperty(CLIENTID);
		streamers.addAll(Main.getPropValues(props, STREAMER));
		asyncStreamers.addAll(Main.getPropValues(props, ASYNC_STREAMER));
		gameIgnore.addAll(Main.getPropValues(props, GAMEIGNORE));
		cols = Integer.parseInt(props.getProperty(COLS, "80"));
		delayMs = Integer.parseInt(props.getProperty(DELAY, "100"));
		String stats = props.getProperty(STATS);
		statsFile = stats != null && stats.length() > 0 ? new File(stats) : null;
		async = Boolean.parseBoolean(props.getProperty(ASYNC));
		asyncPeriod = Integer.parseInt(props.getProperty(ASYNCPERIOD, String.valueOf(15*60)));
		resendPeriod = Integer.parseInt(props.getProperty(RESENDPERIOD, String.valueOf(12*60*60)));
		mailto = props.getProperty(MAILTO);
	}
	
	public void run (CloseableHttpClient client, String cmd) throws Exception {
		switch (cmd) {
			case STREAMS_CMD: printStreams(client); break;
			case CHANNELS_CMD: printChannels(client); break;
			case MAILSTREAMS_CMD: mailStreams(client); break;
			default: Main.println("unknown cmd: " + cmd); return;
		}
	}
	
	private void printChannels (CloseableHttpClient client) throws IOException {
		Main.println("channels");
		for (String user : streamers) {
			HttpGet get = new HttpGet("https://api.twitch.tv/kraken/channels/" + user);
			get.addHeader(new BasicHeader("client-id", clientId));
			try (CloseableHttpResponse resp = client.execute(get)) {
				String content = EntityUtils.toString(resp.getEntity());
				if (resp.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
					String info = channelInfo(user, content);
					System.out.println(StringUtils.left(info, cols-1));
				} else {
					Main.println(user + ": " + resp.getStatusLine());
				}
			}
			Main.sleep(delayMs);
		}
		System.out.println("responses=" + responses);
	}
	
	private void printStreams (CloseableHttpClient client) throws IOException {
		Main.println("streams");
		for (String user : streamers) {
			Stream stream = queryStream(client, user);
			if (liveOk.contains(stream)) {
				System.out.println(StringUtils.left(stream.toString(), cols - 1));
			}
			Main.sleep(delayMs);
		}
		System.out.println("responses=" + responses + " ignored=" + liveNotOk.size());
		updateSeen();
	}
	
	public Stream queryStream (CloseableHttpClient client, String user) throws IOException {
		HttpGet get = new HttpGet("https://api.twitch.tv/kraken/streams/" + user);
		get.addHeader(new BasicHeader("client-id", clientId));
		try (CloseableHttpResponse resp = client.execute(get)) {
			updateResponse(resp);
			String content = EntityUtils.toString(resp.getEntity());
			if (resp.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
				Stream s = parseStream(content);
				updateStream(s);
				return s;
			} else {
				Main.println(user + ": " + resp.getStatusLine());
				return null;
			}
		}
	}
	
	private void updateResponse (CloseableHttpResponse resp) {
		Integer c = Integer.valueOf(resp.getStatusLine().getStatusCode());
		responses.compute(c, (k,v) -> Integer.valueOf((v != null ? v.intValue() : 0) + 1));
	}
	
	private String channelInfo (String user, String content) throws IOException {
		ObjectMapper mapper = new ObjectMapper();
		JsonNode node = mapper.readTree(content);
		JsonNode namenode = node.get("display_name");
		return user + " - " + (namenode != null ? namenode.asText() : null);
	}
	
	private Stream parseStream (String content) throws IOException {
		ObjectMapper mapper = new ObjectMapper();
		JsonNode node = mapper.readTree(content);
		JsonNode stream = node.get("stream");
		return stream != null && !stream.isNull() ? new Stream(stream) : null;
	}
	
	private void mailStreams (CloseableHttpClient client) throws IOException {
		if (!StringUtils.contains(mailto, "@") || asyncStreamers.size() == 0) {
			Main.println("mail streams invalid config: t=" + mailto + ", as=" + asyncStreamers.size());
			return;
		}
		
		for (String user : asyncStreamers) {
			queryStream(client, user);
			Main.sleep(delayMs);	
		}
		
		if (checkNotSeen()) {
			//Main.println("mail streams send");
			StringBuilder subSb = new StringBuilder("Streamers live: ");
			StringBuilder textSb = new StringBuilder();
			for (Stream s : liveOk) {
				if (subSb.length() > 0) {
					subSb.append(", ");
				}
				subSb.append(s.name);
				textSb.append(s.toString()).append("\n");
			}
			textSb.append("responses: " + responses + "\n");
			textSb.append("ignored: " + liveNotOk.size() + "\n");
			SendMail sm = SendMail.create();
			sm.send(mailto, subSb.toString(), textSb.toString());
			updateSeen();
		}
	}
	
	private void updateStream (Stream s) {
		if (s != null && s.live()) {
			seenProps.setProperty(s.name.toLowerCase(),  Main.dateFormat().format(new Date()));
			seenMod = true;
			if (gameIgnore.contains(s.game.toLowerCase())) {
				liveNotOk.add(s);
			} else {
				liveOk.add(s);
			}
		}
	}
	
	public void loadStats () throws IOException {
		if (statsFile != null && statsFile.exists()) {
			try (FileInputStream is = new FileInputStream(statsFile)) {
				seenProps.load(is);
			}
		}
	}
	
	public void saveStats() throws IOException {
		if (statsFile != null && seenMod) {
			Main.saveProps(statsFile, seenProps);
			seenMod = false;
		}
	}
	
	/** true if any not seen or seen more than 24h ago */
	private boolean checkNotSeen () {
		boolean notseen = false;
		long ct = System.currentTimeMillis();
		for (Stream s : liveOk) {
			Long lt = SEEN.get(s.name.toLowerCase());
			if (lt == null || lt.longValue() + (resendPeriod * 1000L) < ct) {
				notseen = true;
				break;
			}
		}
		return notseen;
	}
	
	/**
	 * set all liveOk streamers seen
	 */
	private void updateSeen () {
		Long t = Long.valueOf(System.currentTimeMillis());
		for (Stream s : liveOk) {
			SEEN.put(s.name.toLowerCase(), t);
		}
	}
	
	/** check async thread is running appropriately */
	public void updateAsync () {
		if (async && asyncPeriod >= 600) {
			long nextMs = System.currentTimeMillis() + asyncPeriod*1000L;
			//Main.println("update async next " + new Date(nextMs));
			if (thread == null) {
				Main.println("creating async thread");
				thread = new TwitchThread();
				thread.setDaemon(true);
				thread.nextMs = nextMs;
				thread.start();
			} else {
				thread.nextMs = nextMs;
			}
		} else if (thread != null) {
			Main.println("stopping async thread");
			thread.stop = true;
		}
	}
	
}
