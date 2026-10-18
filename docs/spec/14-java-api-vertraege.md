# 14 — Verbindliche Java-API und Methodenverträge

**Version:** 4.1, 18. Oktober 2026.  
**Status:** Implementierungsspezifikation. Die eingebetteten Java-Dateien sind eine kompilierbare API-Referenz, kein fertiger Runner.  
**Normativ für:** öffentliche Java-SPI, Datenklassen, Exceptions, Context-Lebensdauer und typisierte Metadatenhelfer. Ausführungssemantik: [03](03-runtime-vertraege.md); interne Verantwortlichkeiten: [15](15-runner-klassendesign.md); Logging: [16](16-logging-und-telemetrie.md).

## API-001 — Geltungsbereich, Namensraum und Änderungsregel

Das Repository heisst `gretl-runtime`, das Produkt weiterhin GRETL. Die neue SPI verwendet **`ch.so.agi.gretl.runtime.api`**. Damit bleibt sie unter `ch.so.agi.gretl`, kollidiert aber nicht mit bisherigen Gradle-Tasktypen. Die früheren illustrativen `ch.so.agi.gretllight`-Importe sind ersetzt. Der ServiceLoader-Eintrag lautet `META-INF/services/ch.so.agi.gretl.runtime.api.OperationProvider`.

Jede hier als Java-Datei bezeichnete Definition wird einzeln angelegt. Zusammen bilden sie den gesamten verpflichtenden Anfangsbestand von `gretl-api`; `ApiChecks` ist paketprivat. Die Produkt-Toolchain bleibt Java 25 gemäss Kapitel 07. Die hier durchgeführte zusätzliche Syntaxprüfung mit dem verfügbaren JDK 21 ist **keine** Änderung der Produktbasis und keine vollständige Laufzeitqualifikation.

Keine Abhängigkeit von Gradle, Groovy, SLF4J, Logback, OpenTelemetry, GeoTools oder INTERLIS erscheint in der SPI. Ein kleiner `TaskLogger` schützt diese Grenze; seine Implementierung benutzt ein etabliertes Logging-Backend. Es wird kein eigenes allgemeines Loggingframework entwickelt.

Die Signaturen sind verbindlich. Private Algorithmen bleiben Implementierungsspielraum. Während der MVP-Entwicklung dürfen Schnittstellen nach einem belegten Problem angepasst werden, aber Vertrag, Beispiel und Tests müssen gemeinsam geändert werden. Neue abstrakte Methoden einer veröffentlichten SPI sind nicht automatisch binärkompatibel. Keine allgemeine Kompatibilitätsgarantie für ältere, anders gebaute Erweiterungen behaupten; `gretl-api` und tatsächlicher Erweiterungsbestand werden inventarisiert und geprüft.

## API-002 — Allgemeine Wert- und Fehlerregeln

Standard: Argumente, Rückgabewerte, Klassen und Sammlungen sind nicht null. Ausnahmen sind ausdrücklich dokumentiert: fehlende optionale Jobparameter, ein `Void`-/Actionresultat ohne Wert, als nullable deklarierte Ergebnisfelder und der optionale `Throwable` im Logger. `Optional`-Container selbst sind nie null. Leere Sammlungen sind erlaubt, soweit der jeweilige Vertrag nichts anderes sagt.

Öffentliche Records kopieren ihre Sammlungen defensiv. Das bedeutet keine generische Tiefenkopie von Fachwerten. Eine `config(alias)`-JSON-Struktur ist dagegen vollständig eingefroren, weil ihr Wertuniversum begrenzt ist: Strings, Booleans, Zahlen, null, Listen und String-Key-Maps. `Map.copyOf` allein genügt dort wegen zulässiger JSON-null-Werte nicht; eigene rekursive Kopie plus unveränderliche Wrapper verwenden.

Falsche statische Providerdefinitionen erzeugen `IllegalArgumentException`; nicht-null-Verletzungen können `NullPointerException` liefern. Der Loader ordnet dies als Definitionsfehler Code 2 ein, solange kein Run begonnen hat. Falsche späte Taskkonfiguration wird Code 10. Fachliche Exceptions werden am synchronen Aufruf unverändert weitergegeben, sofern keine unvermeidbare Sprach-/Reflection-Hülle entfernt werden muss. Kein Catch-and-rethrow ohne ursprüngliche Ursache.

`InputField.allowedValues` ist dokumentierende Metainformation; **`convert` muss dieselbe Einschränkung tatsächlich prüfen**. Der Core errät keine fachliche Enumkonversion anhand eines Typnamens. Defaults durchlaufen denselben Konverter und Setter wie Anwenderwerte. `required=true` plus ein Taskfeld-Default ist zulässig: die Vorgabe erfüllt das Feld. Das ist bewusst anders als die verbotene Kombination `required` plus `defaultValue` bei öffentlichen Jobparameterdeklarationen.

## API-010 — Wiederverwendbare Operation und Metadaten

