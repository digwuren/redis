package net.mirky.redis;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Iterator;

public final class TextResource implements Iterable<String> {
	private final String name;

	public TextResource(String name) {
		this.name = name;
	}

	public final LineIterator iterator() {
		return new TextResource.LineIterator(name);
	}
	
	static final class LineIterator implements Iterator<String> {
		private final String name;
		private final BufferedReader reader;
		private String nextLine;

		LineIterator(String name) {
			this.name = name;
			InputStream stream = Main.class.getResourceAsStream(name);
			if (stream == null) {
				throw new RuntimeException("resource " + name + " missing");
			}
			reader = new BufferedReader(new InputStreamReader(stream));
			try {
				nextLine = reader.readLine();
			} catch (IOException e) {
				try {
					reader.close();
				} catch (IOException e1) {
					// double exception; ignore the second one
				}
				throw new RuntimeException("I/O error reading resource " + name, e);
			}
		}

		public final boolean hasNext() {
			return nextLine != null;
		}

		public final String next() {
			String line = nextLine;
			try {
				nextLine = reader.readLine();
				if (nextLine == null) {
					reader.close();
				}
			} catch (IOException e) {
				throw new RuntimeException("I/O error reading resource " + name, e);
			}
			return line;
		}

		public final void remove() {
			throw new RuntimeException("resource files are not supposed to be modified");
		}
	}
}
