import java.time.Instant;

public class Stream implements Comparable<Stream> {
	/** streamer name */
	public String userName;
	public String gameId;
	/** stream title */
	public String title;
	public Instant startedAt;
	public int viewerCount;
	@Override
	public int compareTo(Stream o) {
		return userName.toLowerCase().compareTo(o.userName.toLowerCase());
	}
	@Override
	public String toString() {
		return String.format("Stream[%s]", userName);
	}
}