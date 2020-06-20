import java.time.Duration;
import java.time.Instant;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.databind.JsonNode;

public class Stream {
	/** streamer name */
	public String name;
	public String game;
	/** stream title */
	public String status;
	public String type;
	public Instant created;
	public int view;
	public boolean live() {
		return type.equals("live");
	}
	public String durStr() {
		return Main.formatDuration(Duration.between(created, Instant.now()));
	}
	@Override
	public String toString() {
		return String.format("%s - %s - %dv - %s - %s", 
				name, durStr(), view, game + (live() ? "" : " [" + type + "]"), status);
	}
}