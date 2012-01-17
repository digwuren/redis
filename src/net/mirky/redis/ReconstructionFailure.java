package net.mirky.redis;

public class ReconstructionFailure extends Exception {
    ReconstructionFailure(String msg) {
        super(msg);
    }

    ReconstructionFailure(String msg, Exception cause) {
        super(msg, cause);
    }

    public static final class DataInconsistent extends ReconstructionFailure {
        DataInconsistent(String msg) {
            super(msg);
        }
    }
    
    static final class RcnParseError extends ReconstructionFailure {
        RcnParseError(String filename, int lineno) {
            super(filename + ":" + lineno + ": rcn parse error");
        }

        RcnParseError(String filename, int lineno,
                Exception cause) {
            super(filename + ":" + lineno + ": rcn parse error", cause);
        }
    }
}
