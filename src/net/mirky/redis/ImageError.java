package net.mirky.redis;

public class ImageError extends Exception {
    public ImageError(String msg) {
        super(msg);
    }
    
    public static final class DuplicateChildFilenameError extends ImageError {
        DuplicateChildFilenameError(String msg) {
            super(msg);
        }
    }
}
