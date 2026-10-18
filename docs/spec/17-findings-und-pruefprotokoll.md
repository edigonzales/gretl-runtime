# 17 — Findings, Änderungen und Abschlussprüfung

**Version:** 4.1, 18. Oktober 2026.  
**Zweck:** transparent nachweisen, welche Findings in der Spezifikation behoben sind und welche Ausführungsnachweise erst das spätere Produkt liefern muss.  
**Prüfgegenstand:** diese 19 Markdown-Dateien, deren eingebettete Java-Referenz und das kompakte separate Providerbeispiel. Keine produktiven GRETL-Jobs oder Systeme wurden geändert.

## Ergänzung 4.1 — Maven-Entscheidung und Dokumentprüfung

**Änderungsumfang:** Die bestätigte Maven-Wahl ist als BLD-001 bis BLD-008 in Kapitel 07 präzisiert. Strategisches Zielbild, AGENTS-Anweisung, Betrieb, Implementierungsplan, Paketierungsgrenze und Abnahme sind damit abgeglichen. TC-BLD01 bis TC-BLD08 prüfen den künftigen tatsächlichen Build; sie behaupten keinen schon ausgeführten Maven-Produktbuild.

| Neues Finding | Erfüllung in der Spezifikation | Noch offener Produktnachweis |
|---|---|---|
| F-11: Buildwerkzeug entschieden, aber Komplexitätsgrenzen fehlten | Ein Root-Parent, kleine Modul-POMs, gezielte Versionen/Dependencies, normale Lifecyclebindungen, begründete Profile/Plugins und kein zusätzliches Buildframework; M0-Auftrag entsprechend ergänzt | TC-BLD01 bis TC-BLD06 und TC-BLD08 im Implementierungsrepository |
| F-12: Buildvereinfachung darf Runtime-/Liefergrenzen nicht verwischen | Maven nur im Produkt-/expliziten Paketbuild; keine pauschale Shade-Vererbung, keine Testdependencies im Lieferbestand, keine Abschwächung von Logging-/Fachtests | TC-BLD07 an den tatsächlich gelieferten M3-/M6-Artefakten |

**In dieser Revision tatsächlich geprüft:** die Markdown-Dateien, interne Verweise und Definitionen sowie die Übereinstimmung unveränderter Code-/Publisherverträge mit dem bereitgestellten 4.0-Archiv. Die Java-Referenztests aus Abschnitt 4 sind historische Nachweise der Revision 4.0. Sie wurden für diese reine Buildleitlinien-Ergänzung nicht erneut ausgeführt. Auch Maven, Groovy, GeoTools und die dokumentierten Versionskandidaten wurden hier nicht neu installiert, ausgeführt oder recherchiert.

<!-- validation-v41-start -->
Der abschliessende Durchlauf für Version 4.1 prüfte **19 Markdown-Dateien**, **131 interne Dateiverweise** und die neuen Verweise auf Abschnitt 6.4. Alle **85 Codeblöcke** sind geschlossen und inhaltlich identisch mit dem bereitgestellten 4.0-Archiv. Die **5 JSON-** und **1 YAML-Beispiele** sind parsebar.

Die **148 vorhandenen Testfalldefinitionen** bleiben erhalten; acht TC-BLD-Fälle ergeben **156 eindeutige Testkennungen**. Alle referenzierten konkreten Testkennungen sind definiert. BLD-001 bis BLD-008 sind jeweils einmal definiert und aus den betroffenen Kapiteln referenziert. Es wurden keine fehlenden internen Dateiverweise, offenen Codefences oder doppelten geprüften Definitionen gefunden.

Die neun Publisher-Testzeilen sind unverändert. Neun Kapitel zu Publisher, Runtime, DSL, Native, Anwenderrezepten, API, Kernklassen und Logging sind abgesehen von ihrer Versionskopfzeile vollständig unverändert; in den anderen Kapiteln sind die Buildergänzungen ausdrücklich markiert beziehungsweise integriert. Das ist eine Dokumentkonsistenzprüfung und keine Produktabnahme. Die finalen 19 Dateien werden ohne zusätzliche Testwerkzeuge ins ZIP aufgenommen und dessen CRC sowie jedes Mitglied bytegenau mit den ausgelieferten Dateien verglichen.
<!-- validation-v41-end -->

## 1. Ergebnis

Die offenen **Spezifikationslücken** bei öffentlicher API, Context-Lebensdauer, Ergebnismodell, Kernverantwortlichkeiten, Aufrufsequenzen und Logging sind geschlossen. Das Custom-Task-Beispiel wurde vereinfacht und als getrennte Java-Bibliothek wirklich ausgeführt. Die Anwender-DSL und die MVP-Liefergrenzen bleiben erhalten.

