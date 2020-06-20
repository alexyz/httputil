
import java.io.*;
import java.time.Instant;
import java.util.*;

import com.fasterxml.jackson.databind.node.ArrayNode;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.*;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.*;
import org.apache.http.message.BasicHeader;
import org.apache.http.util.EntityUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * query twitch to see who is live
 */
public class TwitchQuery {
	
	public static final String CLIENTID = "clientid", DELAY = "delay", COLS = "cols", STREAMER = "s.", GAMEIGNORE = "gi.", OAUTH = "oauth";
	public static final String ASYNC_STREAMER = "as.", ASYNC = "async", ASYNCPERIOD = "asyncperiod", RESENDPERIOD = "resendperiod", MAILTO = "mailto";
	public static final String MAILSTREAMS_CMD = "m", STREAMS_CMD = "s", AUTH_CMD = "a";
	private static final Map<String, Long> SEEN = new TreeMap<>();
	
	public static TwitchThread thread;
	
	public static void main(String[] args) throws Exception {
		try (CloseableHttpClient client = HttpClients.createDefault()) {
			String cmd = null;
			while (true) {
				try {
					TwitchQuery q = create();
					q.updateAsync();
					q.run(client, StringUtils.defaultString(cmd, STREAMS_CMD));
				} catch (RuntimeException e) {
					throw e;
				} catch (Exception e) {
					println("main: " + e);
				}
				cmd = StringUtils.trimToNull(Main.readLine());
			}
		}
	}
	
	public static TwitchQuery create() throws IOException {
		return new TwitchQuery(Main.loadProps(new File("twitch.properties")));
	}
	
	private static void println(String l) {
		Main.println("TQ", l);
	}
	
	private final Set<String> streamers = new TreeSet<>();
	private final Set<String> asyncStreamers = new TreeSet<>();
	private final Set<String> gameIgnore = new TreeSet<>();
	private String clientId, mailto, oauth;
	private int asyncPeriod, resendPeriod, cols;
	private boolean async;
	
	private TwitchQuery(Properties props) {
		clientId = props.getProperty(CLIENTID, "");
		oauth = props.getProperty(OAUTH, "");
		streamers.addAll(Main.getPropValues(props, STREAMER));
		asyncStreamers.addAll(Main.getPropValues(props, ASYNC_STREAMER));
		gameIgnore.addAll(Main.getPropValues(props, GAMEIGNORE));
		cols = Integer.parseInt(props.getProperty(COLS, "80"));
		async = Boolean.parseBoolean(props.getProperty(ASYNC));
		asyncPeriod = Integer.parseInt(props.getProperty(ASYNCPERIOD, String.valueOf(15 * 60)));
		resendPeriod = Integer.parseInt(props.getProperty(RESENDPERIOD, String.valueOf(12 * 60 * 60)));
		mailto = props.getProperty(MAILTO);
	}
	
	public void run(CloseableHttpClient client, String cmd) throws Exception {
		switch (cmd) {
			case STREAMS_CMD:
				printStreams(client);
				break;
			case MAILSTREAMS_CMD:
				mailStreams(client);
				break;
			case AUTH_CMD:
				validateOath(client);
				break;
			default:
				println("unknown cmd: " + cmd);
				break;
		}
	}
	
	private void validateOath(CloseableHttpClient client) throws IOException {
		println("validate oauth");
		URIBuilder b = new URIBuilder().setScheme("https").setHost("id.twitch.tv").setPath("/oauth2/validate");
		HttpGet get = new HttpGet(b.toString());
		get.addHeader(createOAuth());
		try (CloseableHttpResponse resp = client.execute(get)) {
			EntityUtils.consume(resp.getEntity());
			System.out.println(resp.getStatusLine());
		}
	}
	
	private BasicHeader createOAuth() {
		return new BasicHeader("Authorization", "OAuth " + oauth);
	}
	
	private BasicHeader createBearer() {
		return new BasicHeader("Authorization", "Bearer " + oauth);
	}
	
