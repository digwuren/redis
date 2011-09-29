package net.mirky.redis;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

public final class TextResource{
	private TextResource() {
	    // not a real constructor
	}

	public static final InputStream getStream(String name) throws TextResource.Missing {
        InputStream stream = Main.class.getResourceAsStream(name);
        if (stream == null) {
            throw new TextResource.Missing(name + ": resource missing");
        }
        return stream;
    }

    public static final BufferedReader getBufferedReader(String name) throws TextResource.Missing {
        return new BufferedReader(new InputStreamReader(getStream(name)));
    }

	public static final class Missing extends RuntimeException {
	    public Missing(String message) {
	        super(message);
	    }
	}
}