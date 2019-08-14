import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

public class TwitchThread extends Thread {
	
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
			Main.println("async thread exit");
			TwitchQuery.thread = null;
		}
	}
	
	private static void runAsync () {
		try (CloseableHttpClient client = HttpClients.createDefault()) {
			TwitchQuery q = TwitchQuery.create();
			q.loadStats();
			q.run(client, TwitchQuery.MAILSTREAMS_CMD);
			q.updateAsync();
			q.saveStats();
		} catch (Exception e) {
			Main.println("runAsync: " + e);
		}
	}
}