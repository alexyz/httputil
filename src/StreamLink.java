
import java.io.*;
import java.net.InetAddress;
import java.text.*;
import java.util.*;

import org.apache.http.client.utils.URIBuilder;

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
		
		print("exe = " + exe + " exists = " + exe.exists());
		print(STREAMER + " = " + streamer);
		print(QUALITY + " = " + qual);
		print(SLEEPTIME + " = " + stime);
		print(DIR + " = " + dir  + " exists = " + dir.exists());
		print(HOST + " = " + host);
		print(REBOOT + " = " + reboot);
		
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
		File out = new File(dir, streamer + "-" + timestamp() + ".ts");
		
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
					print(l);
				}
			}
		}
		
		print("exit " + p.exitValue());
		
		if (out.exists()) {
			stime = 60;
		} else {
			stime = Math.min(stime + 60, maxstime);
		}
	}

	private static void sleep(int n) throws Exception {
		print("sleep " + n);
		Thread.sleep(n*1000);
	}

	private static void print(String l) {
		System.out.println(timestamp() + ": " + l);
	}

	private static String timestamp() {
		return format.format(new Date());
	}

	private static void checkreboot () throws Exception {
		if (reboot) {
			try {
				InetAddress.getByName(host);
				return;
			} catch (Exception e) {
				print("could not get host: " + e.toString());
			}
			
			long t = System.nanoTime();
			long hourns = 60 * 60 * Main.NS_IN_S;
			if (t > starttime + hourns) {
				if (lastreboot == 0 || t > lastreboot + hourns) {
					VmReboot.main(new String[0]);
					lastreboot = t;
				} else {
					print("avoid reboot due to last reboot less than 1 hour ago");
				}
			} else {
				print("avoid reboot due to started less than 1 hour ago");
			}
		}
	}

}