Die vorige Empfehlung einer **echten Groovy-6-Probe** ist als konkrete, implementierbare Klassen-/Methodenreferenz und Prüfmatrix ausgearbeitet. Ihr Lauf mit der echten Produktversion wurde hier jedoch nicht durchgeführt. „Konzipiert und prüfbar spezifiziert“ ist nicht „schon ausgeführt“. Ebenso wurden die neue Runtime, Logger-/Bridgeintegration, Java-25-Gesamtkombination, ein vollständiges Job-Fat-JAR, Native Image und eine Fachanwenderstudie hier nicht freigegeben.

## 2. Abgleich mit den Findings

| Finding | Konkrete Änderung | Spezifikationsstatus / Nachweis | Offener Produktnachweis |
|---|---|---|---|
| F-01: Context nur funktional beschrieben | Vollständige ContextView-, TaskContext-, OperationContext-, CleanupContext-, Budget-, Cancellation-, Secret- und Ressourcen-Signaturen; ACTIVE/CLOSING/CLOSED | In Kapitel 14 vollständig, Quellreferenz kompiliert | Reale Context-/Thread-/Lebensdauertests TC-A05 bis TC-A09 |
| F-02: interne Klassen und Zuständigkeiten zu offen | Klassenkatalog, Zustandshoheit und Methoden mit Parametern/Rückgaben; Registry/Freeze/Planner/Invoker/ResultStore/Arbiter getrennt | Kapitel 15; keine Pflicht jede private Hilfsmethode vorzugeben | TC-K01 bis TC-K10 am echten Runner |
| F-03: Ergebniszustände können vermischt werden | TaskStatus plus ResultSlot, kein einzelnes Optional als Gesamtzustand; zulässige null-/Void- und Feld-null-Fälle | Kapitel 03/14/15; versiegeltes Zustandsfragment kompiliert | TC-A10, TC-K03, TC-D04/05 |
| F-04: kritische technische Übergänge fehlen | Invocation vor Konfiguration; Typprüfung/Freigabe vor Ergebnisregistrierung; deterministische Fehler-/Finish-/Cancel-/Cleanup-Präzedenz | CORE-022 bis CORE-025 sowie Contextvertrag | TC-K04 bis TC-K09 und vorhandene TC-E-Fälle |
| F-05: Groovy-Bindung braucht konkrete technische Referenz | Script-/Scope-/Facade-/ResultView-Klassen, Map-first-Methoden, Kopie/rehydrate, DELEGATE_ONLY, Arity, Quellenpositionen | Kapitel 04/15 spezifiziert; **kein** als bestanden ausgegebener Groovy-Smoke-Test | TC-GR01 bis TC-GR08 mit echter Groovy-Produktversion; verbindlich M2 |
| F-06: einfache Custom-Tasks zu viel Boilerplate | Eine Java-Quelldatei mit kleinen verschachtelten Typen; Fields-Factory statt Handcasts; kein Annotationframework | Reale getrennte Provider-JAR-Ausführung und Dateigrössenprüfung | Zusätzlich Anwender-DSL und operation-Hilfe am Produkt TC-A02/TC-D10 |
| F-07: über alle Teile konsistent aktualisieren | Alle bisherigen Kapitel, AGENTS, README, Hilfe, Paketierung, Milestones und Testmatrix angepasst; namespace eindeutig | Dokumentprüfung, Links/IDs/Fences/Beispieldaten geprüft | Driftkontrolle von Dokumentation und Produktbeispielen in CI |
| F-08: Logging nicht ausreichend ausgearbeitet | Eigenes normatives MVP-Kapitel zu Identität, Phasen, Ursachen, Schema, Senken, Maskierung, Bibliotheksbrücken und Limits | Kapitel 16 plus SPI und technische Rollen in 14/15 | TC-L01 bis TC-L22 nach jeweiliger A-/B-Zuordnung |
| F-09: Kubernetes/Telemetrie nicht unnötig einbauen | Backendneutraler Stream-/Reportvertrag; Cluster-/Jenkins-Erfassung getrennt; OTel und Batch-Metriken später gezielt | TEL-010 bis TEL-050 mit Primärquellen und expliziten Grenzen | Tatsächlicher Betriebsnachweis; TC-L24 nur beim gelieferten OTel-Profil |
| F-10: ursprüngliche strategische Grenzen bewahren | Kein Publisher im MVP, kein Reconcile/Business-State-Core, keine Native-/Workerpflicht; offener Standardtaskkatalog | Alle 9 aufbewahrten Publisher-Testzeilen unverändert; Kapitel 01/05/10/11 | Publisher bleibt eigener späterer Auftrag |

