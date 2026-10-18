package ch.so.agi.gretl.runtime.logging;

import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Set;
import org.slf4j.LoggerFactory;

/** Startup guard: exactly one SLF4J provider, and it is Logback. */
public final class ProviderInventory {
    private ProviderInventory() {
    }

    public static void verify(ClassLoader classLoader) {
        Set<String> providers = new LinkedHashSet<>();
        try {
            Enumeration<URL> resources = classLoader.getResources(
                    "META-INF/services/org.slf4j.spi.SLF4JServiceProvider");
            while (resources.hasMoreElements()) {
                providers.add(resources.nextElement().toExternalForm());
            }
        } catch (IOException exception) {
            throw new IllegalStateException("SLF4J-Provider konnten nicht inventarisiert werden",
                    exception);
        }
        if (providers.size() != 1) {
            throw new IllegalStateException(
                    "Genau ein SLF4J-Provider ist erlaubt, gefunden: " + providers);
        }
        Object factory = LoggerFactory.getILoggerFactory();
        if (!"ch.qos.logback.classic.LoggerContext".equals(factory.getClass().getName())) {
            throw new IllegalStateException(
                    "The active SLF4J provider is not Logback: "
                            + factory.getClass().getName());
        }
    }
}
