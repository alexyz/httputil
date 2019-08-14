
import java.io.*;
import java.net.InetAddress;
import java.text.*;
import java.util.*;

import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

/**
 * query for and record twitch streamer (wrapper for streamlink)
 */
public class StreamLink {

	private static final String HOST = "host", SLEEPTIME = "sleeptime", QUALITY = "quality", DIR = "dir", STREAMER = "streamer", REBOOT = "reboot";
	
	private static File exe, dir;
	private static String streamer, host, qual;
	private static DateFormat format;
	private static int stime, maxstime;
	private static boolean reboot;
	private static long starttime, lastreboot;

	public static void main(String[] args) throws Exception {
		if (args.length > 1) {
			throw new Exception("usage: java -jar httputil.jar StreamLink streamlink.properties");
		}
		
		starttime = System.nanoTime();
		
		Properties p = Main.loadProps(new File(args.length > 0 ? args[0] : "streamlink.properties"));
		exe = new File(System.getProperty("user.home") + "/AppData/Local/Programs/Python/Python36/Scripts/streamlink.exe");
		format = new SimpleDateFormat("yyyyMMdd-HHmmss");
		format.setTimeZone(TimeZone.getTimeZone("UTC"));
		streamer = p.getProperty(STREAMER);
		dir = new File(p.getProperty(DIR, System.getProperty("user.dir")));
		qual = p.getProperty(QUALITY, "best");
		maxstime = Integer.parseInt(p.getProperty(SLEEPTIME, "600"));
		stime = maxstime;
		host = p.getProperty(HOST, "www.twitch.tv");
		reboot = Boolean.parseBoolean(p.getProperty(REBOOT));
		
		Main.println("exe = " + exe + " exists = " + exe.exists());
		Main.println(STREAMER + " = " + streamer);
		Main.println(QUALITY + " = " + qual);
		Main.println(SLEEPTIME + " = " + stime);
		Main.println(DIR + " = " + dir  + " exists = " + dir.exists());
		Main.println(HOST + " = " + host);
		Main.println(REBOOT + " = " + reboot);
		
		if (!dir.exists()) {
			throw new Exception("dir does not exist: " + dir.getAbsolutePath());
		}
		
		while (true) {
			checkreboot();
			run();
			sleep(stime);
		}
	}

	private static void run() throws Exception {
		
		boolean maybeLive = true;
		if (host.equals("www.twitch.tv")) {
			try (CloseableHttpClient client = HttpClients.createDefault()) {
				TwitchQuery q = TwitchQuery.create();
				Stream s = q.queryStream(client, streamer);
				//Main.println("twitch stream: " + s);
				maybeLive = s != null && s.live();
			} catch (Exception e) {
				Main.println("could not query twitch: " + e);
			}
		}
		
		File out = new File(dir, streamer + "-" + format.format(new Date()) + ".ts");
		if (maybeLive) {
			execute(out);
		}
		
		if (out.exists()) {
			stime = 60;
		} else {
			stime = Math.min(stime + 60, maxstime);
		}
	}

	private static void execute (File out) throws Exception {
		URIBuilder ub = new URIBuilder();
		ub.setScheme("https");
		ub.setHost(host);
		ub.setPath(streamer);
		
		List<String> args = new ArrayList<>();
		args.add(exe.getAbsolutePath());
		args.add("--twitch-disable-hosting");
		args.add(ub.build().toString());
		args.add(qual);
		args.add("-o");
		args.add(out.getAbsolutePath());
		
		ProcessBuilder pb = new ProcessBuilder(args);
		pb.redirectErrorStream(true);
		
		Process p = pb.start();
		try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
			String l;
			while ((l = br.readLine()) != null) {
				if ((l = l.trim()).length() > 0 && !l.startsWith("[download]")) {
					if (l.length() > 80) {
						l = l.substring(0, 80) + "...";
					}
					Main.println(l);
				}
			}
		}
		
		Main.println("exit " + p.exitValue());
	}

	private static void sleep(int n) throws Exception {
		//Main.println("sleep " + n);
		Thread.sleep(n*1000);
	}
	
	private static void checkreboot () throws Exception {
		if (reboot) {
			try {
				InetAddress.getByName(host);
				return;
			} catch (Exception e) {
				Main.println("could not get host: " + e.toString());
			}
			
			long t = System.nanoTime();
			long hourns = 60 * 60 * Main.NS_IN_S;
			if (t > starttime + hourns) {
				if (lastreboot == 0 || t > lastreboot + hourns) {
					VmReboot.main(new String[0]);
					lastreboot = t;
				} else {
					Main.println("avoid reboot due to last reboot less than 1 hour ago");
				}
			} else {
				Main.println("avoid reboot due to started less than 1 hour ago");
			}
		}
	}

}
