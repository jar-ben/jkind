package jkind.api.extractor;

public class Span {
	public final int start;
	public final int stop;

	public Span(int start, int stop) {
		this.start = start;
		this.stop = stop;
	}
	
	public boolean contains(int p) {
		return start <= p && p <= stop;
	}
}
