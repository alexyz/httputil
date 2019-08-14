import java.time.Duration;
import java.time.Instant;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.databind.JsonNode;

public class Stream {
	public final String name, game, status, type;
	public final Instant created;
	public final int view;
	public Stream(JsonNode stream) {
		JsonNode channel = stream.get("channel");
		this.name = channel.get("display_name").asText();
		this.status = StringUtils.normalizeSpace(channel.get("status").asText());
		this.game = StringUtils.normalizeSpace(stream.get("game").asText());
		this.view = stream.get("viewers").asInt();
		this.created = Instant.parse(stream.get("created_at").asText());
		this.type = stream.get("stream_type").asText();
	}
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