`ConfigBuilder.build()` erzeugt einen vollständigen fachlichen Request und prüft feldübergreifende Regeln. Es führt weder Netzwerkzugriffe noch die Fachoperation aus. Eine Existenzprüfung von Eingangspfaden im Handler bleibt möglich. Builder- und Handlerfactory liefern **je konkretem Aufruf eine frische Instanz**. Unveränderliche Fachservices dürfen innerhalb der Bibliothek geteilt sein, benötigen aber eigene dokumentierte Lebensdauer.

`OperationHandler.execute` ist synchron. Nicht-null-Resultat muss zur deklarierten `resultType` passen. `Void.class` erlaubt null. Eine Action darf ebenfalls normal mit null enden. Ein nicht-Void-Handler, der null liefert, verletzt dagegen seinen Providervertrag: `NULL_OPERATION_RESULT`, Code 10. Diese Präzisierung ersetzt die pauschale Gleichbehandlung jedes Handler-null aus 3.0; sie verhindert zufällig verschwundene notwendige Ausgänge. Der generische Runner modelliert Erfolg ohne Wert weiterhin ausdrücklich.

Der Invoker prüft den obersten Resultattyp **vor** Ressourcenfreigabe und Ergebnisregistrierung. Ausgänge werden nicht vorab alle gelesen: `OutputField.read` läuft erst bei tatsächlichem Zugriff, ist rein lesend und darf keine neue Verarbeitung auslösen. Unerlaubtes null oder eine Accessorexception wird am konsumierenden Task als Providervertragsverletzung gemeldet; der bereits abgeschlossene Producer wird nicht rückwirkend umgeschrieben.

### Java-Datei `ConfigBuilder.java`

<!-- java-reference: ConfigBuilder.java -->
```java
package ch.so.agi.gretl.runtime.api;

public interface ConfigBuilder<C> { C build(); }
```

### Java-Datei `OperationHandler.java`

<!-- java-reference: OperationHandler.java -->
```java
package ch.so.agi.gretl.runtime.api;

@FunctionalInterface
public interface OperationHandler<C, R> {
    R execute(C request, OperationContext context) throws Exception;
}
```

### Java-Datei `OperationProvider.java`

<!-- java-reference: OperationProvider.java -->
```java
package ch.so.agi.gretl.runtime.api;

public interface OperationProvider {
    java.util.Collection<OperationType<?, ?>> operations();
}
```

### Java-Datei `InputField.java`

<!-- java-reference: InputField.java -->
```java
package ch.so.agi.gretl.runtime.api;

public record InputField<C, V>(
    String name, String valueType, String description, boolean required,
    java.util.Optional<V> defaultValue, java.util.List<String> allowedValues,
    String example, java.util.function.Function<Object, V> convert,
    java.util.function.BiConsumer<ConfigBuilder<C>, V> assign
) {
    public InputField {
        name = ApiChecks.text(name, "name");
        valueType = ApiChecks.text(valueType, "valueType");
        description = ApiChecks.text(description, "description");
        java.util.Objects.requireNonNull(defaultValue, "defaultValue");
        allowedValues = java.util.List.copyOf(allowedValues);
        example = ApiChecks.text(example, "example");
        java.util.Objects.requireNonNull(convert, "convert");
        java.util.Objects.requireNonNull(assign, "assign");
    }
}
```

### Java-Datei `OutputField.java`

<!-- java-reference: OutputField.java -->
```java
package ch.so.agi.gretl.runtime.api;

public record OutputField<R, V>(
    String name, String valueType, String description, boolean nullable,
    java.util.function.Function<R, V> read
) {
    public OutputField {
        name = ApiChecks.text(name, "name");
        valueType = ApiChecks.text(valueType, "valueType");
        description = ApiChecks.text(description, "description");
        java.util.Objects.requireNonNull(read, "read");
    }
}
```

### Java-Datei `OperationType.java`

<!-- java-reference: OperationType.java -->
```java
package ch.so.agi.gretl.runtime.api;

public record OperationType<C, R>(
    String id, String symbol, String description, Class<C> requestType,
    Class<R> resultType,
    java.util.function.Supplier<? extends ConfigBuilder<C>> builderFactory,
    java.util.function.Supplier<? extends OperationHandler<C, R>> handlerFactory,
    java.util.List<InputField<C, ?>> inputs,
    java.util.List<OutputField<R, ?>> outputs, String example
) {
    public OperationType {
        id = ApiChecks.text(id, "id");
        symbol = ApiChecks.text(symbol, "symbol");
        description = ApiChecks.text(description, "description");
        java.util.Objects.requireNonNull(requestType, "requestType");
        java.util.Objects.requireNonNull(resultType, "resultType");
        java.util.Objects.requireNonNull(builderFactory, "builderFactory");
        java.util.Objects.requireNonNull(handlerFactory, "handlerFactory");
        inputs = java.util.List.copyOf(inputs);
        outputs = java.util.List.copyOf(outputs);
        ApiChecks.unique(inputs.stream().map(InputField::name).toList());
        ApiChecks.unique(outputs.stream().map(OutputField::name).toList());
        example = ApiChecks.text(example, "example");
        if (resultType == Void.class && !outputs.isEmpty())
            throw new IllegalArgumentException("Void-Operation ohne Ausgaenge definieren");
    }
}
```

