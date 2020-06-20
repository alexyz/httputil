import java.time.Instant;

public class Stream {
	/** streamer name */
	public String name;
	public String gameId;
	/** stream title */
	public String title;
	public Instant created;
	public int viewers;
	@Override
	public String toString() {
		return String.format("Stream[%s]", name);
	}
}