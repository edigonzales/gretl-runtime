package ch.so.agi.gretl.tasks.io.http;

import ch.so.agi.gretl.runtime.api.DiagnosticCarrier;
import ch.so.agi.gretl.runtime.api.DiagnosticInfo;
import java.net.URI;
import java.util.List;
import java.util.Optional;

/** Safe, structured HTTP task failure. Query, userinfo and headers are never included. */
public final class HttpTaskException extends RuntimeException
        implements DiagnosticCarrier {
    private static final long serialVersionUID = 1L;

    private final String code;
    private final String method;
    private final String origin;
    private final int status;
    private final long bytes;
    private final String phase;

    public HttpTaskException(String code, String message, String method, URI uri,
                             int status, long bytes, String phase, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.method = method;
        this.origin = uri == null ? "?" : origin(uri);
        this.status = status;
        this.bytes = bytes;
        this.phase = phase;
    }

    public String code() {
        return code;
    }

    @Override
    public DiagnosticInfo diagnostic() {
        return new DiagnosticInfo(code, Optional.empty(), Optional.empty(),
                List.of(), Optional.empty());
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