### Java-Datei `ApiChecks.java`

<!-- java-reference: ApiChecks.java -->
```java
package ch.so.agi.gretl.runtime.api;

final class ApiChecks {
    private ApiChecks() {}
    static String text(String value, String field) {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException(field + " darf nicht leer sein");
        return value;
    }
    static void unique(java.util.List<String> names) {
        if (new java.util.HashSet<>(names).size() != names.size())
            throw new IllegalArgumentException("Doppelte Feldnamen");
    }
}
```

### Vertrag der einzelnen Methoden

| Methode | Vorbedingung / Ergebnis | Fehler / Besitzer |
|---|---|---|
| `operations()` | Reine, endliche Sammlung vollständiger Definitionen; keine Facharbeit. | Fehler gehört zur Herkunftsbibliothek. Registry kopiert die Sammlung. |
| `builderFactory.get()` | Neuer Builder, noch keine Anwenderwerte. | Null/falscher Typ ist Providerdefekt. Konfiguration zählt zum Invocation-Lifecycle. |
| `convert.apply(raw)` | Normalisiert nur ausdrücklich erlaubte Werte; Strings aus `CharSequence` bei Textfeldern. | Unpassende Werte mit feldbezogener Meldung, keine pauschale `toString()`-Konversion. |
| `assign.accept(builder,value)` | Setzt genau ein Feld; spätere Zuweisung ersetzt frühere. | Keine versteckte Listenaddition, keine I/O. |
| `build()` | Neuer oder unveränderlicher vollständiger Request. | Fachliche Konfigurationsfehler bleiben Code 10 beim Taskstart. |
| `handlerFactory.get()` | Neuer Handler nach erfolgreicher Konfiguration. | Keine Erstellung bei `when=false` oder `plan`. |
| `execute(request,context)` | Tatsächliche fachliche Verarbeitung. | Eigene Exception zulässig. Offene Ressourcen vor Rückgabe schliessen oder im Scope registrieren. |
| `read.apply(result)` | Lesen genau eines beschriebenen Ausgangs. | Keine reflektive Gettererratung, keine verzögerten Datenbankabfragen. |

Ein resultierender Record mit einer Liste muss diese selbst kopieren. Die Runtime kennt seine innere Fachstruktur nicht. Metadaten dürfen keine Credentials, offenen Streams oder Verbindungen halten. Die Registry prüft zusätzliche Namensregeln und Konflikte aus Kapitel 04; die kompakten Recordkonstruktoren ersetzen diese Prüfung nicht.

## API-011 — Eigene Tasks ohne unnötigen Deklarationsballast

Eine zweite SPI oder ein reflexionsbasiertes Annotationsframework ist nicht nötig. **`Fields` baut dieselben `InputField`-/`OutputField`-Werte.** Es kapselt nur den bisher im Provider sichtbaren Buildercast. `Class.cast` prüft den tatsächlichen Builder; es sucht keine Properties. Erweiterungsautoren können die Records weiterhin direkt verwenden.

Ein kleiner Task braucht nicht fünf separate Quelldateien: Request, Resultat und Builder dürfen als statische geschachtelte Typen eines Providers stehen; der Handler kann eine Lambda sein. Grössere Fachoperationen werden weiterhin sinnvoll getrennt. Die gemeinsame Beispieloperation in Kapitel 04 verwendet genau diese Form. Ihr Verhalten, ihre Hilfe und ihre ResultView unterscheiden sich nicht von mitgelieferten Tasks.

### Java-Datei `Fields.java`

<!-- java-reference: Fields.java -->
```java
package ch.so.agi.gretl.runtime.api;

public final class Fields {
    private Fields() {}
    public static <C, B extends ConfigBuilder<C>, V> InputField<C, V> input(
        String name, String valueType, String description, boolean required,
        java.util.Optional<V> defaultValue, java.util.List<String> allowedValues,
        String example, Class<B> builderClass,
        java.util.function.Function<Object, V> converter,
        java.util.function.BiConsumer<B, V> setter
    ) {
        java.util.Objects.requireNonNull(builderClass, "builderClass");
        java.util.Objects.requireNonNull(setter, "setter");
        return new InputField<>(name, valueType, description, required,
            defaultValue, allowedValues, example, converter,
            (builder, value) -> setter.accept(builderClass.cast(builder), value));
    }
    public static <C, B extends ConfigBuilder<C>> InputField<C, java.nio.file.Path>
    requiredPath(String name, String description, Class<B> builderClass,
                 java.util.function.BiConsumer<B, java.nio.file.Path> setter) {
        return input(name, "path", description, true, java.util.Optional.empty(),
            java.util.List.of(), "file('input.xtf')", builderClass,
            value -> {
                if (value instanceof java.nio.file.Path path) return path;
                throw new IllegalArgumentException(name + " erwartet einen Path");
            }, setter);
    }
    public static <R, V> OutputField<R, V> output(
        String name, String valueType, String description, boolean nullable,
        java.util.function.Function<R, V> reader
    ) { return new OutputField<>(name, valueType, description, nullable, reader); }
}
```

