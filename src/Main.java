
import java.io.*;
import java.lang.reflect.Method;
import java.text.DateFormat;
import java.time.Duration;
import java.util.*;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

public class Main {
	
	public static final long NS_IN_S = 1_000_000_000L;
	
	private static BufferedReader BR;
	
	public static void main (String[] args) throws Exception {
		Map<String,Class<?>> map = new TreeMap<>();
		map.put("twitch", TwitchQuery.class);
		map.put("vmreboot", VmReboot.class);
		map.put("streamlink", StreamLink.class);
		
		if (args.length > 0) {
			Class<?> mainclass = map.get(args[0].toLowerCase());
			if (mainclass != null) {
				run(mainclass, ArrayUtils.remove(args, 0));
			} else {
				println("could not find " + args[0] + " in " + map.keySet());
			}
		} else {
			println("specify one of " + map.keySet());
		}
		
		System.exit(1);
	}

	private static void run (Class<?> mainclass, String[] args) throws IOException {
		try {
			println(mainclass.getSimpleName() + " " + Arrays.toString(args));
			Method main = mainclass.getMethod("main", String[].class);
			main.invoke(null, (Object) args);
			System.exit(0);
		} catch (Exception e) {
			e.printStackTrace(System.out);
			println("press return to exit");
			readLine();
		}
	}
	
	public static Properties loadProps (File f) throws IOException {
		return loadProps(f, true);
	}
	
	public static Properties loadProps (File f, boolean ex) throws IOException {
		Properties p = new Properties();
		if (f.exists()) {
			try (FileInputStream is = new FileInputStream(f)) {
				p.load(is);
			}
		}
		if (ex && p.size() == 0) {
			throw new IOException("could not load properties from " + f.getAbsolutePath());
		}
		return p;
	}
	
	public static void saveProps (File f, Properties p) throws IOException {
		try (FileOutputStream os = new FileOutputStream(f)) {
			p.store(os, null);
		}
	}
	
	public static String readLine() throws IOException {
		if (BR == null) {
			BR = new BufferedReader(new InputStreamReader(System.in));
		}
		return BR.readLine();
	}
	
	public static DateFormat dateFormat () {
		return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM);
	}
	
	public static void println(String cat, String l) {
		String t = dateFormat().format(new Date());
		String n = Thread.currentThread().getName();
		System.out.println(cat + ": " + t + ": " + n + ": " + l);
	}

	private static void println(String l) {
		println("M", l);
	}
	
	public static void sleep (int ms) {
		try {
			Thread.sleep(ms);
		} catch (InterruptedException e) {
			println("could not sleep " + ms + ": " + e);
		}
	}
	
	public static String formatDuration (Duration dur) {
		long d = dur.toDays(), h = dur.toHours() % 24, m = dur.toMinutes() % 60, s = dur.getSeconds() % 60;
		if (d > 0) {
			return String.format("%dd %dh %dm", d, h, m);
		} else if (h > 0) {
			return String.format("%dh %dm", h, m);
		} else {
			return String.format("%dm", m);
		}
	}
	
	public static Set<String> getPropValues (Properties p, String pre) {
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
}
