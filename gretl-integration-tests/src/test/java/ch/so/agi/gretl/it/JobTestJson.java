package ch.so.agi.gretl.it;

import ch.so.agi.gretl.runtime.logging.Json;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/** Field access for the reports and JSON event streams emitted by test jobs. */
final class JobTestJson {
    private JobTestJson() {}

    static Map<?, ?> read(Path path) throws IOException {
        return object(Json.parseFile(path, 16L << 20));
    }

    static Map<?, ?> object(Object value) {
        return assertInstanceOf(Map.class, value);
    }

    static Map<?, ?> execution(Map<?, ?> report) {
        return object(report.get("execution"));
    }

    static Stream<Map<?, ?>> errors(Map<?, ?> report) {
        List<?> errors = assertInstanceOf(List.class, report.get("errors"));
        return errors.stream().map(JobTestJson::object);
    }

    static Map<?, ?> primaryError(Map<?, ?> report) {
        var id = execution(report).get("primaryErrorRef");
        return errors(report).filter(error -> Objects.equals(id, error.get("id"))).findFirst().orElseThrow();
    }

    static Stream<Map<?, ?>> events(String stderr, String eventName) {
        return stderr.lines().filter(line -> line.stripLeading().startsWith("{"))
                .map(Json::parse).map(JobTestJson::object).filter(event -> eventName.equals(event.get("event_name")));
    }
}