## API-020 — Identität und Referenzen

`runId` wird pro tatsächlichem Run neu erzeugt. `jobName` ist ein stabiler, vom Startsetup gelieferter Name, andernfalls der Basename des Scripts; er ist kein geheimer Pfad und keine einmalige UUID. `taskId` bezeichnet einen öffentlichen DAG-Knoten. `invocationId` ist eine neue runlokale ID je Fachaufruf, zum Beispiel `i000001`. IDs sind Korrelation, keine vom Jobautor zu parsenden Arbeitsverzeichnisse.

Job-Cleanup hat keine Task-/Invocation-ID; sein eigener `cleanup.name` steht im Loggingkontext. Ein benannter Taskcontext hat eine Task-ID, aber keine Invocation-ID. Die Konfiguration und der Handler eines Fachaufrufs haben beide dieselbe Invocation-ID.

Eine TaskRef ist opaque: **nur durch die aktuelle JobDefinition ausgegebene Referenzinstanzen werden akzeptiert**. Der Runner validiert ihre Herkunft anhand seiner Registrierung, nicht anhand eines vom Aufrufer nachgebauten `id()`-/`resultType()`-Paars. Stringzugriffe der Groovy-Fassade lösen dagegen ausdrücklich gegen die aktuelle Definition auf. TaskRefs sind keine JSON-Transportobjekte.

### Java-Datei `ExecutionIdentity.java`

<!-- java-reference: ExecutionIdentity.java -->
```java
package ch.so.agi.gretl.runtime.api;

public record ExecutionIdentity(
    java.util.UUID runId, String jobName,
    java.util.Optional<String> taskId, java.util.Optional<String> invocationId
) {
    public ExecutionIdentity {
        java.util.Objects.requireNonNull(runId, "runId");
        jobName = ApiChecks.text(jobName, "jobName");
        java.util.Objects.requireNonNull(taskId, "taskId");
        java.util.Objects.requireNonNull(invocationId, "invocationId");
        taskId.ifPresent(v -> ApiChecks.text(v, "taskId"));
        invocationId.ifPresent(v -> ApiChecks.text(v, "invocationId"));
        if (invocationId.isPresent() && taskId.isEmpty())
            throw new IllegalArgumentException("Invocation benoetigt Task");
    }
}
```

### Java-Datei `TaskRef.java`

<!-- java-reference: TaskRef.java -->
```java
package ch.so.agi.gretl.runtime.api;

public interface TaskRef<R> {
    String id();
    Class<R> resultType();
}
```

## API-030 — Allgemeiner Kontext, vollständig festgelegt

`ContextView` enthält die auch für Cleanup sinnvollen kleinen Fähigkeiten. `OperationContext` ergänzt aufrufbezogene Dienste. `TaskContext` gehört zur Action oder zur aufrufenden benannten Taskausführung. Ein Handler erhält **nur OperationContext**; er kann keine Vorgängerresultate lesen, keine fremden Tasks starten und den ganzen Job nicht fachlich orchestrieren. Seine eigenen Java-Helfer bleiben gewöhnliche interne Methoden.

`TaskContext.call(type, validatedRequest)` ist die reine Java-Expertenform eines synchronen Aufrufs. Der Request muss bereits gebaut und fachlich validiert sein. Es werden keine DSL-Defaults erneut gesetzt. Der Invoker prüft Typ und Nicht-null, erzeugt einen frischen Handler und einen eigenen Invocation-Scope. Die Groovy-Methode `ctx.run(type) { ... }` ist **keine direkte Weiterleitung an `call` nach einer frühzeitigen Konfiguration**: sie muss ihre RequestPreparation in den neuen Invocation-Scope geben, damit Konfigurationsfehler, Pfade und Logs bereits zum richtigen Aufruf gehören. Beide Wege enden im selben OperationInvoker.

Es gibt keine Java-Closure, keinen Service-Locator und keine SQL-Verbindung im Context. `config(alias)` bleibt ein unveränderlicher fachlich interpretierter Konfigurationsabschnitt. Datenbank- oder Publisherdienste werden nicht durch einen generischen `getService(Class)`-Hintereingang eingeführt.

### Java-Datei `ContextView.java`

<!-- java-reference: ContextView.java -->
```java
package ch.so.agi.gretl.runtime.api;

public interface ContextView {
    ExecutionIdentity identity();
    Object parameter(String name);
    <T> T parameter(String name, Class<T> type);
    java.util.Map<String, Object> config(String alias);
    java.nio.file.Path file(String path);
    java.nio.file.Path path(String path);
    java.nio.file.Path workPath(String relativePath) throws java.io.IOException;
    TaskLogger log();
    void report(Diagnostic diagnostic);
}
```

