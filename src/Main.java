
import java.io.*;
import java.lang.reflect.Method;
import java.text.DateFormat;
import java.util.*;

import org.apache.commons.lang3.ArrayUtils;

public class Main {
	
	public static final long NS_IN_S = 1000000000;
	
	private static BufferedReader BR;
	
	public static void main (String[] args) throws Exception {
		Map<String,Class<?>> map = new TreeMap<>();
		map.put("twitch", TwitchQuery.class);
		map.put("vmreboot", VmReboot.class);
		map.put("streamlink", StreamLink.class);
		
		if (args.length > 0) {
			Class<?> mainclass = map.get(args[0].toLowerCase());
			if (mainclass != null) {
				run(mainclass, args);
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
			println("run " + mainclass.getSimpleName() + " " + Arrays.toString(args));
			Method main = mainclass.getMethod("main", String[].class);
			main.invoke(null, (Object) ArrayUtils.remove(args, 0));
			System.exit(0);
		} catch (Exception e) {
			e.printStackTrace(System.out);
			println("press return to exit");
			readLine();
		}
	}
	
	public static Properties loadProps (File f) throws IOException {
		Properties p = new Properties();
		if (f.exists()) {
			try (FileInputStream is = new FileInputStream(f)) {
				p.load(is);
			}
		}
		if (p.size() == 0) {
			throw new IOException("could not load properties from " + f.getAbsolutePath());
		}
		return p;
	}
	
	public static String readLine() throws IOException {
		if (BR == null) {
			BR = new BufferedReader(new InputStreamReader(System.in));
		}
		return BR.readLine();
	}
	
	public static void println(String l) {
		DateFormat f = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM);
		System.out.println(f.format(new Date()) + ": " + l);
	}

}
