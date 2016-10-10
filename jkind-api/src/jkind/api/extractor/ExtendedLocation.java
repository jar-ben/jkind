package jkind.api.extractor;

import jkind.lustre.Location;

public class ExtendedLocation extends Location {
	public final int start;
	public final int stop;

	public ExtendedLocation(int line, int charPositionInLine, int start, int stop) {
		super(line, charPositionInLine);
		this.start = start;
		this.stop = stop;
	}
}