### Java-Datei `OperationContext.java`

<!-- java-reference: OperationContext.java -->
```java
package ch.so.agi.gretl.runtime.api;

public interface OperationContext extends ContextView {
    SecretValue secret(String alias);
    String sensitive(String value);
    CancellationToken cancellation();
    TimeBudget budget();
    ResourceScope resources();
    void progress(Progress progress);
}
```

### Java-Datei `TaskContext.java`

<!-- java-reference: TaskContext.java -->
```java
package ch.so.agi.gretl.runtime.api;

public interface TaskContext extends OperationContext {
    <R> R result(TaskRef<R> task);
    <R> java.util.Optional<R> resultOptional(TaskRef<R> task);
    <C, R> R call(OperationType<C, R> type, C validatedRequest) throws Exception;
    void finishJob(String reason);
}
```

### Java-Datei `CleanupContext.java`

<!-- java-reference: CleanupContext.java -->
```java
package ch.so.agi.gretl.runtime.api;

public interface CleanupContext extends ContextView {
    ResourceScope resources();
    TimeBudget budget();
}
```

### Java-Datei `ContextAccessException.java`

<!-- java-reference: ContextAccessException.java -->
```java
package ch.so.agi.gretl.runtime.api;

public final class ContextAccessException extends IllegalStateException {
    private static final long serialVersionUID = 1L;
    private final String code;
    public ContextAccessException(String code, String message) {
        super(message);
        this.code = ApiChecks.text(code, "code");
    }
    public String code() { return code; }
}
```

### API-031 — Methodentabelle

| Signatur | Präziser Vertrag |
|---|---|
| `identity()` | Unveränderlicher Wert; auch nach Scopeende lesbar. Keine Engineinitialisierung. |
| `parameter(name)` | Deklarierter typisierter Wert; optional fehlend null. Unbekannt: `UNKNOWN_PARAMETER`. |
| `parameter(name,type)` | Wrapperklasse, keine primitiven `Class`-Werte. Null bleibt null; sonst `Class.isInstance`. Kein zusätzlicher Konverter. Falsch: `PARAMETER_TYPE_MISMATCH`. |
| `config(alias)` | Vorbereitetes, tief unveränderliches JSON-Objekt unter taskConfig. Fehlt: `UNKNOWN_CONFIG_ALIAS`. Kein Zugriff auf Configroot. |
| `file(text)` | `Path` gegen Jobroot; normalisiert. Keine Dateierstellung/Existenzgarantie. |
| `path(text)` | `Path` gegen ursprüngliches Start-CWD; keine Dateierstellung. |
| `workPath(relative)` | Innerhalb eigenen Scopebereichs; Elternordner erstellen, Ausgabedatei nicht. Ausbruch: `WORK_PATH_ESCAPE`; Dateisystemfehler: IOException. |
| `log()` | Scopegebundener TaskLogger. Bekannte Identität wird vom Adapter ergänzt, nicht vom Anwender. |
| `report(diagnostic)` | Explizite Diagnose erfassen, Grössen begrenzen, keine Statusentscheidung. Artefaktinhalt wird nicht automatisch gelesen/hochgeladen. |
| `secret(alias)` | Lazy, maskierter SecretValue für den aktuellen Run. Kein Quellzugriff beim Erzeugen. |
| `sensitive(value)` | Bekannten dynamischen Secrettext beim Run-Redactor registrieren und denselben String zurückgeben. Kein dauerhafter Speicher; Limits gemäss Kapitel 16. |
| `cancellation()` | Nur lesendes Token. Ein Custom-Task kann nicht die globale Cancellation zurücknehmen. |
| `budget()` | Geerbtes verbleibendes Budget, monoton berechnet. |
| `resources()` | Scope für Lebensdauer dieses Tasks/Aufrufs/Cleanup. Kein vom Anwender vorzeitig schliessbarer Gesamtrun. |
| `progress(value)` | Neuesten aufrufbezogenen Zählerstand melden, keine Zeile je Datensatz. Kein Run-/Taskstatuswechsel. |
| `result(ref)` | Nur direkter/transitiver Datenvorgänger, erfolgreich und mit Wert; rohe Javawerte. |
| `resultOptional(ref)` | Nur regulärer Skip oder Erfolg ohne Wert ergibt leer; echte Zugriffsfehler bleiben Fehler. |
| `call(type,request)` | Genau ein synchroner Aufruf; keine DAG-Registrierung und kein Retry. |
| `finishJob(reason)` | Speichert Jobstop und wirft internes Kontrollsignal; kehrt regulär nicht zurück. Semantik aus RUN-042. |

Ein Cache eines Kontextobjekts über sein Scopeende ist unzulässig. Der Context impliziert keine Sandbox gegen gewöhnliche Java-Aufrufe. Eine Taskbibliothek kann Dateien direkt öffnen, trägt dann aber die Verantwortung für deren Ressourcen und Logs.

