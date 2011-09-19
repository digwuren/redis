package net.mirky.redis;

import java.util.HashMap;
import java.util.Map;

abstract class ResourceManager<T> {
    private final String type;
    private final Map<String, T> cache;
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
    
    protected abstract T load(String name) throws ResourceManager.ResolutionError;

    public final void registerAlias(String alias, String cname) {
        aliases.put(alias, cname);
    }

    public static final boolean validName(String candidate) {
        if (candidate.length() == 0) {
            return false;
        }
        for (int i = 0; i < candidate.length(); i++) {
            char c = Character.toLowerCase(candidate.charAt(i));
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'z') || c == '-')) {
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