	private void printStreams(CloseableHttpClient client) throws IOException {
		println("streams");
		for (Stream s : queryStreams(client, streamers)) {
			if (isOk(s)) {
				System.out.println(StringUtils.left(s.toString(), cols - 1));
			} else {
				System.out.println("ignore " + s);
			}
		}
	}
	
	public List<Stream> queryStreams(CloseableHttpClient client, Set<String> users) throws IOException {
		URIBuilder b = new URIBuilder().setScheme("https").setHost("api.twitch.tv").setPath("/helix/streams");
		users.stream().forEach(s -> b.addParameter("user_login", s));
		HttpGet get = new HttpGet(b.toString());
		get.addHeader(createClientId());
		get.addHeader(createBearer());
		try (CloseableHttpResponse resp = client.execute(get)) {
			String content = EntityUtils.toString(resp.getEntity());
			System.out.println("resp=" + resp.getStatusLine() + " content=" + content);
			if (resp.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
				ObjectMapper mapper = new ObjectMapper();
				JsonNode node = mapper.readTree(content);
				ArrayNode data = (ArrayNode) node.get("data");
				List<Stream> list = new ArrayList<>();
				for (int n = 0; n < data.size(); n++) {
					JsonNode snode = data.get(n);
					Stream s = new Stream();
					s.created = Instant.parse(snode.get("started_at").asText());
					s.game = snode.get("game_id").asText();
					s.status = snode.get("title").asText();
					s.type = snode.get("type").asText();
					s.view = snode.get("viewer_count").asInt();
					s.name = snode.get("user_name").asText();
					list.add(s);
				}
				return list;
			} else {
				println("could not query streams: " + resp.getStatusLine());
				return Collections.emptyList();
			}
		}
	}
	
	private BasicHeader createClientId() {
		return new BasicHeader("Client-ID", clientId);
	}
	
	private void mailStreams(CloseableHttpClient client) throws IOException {
		if (!StringUtils.contains(mailto, "@") || asyncStreamers.size() == 0) {
			println("mail streams invalid config: t=" + mailto + ", as=" + asyncStreamers.size());
			return;
		}
		
		List<Stream> list = queryStreams(client, asyncStreamers);
		
		if (checkNotSeen(list)) {
			//Main.println("mail streams send");
			StringBuilder subSb = new StringBuilder();
			StringBuilder textSb = new StringBuilder();
			for (Stream s : list) {
				subSb.append(subSb.length() > 0 ? ", " : "").append(s.name);
				textSb.append(s.toString()).append("\n");
			}
			SendMail sm = SendMail.create();
			sm.send(mailto, "Streamers live: " + subSb.toString(), textSb.toString());
			updateSeen(list);
		}
	}
	
	private boolean isOk(Stream s) {
		return s != null && s.live() && !gameIgnore.contains(s.game.toLowerCase());
	}
	
	/**
	 * true if any not seen or seen more than 24h ago
	 */
	private boolean checkNotSeen(List<Stream> list) {
		boolean notseen = false;
		long ct = System.currentTimeMillis();
		for (Stream s : list) {
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
	private void updateSeen(List<Stream> list) {
		Long t = Long.valueOf(System.currentTimeMillis());
		list.stream().filter(s -> isOk(s)).forEach(s -> SEEN.put(s.name.toLowerCase(), t));
	}
	
	/**
	 * check async thread is running appropriately
	 */
	public void updateAsync() {
		if (async && asyncPeriod >= 600) {
			long nextMs = System.currentTimeMillis() + asyncPeriod * 1000L;
			//Main.println("update async next " + new Date(nextMs));
			if (thread == null) {
				println("creating async thread");
				thread = new TwitchThread();
				thread.setDaemon(true);
				thread.nextMs = nextMs;
				thread.start();
			} else {
				thread.nextMs = nextMs;
			}
		} else if (thread != null) {
			println("stopping async thread");
			thread.stop = true;
		}
	}
	
}
