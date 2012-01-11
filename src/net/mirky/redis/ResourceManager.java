package net.mirky.redis;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

abstract class ResourceManager<T> {
    public final String type;
    protected final Map<String, T> cache;
    private final Map<String, String> aliases;

    ResourceManager(String type) {
        this.type = type;
        cache = new HashMap<String, T>();
        aliases = new HashMap<String, String>();
    }

    final T get(String rawName) throws ResourceManager.ResolutionError {
        String name = rawName.toLowerCase();
        if (!validName(name)) {
            throw new ResourceManager.ResolutionError(rawName, type, "invalid name");
        }
        if (aliases.containsKey(name)) {
            return get(aliases.get(name));
        } else {
            T resource = cache.get(name);
            if (resource == null) {
                resource = load(name);
                cache.put(name, resource);
                return resource;
            }
            return resource;
        }
    }

    private final T load(String name) throws ResourceManager.ResolutionError {
        try {
            BufferedReader reader;
            // If the given resource name contains a period ...
            if (name.indexOf('.') != -1) {
                // ... it will be treated as a filename.
                try {
                    reader = new BufferedReader(new InputStreamReader(new FileInputStream(name)));
                } catch (FileNotFoundException e) {
                    throw new ResourceManager.ResolutionError(name, type, "file not found", e);
                }
            } else {
                // Otherwise, it will be treated as a resource name without
                // explicit path and suffix.
                reader = TextResource.getBufferedReader("resources/" + name + "." + type);
            }
            try {
                return load(name, reader);
            } catch (IOException e) {
                throw new RuntimeException(name + " (" + type + "): I/O error reading resource stream", e);
            } finally {
                try {
                    reader.close();
                } catch (IOException e) {
                    throw new RuntimeException(name + " (" + type + "): I/O error closing resource stream", e);
                }
            }
        } catch (TextResource.Missing e) {
            throw new ResourceManager.ResolutionError(name, type, "resource not found", e);
        }
    }

    /**
     * Load a resource from an opened text stream.
     * 
     * @param name
     *            resource's short name
     * @param reader
     *            text stream as a {@link BufferedReader} instance. Caller will
     *            take care of closing it.
     * @return the parsed resource
     * @throws IOException
     *             when an I/O problem hampers reading the stream
     */
    protected abstract T load(String name, BufferedReader reader) throws IOException;

    public final void registerSpecial(String name, T value) {
        assert !cache.containsKey(name);
        cache.put(name, value);
    }

    public final void registerAlias(String alias, String cname) {
        aliases.put(alias, cname);
    }

    public static final boolean validName(String candidate) {
        if (candidate.length() == 0) {
            return false;
        }
        for (int i = 0; i < candidate.length(); i++) {
            char c = Character.toLowerCase(candidate.charAt(i));
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'z') || c == '-' || c == '.')) {
                return false;
            }
        }
        if (candidate.charAt(0) == '-' || candidate.charAt(candidate.length() - 1) == '-') {
            return false;
        }
        return true;
    }

    public static final class ResolutionError extends Exception {
        public ResolutionError(String name, String type, String comment) {
            super(name + " (" + type + "): " + comment);
        }

        public ResolutionError(String name, String type, String comment, Exception cause) {
            super(name + " (" + type + "): " + comment, cause);
        }
    }
}