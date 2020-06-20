import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

public class TwitchThread extends Thread {

	private static void println(String l) {
		Main.println("TT", l);
	}

	public volatile boolean stop;
	public volatile long nextMs;
	
	@Override
	public void run () {
		try {
			//Main.println(getClass().getName() + " starting");
			while (!stop) {
				if (System.currentTimeMillis() > nextMs) {
					runAsync();
				}
				Main.sleep(1000);
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			println("async thread exit");
			TwitchQuery.thread = null;
		}
	}
	
	private static void runAsync () {
		try (CloseableHttpClient client = HttpClients.createDefault()) {
			TwitchQuery q = TwitchQuery.create();
			q.run(client, TwitchQuery.MAILSTREAMS_CMD);
			q.updateAsync();
		} catch (Exception e) {
			println("runAsync: " + e);
		}
	}
}