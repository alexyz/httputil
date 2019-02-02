
import java.io.*;
import java.lang.reflect.Method;
import java.util.*;

import org.apache.commons.lang3.ArrayUtils;

public class Main {
	
	public static final long NS_IN_S = 1000000000;
	
	public static BufferedReader reader;
	
	public static void main (String[] args) throws Exception {
		reader = new BufferedReader(new InputStreamReader(System.in));
		
		Map<String,Class<?>> map = new TreeMap<>();
		map.put("twitch", TwitchQuery.class);
		map.put("vmreboot", VmReboot.class);
		map.put("streamlink", StreamLink.class);
		
		if (args.length > 0) {
			Class<?> mainclass = map.get(args[0].toLowerCase());
			if (mainclass != null) {
				run(mainclass, args);
			} else {
				System.out.println("could not find " + args[0] + " in " + map.keySet());
			}
		} else {
			System.out.println("specify one of " + map.keySet());
		}
		
		System.exit(1);
	}

	private static void run (Class<?> mainclass, String[] args) throws IOException {
		try {
			Method main = mainclass.getMethod("main", String[].class);
			main.invoke(null, (Object) ArrayUtils.remove(args, 0));
			System.exit(0);
		} catch (Exception e) {
			e.printStackTrace(System.out);
			System.out.println("press return to exit");
			reader.readLine();
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
	
}