### API-032 — Context-Lebensdauer und Threads

Interner Zustand: `ACTIVE → CLOSING → CLOSED`. Er entsteht vor der jeweiligen Konfiguration/Action; `CLOSING` beginnt beim Abschluss der primären Arbeit, bevor registrierte Ressourcen geschlossen werden.

| Zugang | ACTIVE | CLOSING | CLOSED |
|---|---|---|---|
| Identität | erlaubt | erlaubt | erlaubt |
| Parameter, Config, bereits berechnete Pfade | erlaubt | erlaubt | ContextAccessException `CONTEXT_CLOSED` |
| Log/Diagnose | erlaubt | erlaubt | kein Taskaufruf mehr; gecachter Logger schreibt höchstens `gretl.late_log` mit alter Identität, nie Identität des nächsten Tasks |
| Neue Ressource/`workPath`-Elternerzeugung | erlaubt | abgelehnt | abgelehnt |
| Progress | erlaubt | letzter vorhandener Stand | abgelehnt |
| `call`, `ctx.run`, `finishJob` | nur bei zulässiger Rolle und nicht gestoppt | abgelehnt | abgelehnt |

Kontexte sind nicht als beliebige parallel nutzbare Jobschnittstelle freigegeben. Der Ausführungsthread besitzt mutable Scopes. `log`, `progress` und das lesende CancellationToken dürfen von ausdrücklich zugeordneten, vom Adapter verwalteten Hilfsthreads benutzt werden. Vor dem Scopeende müssen diese Threads beendet/joined sein. Es gibt keine automatische MDC-Vererbung zu beliebigen Executor-Threads und keine stille Zuordnung fremder Hintergrundlogs zur gerade zufällig aktiven Task.

Die Java-SPI erhält keine OTel-Kontexttypen. Eine spätere Instrumentierung kapselt Scopeaufnahme und -wiederherstellung im Observability-Adapter. Die Scopeidentität stammt immer aus dem expliziten Context, nicht ausschliesslich aus einem ThreadLocal.

## API-040 — Abbruch, Budget und Ressourcen

`CancellationToken.reason()` ist leer, solange kein kontrollierter Abbruch vorliegt. Ein einmal gesetzter Grund wird nicht zurückgenommen. `throwIfCancellationRequested` wirft die unten definierte Exception. Ein gesetztes Interrupted-Flag wird bei kontrollierter Erkennung nicht als Erfolg verschluckt. Die Runtime kann für Cleanup das Flag zeitweilig sichern und klären; Wiederherstellung und Primärfehler bleiben gemäss Kapitel 15 definiert.

`TimeBudget.remaining()` ist leer bei unbegrenztem Budget, sonst nie negativ; abgelaufen ergibt Duration.ZERO. `expired()` ist dann true. Ein Kindbudget ist das Minimum des verbleibenden Elternbudgets und seines eigenen Limits. Es beginnt nicht den Jobtimeout neu. Kein Vertrauen auf Systemuhrsprünge zur Dauerberechnung.

`ResourceScope.manage(resource)` gibt dieselbe Instanz zurück. Registrierung derselben Objektidentität zweimal im gleichen Scope ist `IllegalArgumentException`. Ressourcen werden in umgekehrter Reihenfolge genau einmal durch diesen Scope geschlossen. Nach erfolgter Übernahme nicht zusätzlich manuell oder per try-with-resources schliessen; wähle **einen** Besitzer. Ein gesondertes Release-/Transfer-of-ownership-Framework gehört nicht zum MVP.

### Java-Datei `CancelReason.java`

<!-- java-reference: CancelReason.java -->
```java
package ch.so.agi.gretl.runtime.api;

public enum CancelReason { REQUESTED, INTERRUPTED, TIMEOUT }
```

### Java-Datei `TaskCancelledException.java`

<!-- java-reference: TaskCancelledException.java -->
```java
package ch.so.agi.gretl.runtime.api;

public final class TaskCancelledException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final CancelReason reason;
    public TaskCancelledException(CancelReason reason) {
        super("Ausfuehrung abgebrochen: " + java.util.Objects.requireNonNull(reason));
        this.reason = reason;
    }
    public CancelReason reason() { return reason; }
}
```

### Java-Datei `CancellationToken.java`

<!-- java-reference: CancellationToken.java -->
```java
package ch.so.agi.gretl.runtime.api;

public interface CancellationToken {
    boolean isCancellationRequested();
    java.util.Optional<CancelReason> reason();
    void throwIfCancellationRequested() throws TaskCancelledException;
}
```

### Java-Datei `TimeBudget.java`

<!-- java-reference: TimeBudget.java -->
```java
package ch.so.agi.gretl.runtime.api;

public interface TimeBudget {
    java.util.Optional<java.time.Duration> remaining();
    boolean expired();
}
```

### Java-Datei `ResourceScope.java`

