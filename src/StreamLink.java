
import java.io.*;
import java.text.*;
import java.util.*;

/**
 * query for and record twitch streamer (wrapper for streamlink)
 */
public class StreamLink {

	private static File exe;
	private static String streamer;
	private static DateFormat format;
	private static String qual;

	public static void main(String[] args) throws Exception {
		exe = new File(System.getProperty("user.home") + "/AppData/Local/Programs/Python/Python36/Scripts/streamlink.exe");
		format = new SimpleDateFormat("yyyyMMdd-HHmmss");
		format.setTimeZone(TimeZone.getTimeZone("UTC"));
		streamer = args[0];
		qual = args.length >= 1 ? args[1] : "best";
		
		print("exe = " + exe + " exists = " + exe.exists());
		print("streamer = " + streamer);
		print("quality = " + qual);
		
		while (true) {
			run();
			sleep(600);
		}
	}

	private static void run() throws Exception {
		List<String> args = new ArrayList<>();
		args.add(exe.getAbsolutePath());
		args.add("--twitch-disable-hosting");
		args.add("https://www.twitch.tv/" + streamer);
		args.add(qual);
		args.add("-o");
		args.add(streamer + "-" + timestamp() + ".ts");
		ProcessBuilder pb = new ProcessBuilder(args);
		pb.redirectErrorStream(true);
		Process p = pb.start();
		try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
			String l;
			while ((l = br.readLine()) != null) {
				if ((l = l.trim()).length() > 0 && !l.startsWith("[download]")) {
					print(l);
				}
			}
		}
		print("exit " + p.exitValue());
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

}
