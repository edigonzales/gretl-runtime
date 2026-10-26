package external.logging;

import ch.so.agi.gretl.runtime.api.*;
import java.io.IOException;
import java.util.*;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.LoggingEvent;
import java.time.Instant;
import java.util.concurrent.*;

/** Test-only, compiled and packaged through the regular external-provider path. */
public final class ScopeProvider implements OperationProvider {
    static final org.slf4j.Logger LOG = LoggerFactory.getLogger("fixture.scope");
    static LoggingEvent deferred;
    static Thread child;
    static ExecutorService pool;
    static Future<?> pooled;
    static final CountDownLatch ready = new CountDownLatch(2), release = new CountDownLatch(1);
    static void startForeignLogs() throws Exception {
        Runnable log = () -> {
            ready.countDown();
            try {
                if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("foreign release timeout");
                LOG.warn("provider-foreign-" + Thread.currentThread().getName());
                java.util.logging.Logger.getLogger("fixture.scope").warning("provider-jul-foreign-" + Thread.currentThread().getName());
            } catch (InterruptedException e) { throw new RuntimeException(e); }
        };
        child = Thread.ofPlatform().name("child").start(log);
        pool = Executors.newSingleThreadExecutor(); pooled = pool.submit(log);
        if (!ready.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("foreign ready timeout");
    }
    public record Request(String label) {}
    public static final class Builder implements ConfigBuilder<Request> {
        private String label = "default";
        public Builder() { LOG.warn("provider-builder"); }
        public void setLabel(String value) { label = value; }
        public Request build() { LOG.warn("provider-config-" + label); return new Request(label); }
    }
    public Collection<OperationType<?,?>> operations() {
        InputField<Request,String> label = Fields.input("label", "string", "Probe label", false,
                Optional.of("default"), List.of(), "'one'", Builder.class, Object::toString, Builder::setLabel);
        return List.of(new OperationType<Request,String>("fixture.scope", "ScopeProbe", "Library scope probe",
                Request.class, String.class, Builder::new,
                () -> {
                    LOG.warn("provider-factory");
                    return (request, context) -> {
                        LOG.warn("provider-handler-" + request.label());
                        java.util.logging.Logger.getLogger("fixture.scope").warning("provider-jul-" + request.label());
                        context.resources().manage(() -> {
                            LOG.warn("provider-close-" + request.label());
                            java.util.logging.Logger.getLogger("fixture.scope").warning("provider-jul-close-" + request.label());
                        });
                        if (request.label().equals("a")) {
                            MDC.put("foreign.provider", "foreign-provider-value");
                            deferred = new LoggingEvent(ScopeProvider.class.getName(), (ch.qos.logback.classic.Logger)LOG,
                                    Level.WARN, "provider-deferred", null, null);
                            deferred.setInstant(Instant.parse("2026-10-20T01:02:03.123Z"));
                            deferred.prepareForDeferredProcessing(); startForeignLogs();
                        }
                        if (request.label().equals("b")) {
                            if (!"foreign-provider-value".equals(MDC.get("foreign.provider")))
                                throw new IllegalStateException("foreign MDC changed");
                            ((ch.qos.logback.classic.Logger)LOG).callAppenders(deferred);
                            var invalid = new LoggingEvent(ScopeProvider.class.getName(), (ch.qos.logback.classic.Logger)LOG,
                                    Level.WARN, "provider-invalid", null, null);
                            invalid.setMDCPropertyMap(Map.of("gretl.run_id", UUID.randomUUID().toString(),
                                    "gretl.job_name", "foreign", "gretl.task_id", "invented"));
                            ((ch.qos.logback.classic.Logger)LOG).callAppenders(invalid);
                            release.countDown(); child.join(10000);
                            if (child.isAlive()) throw new IllegalStateException("foreign thread did not finish");
                            try { pooled.get(10, TimeUnit.SECONDS); } finally { pool.close(); }
                            String secret = context.secret("scopeDummy").reveal();
                            var error = new IOException("secret-" + secret, new IOException("cause-" + secret));
                            error.addSuppressed(new IOException("suppressed-" + secret));
                            for (Throwable part : List.of(error, error.getCause(), error.getSuppressed()[0]))
                                part.setStackTrace(new StackTraceElement[]{new StackTraceElement("Fixture", "log", "Fixture.java", 1)});
                            LOG.warn("provider-secret-" + secret, error);
                        }
                        if (request.label().equals("caught")) throw new IOException("provider-caught-failure");
                        return request.label();
                    };
                }, List.of(label), List.of(), "task('probe', ScopeProbe) { label = 'one' }"));
    }
}
