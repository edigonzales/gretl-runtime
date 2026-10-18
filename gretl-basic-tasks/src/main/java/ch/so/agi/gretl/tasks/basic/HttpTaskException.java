package ch.so.agi.gretl.tasks.basic;

import java.net.URI;

/** Safe, structured HTTP task failure. Query, userinfo and headers are never included. */
public final class HttpTaskException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final String method;
    private final String origin;
    private final int status;
    private final long bytes;
    private final String phase;

    public HttpTaskException(String code, String message, String method, URI uri,
                             int status, long bytes, String phase, Throwable cause) {
        super(code + ": " + message + " [method=" + method + ", origin="
                + (uri == null ? "?" : origin(uri)) + ", status=" + status
                + ", bytes=" + bytes + ", phase=" + phase + "]", cause);
        this.method = method;
        this.origin = uri == null ? "?" : origin(uri);
        this.status = status;
        this.bytes = bytes;
        this.phase = phase;
    }

    public String method() {
        return method;
    }

    public String origin() {
        return origin;
    }

    public int status() {
        return status;
    }

    public long bytes() {
        return bytes;
    }

    public String phase() {
        return phase;
    }

    static String origin(URI uri) {
        if (uri == null || uri.getScheme() == null || uri.getHost() == null) {
            return "?";
        }
        int port = uri.getPort();
        return uri.getScheme() + "://" + uri.getHost()
                + (port == -1 ? "" : ":" + port);
    }
}