<!-- java-reference: ResourceScope.java -->
```java
package ch.so.agi.gretl.runtime.api;

public interface ResourceScope {
    <T extends AutoCloseable> T manage(T resource);
}
```

### API-041 — Fehler bei Ressourcenabschluss

Der interne `CloseStack.closeAll()` versucht jede registrierte Freigabe und liefert eine geordnete Liste echter Fehler. Er bestimmt **nicht** allein den Jobstatus. `FailureArbiter` kombiniert diese Liste mit primärem Fehler, erkanntem Cancel und Stopanforderung. Ein Finishsignal ist keine echte Primärexception. Daher wird ein Closefehler nach `finishJob` sichtbar und führt zu FAILED.

Hat der Invocationbody bereits eine echte Exception, bleibt sie primär; Closefehler werden als suppressed hinzugefügt, ohne sie selbst als neue Primärursache zu loggen. Ohne Primärfehler wird der erste Closefehler primär. Der Invoker veröffentlicht das Ergebnis erst nach diesem Schritt. Ein Action-Catch darf auch diese weitergereichte Exception bewusst behandeln. Ein späterer unbehandelter Job-Cleanupfehler kann den Job weiterhin fehlschlagen lassen.

## API-050 — Secrets

Ein über `context.secret(alias)` bezogener SecretValue hat Run-Lebensdauer. Der resolver prüft, dass der Run noch offen ist, liest die konfigurierte Quelle beim ersten Reveal, registriert den Wert **vor seiner Rückgabe** beim Redactor und memoisiert ihn für diesen Run. Nach Runabschluss schlägt ein neues Reveal mit `CONTEXT_CLOSED` fehl. Die Klasse selbst enthält keine globale Secretregistry; ihr Supplier wird von der Runtime kontrolliert.

Die öffentliche Factory ist ein kleiner Aufbaupunkt für Runtime/Testdoubles. Wer selbst einen Supplier übergibt, erhält dadurch weder die Kontextprüfung noch eine automatische Redaction fremder Logs. Standardtasks verwenden die vom Context erhaltenen Werte. Das Maskieren von `toString` ist keine Garantie gegen einen absichtlich ausgegebenen `reveal()`-Wert oder ein Heapdump.

Dynamische Token aus Antworten können als String in Actions bleiben: `return ctx.sensitive(token)`. Der bekannte Originalwert wird dann auf sämtlichen produktverantworteten Log-/Reportpfaden maskiert. Es wird weder ein Token in einen Hashlog geschrieben noch beliebiger Resultatinhalt zur Secretentdeckung durchsucht.

### Java-Datei `SecretValue.java`

<!-- java-reference: SecretValue.java -->
```java
package ch.so.agi.gretl.runtime.api;

public final class SecretValue {
    private final java.util.function.Supplier<String> resolver;
    private SecretValue(java.util.function.Supplier<String> resolver) {
        this.resolver = java.util.Objects.requireNonNull(resolver, "resolver");
    }
    public static SecretValue deferred(java.util.function.Supplier<String> resolver) {
        return new SecretValue(resolver);
    }
    public String reveal() {
        return java.util.Objects.requireNonNull(resolver.get(), "secret value");
    }
    @Override public String toString() { return "[REDACTED]"; }
}
```

## API-060 — Logging, Diagnose und Fortschritt

Dieser öffentliche Teil ist absichtlich klein. **Nicht** jede Task soll eine Logging-Konfiguration oder eigene Spanverwaltung implementieren. Die Runtime ergänzt Start/Ende und Kontext automatisch; der Adapter liefert nützliche fachliche Phasen und Diagnosen.

`TaskLogger.log` akzeptiert eine explizite Map aus Stringwerten. Es gibt keine Überladung für beliebige Objektgraphen. `cause` darf null sein; eine vorhandene Ursache wird strukturiert mit Cause-/Suppressed-Kette erfasst. `eventName` folgt einer stabilen Namenskonvention. Custom-Events beginnen `task.` oder einem Bibliotheksnamespace; `gretl.*`-Lifecycle wird nur vom Runner erzeugt. Die eingebauten Convenience-Methoden verwenden `gretl.task.message`, den einen dafür reservierten allgemeinen Nachrichtentyp.

`isEnabled` bezieht sich auf die konfigurierten Ausgabesenken; damit kann teure DEBUG-Aufbereitung vermieden werden. Sink-I/O-Fehler werden als Loggingdegradation erfasst, nicht als fachliche Handlerexception eingeworfen. **Ungültige API-Argumente** sind dagegen Programmierfehler und dürfen sofort scheitern. Logging ist kein Rückkanal zur Tasksteuerung.

`Diagnostic` dient einer endlichen, expliziten Diagnose und nicht der Erzeugung eines Diagnostics pro verarbeiteter Zeile. Für viele Validierungsfehler: begrenzte Samples im zentralen Log und vollständiger geschützter Bericht als Artefakt. `restricted` kennzeichnet den Zugriff auf das Artefakt; es wird nicht automatisch öffentlich archiviert. Schon der Pfad kann sensibel sein und wird entsprechend dem Berichtsziel dargestellt.