Damit sind alle Findings **behandelt und auf konkrete Verträge/Testorakel zurückgeführt**. Der Ausführungsanteil der Groovy-Probe und die Produktintegration sind offen und werden nicht durch Umfang der Dokumentation ersetzt.

## 3. Bewusste Präzisierungen gegenüber 3.0

Der öffentliche Namensraum lautet nun `ch.so.agi.gretl.runtime.api`. Er bleibt unter der Produktorganisation und vermeidet Kollisionen mit bisherigen Gradle-Tasktypen. ServiceLoaderpfad, Providerbeispiele und Paketierungsbeschreibung wurden mit geändert.

Nicht-Void-Handler müssen einen nicht-null Wert des deklarierten Typs liefern. Nur Actions und ausdrücklich Void deklarierte Operationen können regulär ohne Wert enden. Einzelne dokumentiert nullable Felder bleiben erlaubt. Dies ist eine kleine ausdrückliche Vertragsänderung, kein stiller Sideeffect eines neuen Genericsmodells.

Die Handlerfactory wird weiterhin für jeden Aufruf frisch verwendet. Das kompakte Beispiel nutzt eine tatsächlich neu erzeugte Handlerinstanz: eine zustandslose Java-Lambda kann von der JVM wiederverwendet werden und wäre deshalb kein verlässlicher Nachweis des streng zugesagten Instanzvertrags. Der separate Prüfharness kontrolliert jetzt Builder **und Handler** auf frische Instanzen.

Fehlerdetails werden höchstens einmal **je tatsächlicher Senke** emittiert. Eine auf WARN gefilterte innere Detailmeldung darf einen späteren ERROR nicht zu einer unauflösbaren Referenz machen. Dieser nachträglich gefundene Diagnosefall ist in LOG-050 und TC-L06 ergänzt.

Logging beginnt mit dem ersten Runtime-/Adapterpfad, nicht nach Abschluss des MVP. Echte Exporter oder Clusterinstallationen bleiben optional. Eine Log-/Reportkonfiguration ist eine allgemeine Betriebsfunktion, keine fachliche Publisher-Sonderstellung.

## 4. Tatsächlich ausgeführte Referenzprüfungen der Revision 4.0

### 4.1 Umfang und Umgebung

Verfügbare lokale Toolchain: **javac 21.0.12.1**. Die normative Produkttoolchain bleibt Java 25. Es wurden die **finalen Codeblöcke** aus Kapitel 14 extrahiert, nicht eine möglicherweise abweichende handgeschriebene Parallelversion. 27 API-Dateien wurden mit `--release 21 -Xlint:all -Werror` kompiliert. Danach wurden die API und der aus Kapitel 04 extrahierte Provider getrennt zu JARs gebaut.

Ein dritter kleiner Java-Prozess wurde nur gegen das API-JAR kompiliert und beim Start um das separate Provider-JAR ergänzt. ServiceLoader musste den Provider tatsächlich finden. Die Fachoperation las die reale Bytezahl einer temporären UTF-8-Datei. Für nicht benötigte Contextdienste verwendet der Harness ausdrücklich Testdoubles; daraus folgt keine funktionierende Logger-, Cancellation- oder ResourceScope-Implementierung.

Das versiegelte ResultSlot-/Status-Codefragment aus Kapitel 15 wurde zusätzlich kompiliert. Ein gültiges Typfragment beweist noch keinen korrekten Zustandsautomaten.

### 4.2 Beobachtete Ergebnisse

**18 Referenzprüfungen bestanden:**

```text
PASS separate JAR provider discovered
PASS service symbol
PASS fresh handler factory
PASS fresh builder factory
PASS request type
PASS result type
PASS separate provider actual file size
PASS path converter rejects String
PASS metadata list immutable
PASS duplicate fields rejected
PASS secret lazy before reveal
PASS secret toString masked and lazy
PASS explicit reveal
PASS invalid progress rejected
PASS diagnostic map copied
PASS diagnostic map immutable
PASS invocation identity requires task
PASS context exception carries code
CHECKS=18
```

Diese Zeilen bezeichnen den nachstehend enthaltenen **Dokumentreferenz-Harness**, nicht 18 grüne produktive TC-A-/TC-L-Abnahmen. Er prüft insbesondere keinen echten SQL-/INTERLIS-/GeoTools-Pfad und keinen OpenTelemetry-Exporter.

### 4.3 Dokument-/Paketprüfung

