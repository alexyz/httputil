
import java.lang.reflect.Method;
import java.util.*;

import org.apache.commons.lang3.ArrayUtils;

public class Main {
	
	public static void main (String[] args) throws Exception {
		Map<String,Class<?>> map = new TreeMap<>();
		map.put("twitch", TwitchQuery.class);
		map.put("vmreboot", VmReboot.class);
		map.put("streamlink", StreamLink.class);
		
		if (args.length > 0) {
			Class<?> mainclass = map.get(args[0].toLowerCase());
			if (mainclass != null) {
				Method main = mainclass.getMethod("main", String[].class);
				main.invoke(null, (Object) ArrayUtils.remove(args, 0));
			} else {
				System.out.println("could not find " + args[0] + " in " + map.keySet()); 
			}
		} else {
			System.out.println("specify one of " + map.keySet());
		}
	}
	
}
