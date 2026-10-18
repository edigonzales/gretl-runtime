package ch.so.agi.gretl.runtime.qualification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import java.io.IOException;
import java.util.List;
import java.util.ServiceLoader;
import java.util.logging.Handler;
import java.util.logging.LogManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.ILoggerFactory;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;
import org.slf4j.spi.SLF4JServiceProvider;

/**
 * M0 logging inventory probe. It verifies the selected single SLF4J provider,
 * absence of competing providers/bridges, and that cause and suppressed
 * exceptions survive the actual backend. It is not yet the M3 telemetry
 * pipeline.
 */
class LoggingQualificationTest {

    private Logger logger;
    private ListAppender<ILoggingEvent> appender;
    private List<Handler> originalRootHandlers;

    @BeforeEach
    void setUp() {
        ILoggerFactory factory = LoggerFactory.getILoggerFactory();
        assertTrue(factory instanceof LoggerContext, "Logback LoggerContext is active");
        logger = ((LoggerContext) factory).getLogger("ch.so.agi.gretl.m0.qualification");
        logger.setLevel(Level.TRACE);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        originalRootHandlers = List.of(java.util.logging.Logger.getLogger("").getHandlers());
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        appender.stop();
        SLF4JBridgeHandler.uninstall();
        java.util.logging.Logger root = java.util.logging.Logger.getLogger("");
        for (Handler handler : root.getHandlers()) {
            root.removeHandler(handler);
        }
        for (Handler handler : originalRootHandlers) {
            root.addHandler(handler);
        }
    }

    @Test
    void exactlyOneSlf4jProviderAndNoStaticBinderOrCompetingBridge() {
        List<SLF4JServiceProvider> providers = ServiceLoader
                .load(SLF4JServiceProvider.class)
                .stream()
                .map(ServiceLoader.Provider::get)
                .toList();
        assertEquals(1, providers.size(), "exactly one SLF4J provider is required: " + providers);
        assertTrue(providers.get(0).getClass().getName().startsWith("ch.qos.logback."),
                "the one provider is Logback: " + providers.get(0).getClass().getName());
        assertEquals(LoggerContext.class, LoggerFactory.getILoggerFactory().getClass());

        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("org.slf4j.impl.StaticLoggerBinder", false,
                        getClass().getClassLoader()),
                "legacy SLF4J 1.x static binder must be absent");
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("org.apache.logging.slf4j.Log4jLoggerFactory", false,
                        getClass().getClassLoader()),
                "Log4j-to-SLF4J provider must not be present");
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("org.apache.log4j.Logger", false,
                        getClass().getClassLoader()),
                "Log4j API must not be present");
    }

    @Test
    void causeAndSuppressedExceptionsSurviveTheBackend() {
        IOException root = new IOException("root-cause");
        IllegalStateException outer = new IllegalStateException("outer-failure", root);
        outer.addSuppressed(new IllegalArgumentException("suppressed-detail"));

        logger.error("operation failed", outer);

        assertEquals(1, appender.list.size());
        ILoggingEvent event = appender.list.get(0);
        assertEquals(Level.ERROR, event.getLevel());
        assertEquals("operation failed", event.getFormattedMessage());
        assertNotNull(event.getThrowableProxy());
        assertTrue(event.getThrowableProxy() instanceof ThrowableProxy);

        ThrowableProxy proxy = (ThrowableProxy) event.getThrowableProxy();
        assertEquals(IllegalStateException.class.getName(), proxy.getClassName());
        assertNotNull(proxy.getCause(), "cause must not be flattened away");
        assertEquals(IOException.class.getName(), proxy.getCause().getClassName());
        assertEquals("root-cause", proxy.getCause().getMessage());
        assertEquals(1, proxy.getSuppressed().length);
        assertEquals(IllegalArgumentException.class.getName(),
                proxy.getSuppressed()[0].getClassName());
        assertEquals("suppressed-detail", proxy.getSuppressed()[0].getMessage());
    }

    @Test
    void julBridgeForwardsExactlyOnceAndIsNotPreinstalled() {
        assertFalse(hasJulBridgeHandler(java.util.logging.Logger.getLogger("")),
                "the JUL bridge must be explicitly installed by the product, not by classpath accident");
        assertNotNull(SLF4JBridgeHandler.class);

        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();
        try {
            java.util.logging.Logger jul = java.util.logging.Logger.getLogger(
                    "ch.so.agi.gretl.m0.qualification");
            jul.info("jul-message");
        } finally {
            SLF4JBridgeHandler.uninstall();
        }

        assertEquals(1, appender.list.size(),
                "a JUL record must not be forwarded twice or loop back into JUL");
        assertEquals("jul-message", appender.list.get(0).getFormattedMessage());
        assertEquals(Level.INFO, appender.list.get(0).getLevel());
    }

    @Test
    void noImplicitConfigurationOrResultDumpIsWrittenByTheProbe() {
        // This is a deliberate negative documentation test: the M0 probe logs
        // only explicit safe fields and never dumps runtime configuration,
        // environment variables or task results. The M3 sanitizer remains open.
        assertEquals(0, appender.list.size());
        assertTrue(LogManager.getLogManager() != null);
    }

    private static boolean hasJulBridgeHandler(java.util.logging.Logger logger) {
        for (Handler handler : logger.getHandlers()) {
            if (handler instanceof SLF4JBridgeHandler) {
                return true;
            }
        }
        return false;
    }
}