<!-- validation-summary-start -->
Der damalige Dokumentdurchlauf für Revision 4.0 prüfte **19 Markdown-Dateien**, **122 relative Dateiverweise**, **85 geschlossene Codeblöcke**, **5 parsebare JSON-Beispiele** und **1 parsebares YAML-Beispiel**. Es wurden **143 eindeutig definierte Anforderungskennungen** und **148 eindeutig definierte Testfallkennungen** geprüft. Alle referenzierten TC-Kennungen sind vorhanden. Die neun TC-P-Tabellenzeilen wurden exakt mit Version 3.0 verglichen und sind unverändert.

Es gibt keine fehlenden internen Dateiverweise, offenen Codefences, fehlerhaften JSON-/YAML-Beispiele oder doppelten geprüften Definitionen. Der Vergleich ist eine Dokument-/Referenzprüfung, keine Garantie gegen jeden denkbaren semantischen Fehler. Das ZIP wird aus genau diesen Dateien gebaut und anschliessend gegen deren Byteinhalt geprüft.
<!-- validation-summary-end -->

Geprüft wurden ausserdem die erhaltene Publishergrenze, die allgemeine Catch-/Fehlersemantik, die Übereinstimmung von SPI und kompaktem Beispiel sowie die Trennung von Produkttests und späteren optionalen Profilen. Externe Quellen sind in den Fachkapiteln verlinkt; die maschinelle Dateiprüfung wird nicht als Verifikation sämtlicher historischer Internetlinks ausgegeben.

## 5. Nächster Auftrag für einen Coding-Agent

M0 richtet den konventionellen Maven-Build nach BLD-001 bis BLD-008 ein, übernimmt die aktuelle API in die Produkttoolchain und qualifiziert den konkret verwendeten Bibliotheks-/Loggingbestand. M1 implementiert die Klassenrollen und Übergänge aus 15. M2 bringt die Groovy-Probe mit den tatsächlichen Scopes und veröffentlichten Rezepten zum Laufen. M3 liefert den Loggingvertrag gemeinsam mit A, M4 prüft die echten EHI-/Iox-Pfade. Die neue Datei 16 ist vor Adapterarbeit Pflichtlektüre.

Ein Architekturwechsel aufgrund eines wirklich nachgewiesenen Problems ist möglich, muss aber API, Beispiele, Hilfe und Tests gemeinsam aktualisieren. Die Referenz ist keine Aufforderung zu leeren Erfolgsmethoden. Temporäre Testdoubles dürfen nur im klar benannten Referenz-/Testharness stehen und nie als fertige Standardtasks angeboten werden.

## Anhang A — Der ausgeführte Java-Referenzharness

Die folgende Datei liegt bewusst in der Dokumentation, nicht als neue öffentliche Runtimeklasse. Die API-Dateien und ExampleProvider werden aus den markierten Codeblöcken in Kapitel 14 beziehungsweise 04 übernommen; der ServiceLoader-Eintrag steht dort ebenfalls. Gegen API-JAR kompilieren, mit API-JAR und separatem Provider-JAR starten. Auf der hier verwendeten Toolchain wurde `--release 21 -Xlint:all -Werror` eingesetzt. Im Produkt wird zusätzlich Java 25 qualifiziert.

