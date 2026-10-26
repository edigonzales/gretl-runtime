# Phase 2 — Korrektur nach unabhängiger Prüfung

Implementierungsagent: Agent A. Unabhängiger Prüfer: `/root/phase2_review`,
ohne Implementierungsbeteiligung. Ausgangskandidat:
`7db4ab8b97d1aa8adf60bc9b137adf24c65e2a02`.
Neuer Kandidat: `fda17afdc2fc4a8a0b9ee94f7d2922af08da4454`.

Der [erste Implementierungsbericht](phase-2-implementation.md) und seine
Nachweise bleiben erhalten. Der [unveränderte erste Prüfbericht](phase-2-review-initial.md) bewertet F1/F2
mit FAIL. Diese Revision korrigiert die unabhängigen
IMAGE-Vertragsbefunde innerhalb derselben Phase. Phase 3 bleibt bis zum
ausdrücklichen PASS des korrigierten Kandidaten gesperrt.

## Korrekturen

Der Image-Adapter behandelte jeden String mit führendem `/` als Hostpfad.
Das war für den FTP-Serverpfad `/home/gretl/<lease>` falsch. Dieselbe Ursache
wies gültige opaque Korrelations-IDs wie `/ci/review/42` in Konfiguration
und Environment zurück. Agent B hat beide Fehler ohne Containerstart
eigenständig reproduziert und die gültige Korrelation separat in echten
Skript- und JAR-Prozessen gegengeprüft.

Der gemeinsame testseitige Request unterscheidet nun Java-`Path`-Werte
von Text. Kanonische lokale Parameter sind als `Path` markiert. Beim
JAR-Start werden sie zu denselben CLI-Werten serialisiert; IMAGE übersetzt
sie anhand der Mounts. Lokale Pfade in Environment und Fachkonfiguration
verwenden denselben Typ. Serverpfade bleiben Strings. Es gibt keine
RemoteDir-Heuristik und keine zweite fachliche Szenariosammlung.
Die Fachkonfiguration erhält weiterhin die expliziten Dienstendpoints.

Runtimeidentität und die GRETL-Identitäts-ENV-Werte bleiben opaque.
Die bekannten Runtime-Pfadfelder workRoot/reportRoot, Termination-Pfad
und Secretdateien werden ausdrücklich als lokale Pfade behandelt.
Relative Runtimepfade beziehen sich weiterhin auf das Konfigurationsverzeichnis.
Nicht gemountete absolute lokale `Path`-Werte scheitern verständlich.

Zudem las der Testhelfer seine UID/GID aus dem Checkout-Verzeichnis.
Ein sauberer Worktree unter `/tmp` erbte eine andere Gruppe und liess
dadurch alle Adaptertests bereits im Konstruktor fehlschlagen. Der Helfer
liest jetzt die tatsächliche Prozessidentität über die qualifizierte
JDK-API `UnixSystem`. Dateigruppen der Prüfumgebung müssen dafür nicht
angepasst werden.

Neue nicht startende Gegenproben prüfen FTP-Serverpfad mit Leerzeichen,
lokalen Output und FTP-Endpoint gemeinsam, Slash-Korrelation aus Config
und ENV, typisierte Fachkonfigurations-/ENV-Pfade sowie nicht gemountete
lokale Parameter-/ENV-Pfade. Die bisherigen Start-, Secret-, Ergebnis-,
Timeout- und Stop-Verträge bleiben erhalten. Kein GRETL-Image wurde gestartet.

Produktbibliotheken, Maven-Abhängigkeiten, Operationen, DSL- und
Resultatverträge wurden in dieser Revision nicht geändert. Die Anpassung
der fachlichen Tests kennzeichnet ausschliesslich lokale Request-Pfade;
ihre fachlichen Assertions bleiben unverändert.

## Tatsächliche Prüfungen und Nachweise

Umgebung unverändert gegenüber dem ersten Phase-2-Bericht.
Nachweiswurzel: `/tmp/gretl-improvement-evidence/phase2-revision-1/`.

- Gezielter Reactor mit `-Dtest=ImageExecutionAdapterTest
  -Dsurefire.failIfNoSpecifiedTests=false -DskipITs verify`: zunächst
  sieben, nach vollständiger Ursachenbehebung neun Adaptertests grün.
  Logs `image-contract.log` und `image-contract-typed-paths.log`.
- Zwischenlauf `./mvnw -B clean verify` vor dem zusätzlichen opaque-ID-Fix:
  457 Tests ohne Failures, Errors oder Skips, `standard-pre-opaque.log`
  und gleichnamiges Nachweisverzeichnis. Dieser Lauf ist kein Abschlussgate
  des danach geänderten Kandidaten.
- Final `./mvnw -B clean verify`: 459 Tests, 0 Failures, 0 Errors, 0 Skips; BUILD SUCCESS, 8:07 min.
- Final `./mvnw -B -Pgeotools-probe,backing-services clean verify`:
  513 Tests, 0 Failures, 0 Errors, 0 Skips; BUILD SUCCESS, 10:30 min.

Standardroot ist der Hauptcheckout; der Profilroot ist
`/tmp/gretl-phase2-A-revision-profiles`, frisch mit Dateigruppe 501:0 angelegt.
Dort wurde derselbe eingefrorene Patch angewendet. Alle 866 versionierten
Dateiinhalte wurden zwischen beiden Prüfroots per SHA-256 verglichen
(`source-tree-equivalence.json`). Der Profilbuild prüft damit zugleich den
ursprünglich problematischen Worktreefall ohne Gruppenanpassung.

Die finalen Rootlogs, XML, Coverage und Prozessartefakte werden vor dem
nächsten clean unter `standard/` und `profiles/` gesichert. Der [erneute 44er-Nachweis](phase-2-revision-1-operation-job-mapping.md) und
die [Prozessidentitäten](phase-2-revision-1-jar-executions.json) beruhen auf
141 echten JAR-Prozessen und vier zusätzlichen SCRIPT-Regressionsprozessen.
Alle gespeicherten JAR-Hashes wurden erneut berechnet.
Die gemessenen Modulzähler stehen getrennt in
[phase-2-revision-1-coverage.json](phase-2-revision-1-coverage.json).

JaCoCo misst weiterhin Maven-Test-JVMs; die separaten Packager-, Launcher-
und Jobprozesse bleiben uninstrumentiert. Testanzahl und Coverage werden
getrennt ausgewiesen. Die früheren Phasennachweise bleiben als Regressionen
erhalten. JAR wird fachlich ausgeführt, IMAGE ausschliesslich ohne echten
Produktcontainerstart am Vertrag geprüft. Unabhängiges Urteil: ausstehend.