`Progress` zählt kumulativ innerhalb einer Phase. Ein Phasenwechsel darf bei null beginnen. Bei unbekanntem Gesamtumfang bleibt `total` leer; der Runner erfindet keine Prozentzahl. `unit` ist ein stabiler Wert wie `bytes`, `objects` oder `statements`. Die Runtime koalesziert schnelle Updates; Heartbeat und tatsächlich beobachteter Fortschritt sind unterschiedliche Signale.

### Java-Datei `LogLevel.java`

<!-- java-reference: LogLevel.java -->
```java
package ch.so.agi.gretl.runtime.api;

public enum LogLevel { TRACE, DEBUG, INFO, WARN, ERROR }
```

### Java-Datei `TaskLogger.java`

<!-- java-reference: TaskLogger.java -->
```java
package ch.so.agi.gretl.runtime.api;

public interface TaskLogger {
    boolean isEnabled(LogLevel level);
    void log(LogLevel level, String eventName, String message,
             java.util.Map<String, String> fields, Throwable cause);
    default void info(String message) {
        log(LogLevel.INFO, "gretl.task.message", message, java.util.Map.of(), null);
    }
    default void debug(String message) {
        log(LogLevel.DEBUG, "gretl.task.message", message, java.util.Map.of(), null);
    }
    default void warn(String message) {
        log(LogLevel.WARN, "gretl.task.message", message, java.util.Map.of(), null);
    }
    default void error(String message, Throwable cause) {
        log(LogLevel.ERROR, "gretl.task.message", message, java.util.Map.of(), cause);
    }
}
```

### Java-Datei `DiagnosticSeverity.java`

<!-- java-reference: DiagnosticSeverity.java -->
```java
package ch.so.agi.gretl.runtime.api;

public enum DiagnosticSeverity { INFO, WARN, ERROR }
```

### Java-Datei `DiagnosticArtifact.java`

<!-- java-reference: DiagnosticArtifact.java -->
```java
package ch.so.agi.gretl.runtime.api;

public record DiagnosticArtifact(
    String name, java.nio.file.Path path, String mediaType, boolean restricted
) {
    public DiagnosticArtifact {
        name = ApiChecks.text(name, "name");
        java.util.Objects.requireNonNull(path, "path");
        path = path.toAbsolutePath().normalize();
        mediaType = ApiChecks.text(mediaType, "mediaType");
    }
}
```

### Java-Datei `Diagnostic.java`

<!-- java-reference: Diagnostic.java -->
```java
package ch.so.agi.gretl.runtime.api;

public record Diagnostic(
    DiagnosticSeverity severity, String code, String message,
    java.util.Map<String, String> details,
    java.util.List<DiagnosticArtifact> artifacts
) {
    public Diagnostic {
        java.util.Objects.requireNonNull(severity, "severity");
        code = ApiChecks.text(code, "code");
        message = ApiChecks.text(message, "message");
        details = java.util.Map.copyOf(details);
        artifacts = java.util.List.copyOf(artifacts);
    }
}
```

### Java-Datei `Progress.java`

<!-- java-reference: Progress.java -->
```java
package ch.so.agi.gretl.runtime.api;

public record Progress(
    String phase, long completed, java.util.OptionalLong total, String unit
) {
    public Progress {
        phase = ApiChecks.text(phase, "phase");
        unit = ApiChecks.text(unit, "unit");
        java.util.Objects.requireNonNull(total, "total");
        if (completed < 0 || (total.isPresent() && total.getAsLong() < completed))
            throw new IllegalArgumentException("Ungueltiger Fortschritt");
    }
}
```

## API-070 — Vollständigkeit und Implementierungsreihenfolge

Diese Datei schliesst die zuvor nur beschriebenen Contextmethoden, Signaturen, Nullability, Exceptions und Ressourcenregeln. Die API enthält bewusst keine allgemeinen SQL-Verbindungen, Publikationszustände, Telemetrieexporter, Worker-Codecs oder Serialisierung beliebiger Taskresultate.

Vor M1 werden die hier eingebetteten Java-Dateien in `gretl-api` übernommen beziehungsweise aus der Referenz erzeugt und kompiliert. Danach werden die Regeln mit einem unabhängig gebauten Provider und echten Java-Testdoubles geprüft. Eine passende Kompilation beweist noch keine korrekte Scopeverwaltung oder Groovydelegation.

Verbindliche Zuordnung zu Prüfungen: **TC-A01 bis TC-A10** und **TC-K01 bis TC-K10** in Kapitel 09. Die Groovy-Integrationsprobe TC-D12/TC-D15 sowie die neuen TC-GR01 bis TC-GR08 bleiben echte Ausführungstests mit der ausgewählten Groovy-Version. Stand der hier tatsächlich durchgeführten Prüfungen: [17](17-findings-und-pruefprotokoll.md).