<!-- java-verifier-reference: ReferenceVerifier.java -->
```java
import ch.so.agi.gretl.runtime.api.*;
import java.nio.file.*;
import java.util.*;
import java.time.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class ReferenceVerifier {
    static int checks;
    static void check(boolean value, String name) {
        if (!value) throw new AssertionError(name);
        checks++;
        System.out.println("PASS " + name);
    }
    static void rejects(Runnable action, String name) {
        try { action.run(); }
        catch (IllegalArgumentException | UnsupportedOperationException expected) {
            check(true, name); return;
        }
        throw new AssertionError(name);
    }
    static final class Context implements OperationContext {
        public ExecutionIdentity identity() { return new ExecutionIdentity(UUID.randomUUID(), "reference", Optional.of("t"), Optional.of("i1")); }
        public Object parameter(String name) { throw new ContextAccessException("UNKNOWN_PARAMETER", name); }
        public <T> T parameter(String name, Class<T> type) { return type.cast(parameter(name)); }
        public Map<String,Object> config(String alias) { return Map.of(); }
        public Path file(String p) { return Path.of(p).toAbsolutePath().normalize(); }
        public Path path(String p) { return file(p); }
        public Path workPath(String p) { return file(p); }
        public TaskLogger log() { return new TaskLogger() {
            public boolean isEnabled(LogLevel l) { return true; }
            public void log(LogLevel l,String e,String m,Map<String,String> f,Throwable t) {}
        }; }
        public void report(Diagnostic d) {}
        public SecretValue secret(String a) { return SecretValue.deferred(() -> "fixture"); }
        public String sensitive(String v) { return v; }
        public CancellationToken cancellation() { return new CancellationToken() {
            public boolean isCancellationRequested() { return false; }
            public Optional<CancelReason> reason() { return Optional.empty(); }
            public void throwIfCancellationRequested() {}
        }; }
        public TimeBudget budget() { return new TimeBudget() {
            public Optional<Duration> remaining() { return Optional.empty(); }
            public boolean expired() { return false; }
        }; }
        public ResourceScope resources() { return new ResourceScope() {
            public <T extends AutoCloseable> T manage(T r) { return r; }
        }; }
        public void progress(Progress p) {}
    }
    static <C,V> void assign(InputField<C,V> field,ConfigBuilder<C> builder,Object raw) {
        field.assign().accept(builder,field.convert().apply(raw));
    }
    static <C,R> void exercise(OperationType<C,R> op, Path input) throws Exception {
        check(op.symbol().equals("FileSize"),"service symbol");
        check(op.handlerFactory().get()!=op.handlerFactory().get(),"fresh handler factory");
        ConfigBuilder<C> builder=op.builderFactory().get();
        check(builder!=op.builderFactory().get(),"fresh builder factory");
        InputField<C,?> field=op.inputs().get(0);
        assign(field,builder,input);
        C req=builder.build();
        check(op.requestType().isInstance(req),"request type");
        R res=op.handlerFactory().get().execute(req,new Context());
        check(op.resultType().isInstance(res),"result type");
        Object bytes=op.outputs().get(0).read().apply(res);
        check(bytes.equals(Files.size(input)),"separate provider actual file size");
        rejects(()->assign(field,op.builderFactory().get(),"not a Path"),"path converter rejects String");
        rejects(()->op.inputs().clear(),"metadata list immutable");
        rejects(()->new OperationType<C,R>(op.id(),op.symbol(),op.description(),op.requestType(),op.resultType(),op.builderFactory(),op.handlerFactory(),List.of(field,field),op.outputs(),op.example()),"duplicate fields rejected");
    }
    public static void main(String[] args) throws Exception {
        List<OperationProvider> providers=ServiceLoader.load(OperationProvider.class).stream().map(ServiceLoader.Provider::get).toList();
        check(providers.size()==1,"separate JAR provider discovered");
        Path f=Files.createTempFile("api-reference-", ".txt");
        try {
            Files.writeString(f,"Äpfel & Öl\n");
            exercise(providers.get(0).operations().iterator().next(),f);
        } finally { Files.deleteIfExists(f); }
        AtomicInteger reads=new AtomicInteger();
        SecretValue s=SecretValue.deferred(()->{reads.incrementAndGet();return "sentinel-secret";});
        check(reads.get()==0,"secret lazy before reveal");
        check(s.toString().equals("[REDACTED]") && reads.get()==0,"secret toString masked and lazy");
        check(s.reveal().equals("sentinel-secret") && reads.get()==1,"explicit reveal");
        rejects(()->new Progress("download",5,OptionalLong.of(4),"bytes"),"invalid progress rejected");
        Map<String,String> map=new HashMap<>();map.put("key","old");
        Diagnostic d=new Diagnostic(DiagnosticSeverity.WARN,"fixture","message",map,List.of());
        map.put("key","new");check(d.details().get("key").equals("old"),"diagnostic map copied");
        rejects(()->d.details().put("x","y"),"diagnostic map immutable");
        rejects(()->new ExecutionIdentity(UUID.randomUUID(),"job",Optional.empty(),Optional.of("i1")),"invocation identity requires task");
        ContextAccessException e=new ContextAccessException("UNKNOWN_PARAMETER","name");
        check(e.code().equals("UNKNOWN_PARAMETER"),"context exception carries code");
        System.out.println("CHECKS="+checks);
    }
}
```

Beispiel für die letzten Schritte nach Extraktion und Bau von `api-reference.jar` und `example-extension.jar`:

```sh
javac --release 21 -Xlint:all -Werror   -cp api-reference.jar -d verifier-classes ReferenceVerifier.java
java -cp api-reference.jar:example-extension.jar:verifier-classes ReferenceVerifier
```

Der Klassentrenner `:` gilt hier für Linux/macOS; Windows verwendet `;`. Die separate Betriebssystemmatrix des eigentlichen GRETL-Produkts wird durch diesen Referenztest nicht als erledigt ausgegeben.
