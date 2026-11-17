package ch.so.agi.gretl.runtime.logging;

import java.io.PrintStream;

/** Text or one-line JSON on stderr. */
final class ConsoleSink {
    private final PrintStream stream;
    private final LoggingConfig config;
    private final TextFormatter text;
    private boolean degraded;

    ConsoleSink(PrintStream stream, LoggingConfig config, LogContext context) {
        this.stream = stream;
        this.config = config;
        this.text = new TextFormatter(context.locale());
    }

    boolean accepts(LogEvent event) {
        return !degraded && config.enables(event.level());
    }

    void write(LogEvent event, byte[] jsonLine) {
        try {
            if (config.format() == LoggingConfig.Format.JSON) {
                if (jsonLine == null) {
                    degraded = true;
                    return;
                }
                stream.write(jsonLine, 0, jsonLine.length);
            } else {
                stream.println(text.format(event));
            }
            degraded = stream.checkError();
        } catch (RuntimeException exception) {
            degraded = true;
        }
    }

    boolean degraded() {
        return degraded;
    }
}
