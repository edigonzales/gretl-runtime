# TST4 – reguläre Drei-Modus-CI und kumulative Abnahme

Phasenbasis: `15666544e55af21e16598e94c46294f9bdcae2c9`.
Auftragsbasis: `520b59be12f7966be46d0eaaca046c6ee3dc04f7`.
Voraussetzung: gültiges [TST3-PASS](../reviews/TST3-r1-17dca71d8344.md)
am Produktstand `17dca71d8344095eefc586ed1c4f610c02675f22`.
Arbeitszustand: Umsetzung und gezielte Entwicklungstests abgeschlossen;
feste Kandidatengates und unabhängige kumulative 38-Kriterien-Abnahme folgen.

## CI und Veröffentlichungsgrenzen

Der wiederverwendbare `qualification.yml` enthält den Dockerfreien
SCRIPT-Standard und den vollständigen sequenziellen ALL/full-Weg.
`publish-snapshots.yml` ruft ihn regulär bei Push, Pull Request und manuell
auf. Snapshot-Publikation hängt von dessen Erfolg ab und bleibt auf main
beschränkt; Pull Requests veröffentlichen nie. Ein Qualifikationsjob
übernimmt keine Deploy-Skipflags. Die vorhandenen Deployflags gelten erst
nach beiden erfolgreichen Pflichtjobs.

Der Release-Candidate-Workflow verwendet dieselbe Pflichtqualifikation,
lädt deren qualifizierte ZIP-Datei und erhält die zusätzliche Checksum-/
Validator-Paketstartprobe. Er baut die Distribution nach den Tests nicht
neu. Der manuelle IMAGE-Workflow ist jetzt eine zusätzliche Diagnose mit
local/full-Auswahl; er baut aus der aktuellen geprüften Distribution.
IMAGE/full ist damit kein manueller Optionalweg mehr.

Jeder Modus archiviert seinen eigenen kuratierten Bestand, Standard
separat. Das Vollzeitlimit von 90 Minuten berücksichtigt die gemessenen
lokalen etwa 8/12/12 Minuten sowie Sicherung/Audits und Runnerreserve;
keine Paket-/Dienstfälle werden dafür entfernt. Nach späteren clean-Läufen
prüft ALL alle drei Archive erneut, bevor es den Gesamtabschluss schreibt.

Auch `publish-snapshots.sh` führt Standard und ALL/full zwingend vor seinem
Deploy aus. Vier Kontrolltests benutzen Fake-Gates, Fake-Maven und
synthetische Credentials: fehlende Credentials starten nichts, Standard-
oder Vollfehler verhindern Deploy, beide erfolgreichen Gates erlauben erst
danach den Fake-Deploy und erhalten dessen tatsächlichen Exit.
Kein tatsächlicher Deploy, Push, Merge, Release oder Image-Push ausgeführt.

Workflow-YAML und Jobabhängigkeiten wurden lokal mit vorhandenem Ruby/YAML
geprüft. Das ist kein behaupteter Remote-CI-Lauf. Ein Maintainer kann nach
dem ersten autorisierten CI-Lauf die beiden Jobs `standard` und `full` des
aufrufenden `qualification` als Required Checks konfigurieren. Eine
Branch-Protection-Änderung wurde nicht vorgenommen und verhindert keine
lokale technische Gesamtabnahme. Grundlage der Verdrahtung:
[GitHub reusable workflows](https://docs.github.com/en/actions/how-tos/reuse-automations/reuse-workflows)
und [Jobabhängigkeiten](https://docs.github.com/en/actions/reference/workflows-and-actions/workflow-syntax#jobsjob_idneeds).

## Unabhängiger Sollbestand und Fehlgrün-Proben

E12 reproduzierte eine tatsächliche Lücke des früheren Auditors: Aus einer
zunächst gültigen echten IMAGE-Kopie wurde ein negativer Projektgraphstart
entfernt und gleichzeitig dessen Szenariodeklaration reduziert. Der alte
Auditor gab Exit 0. Die bereits versionierten JUnit-/Szenariodeklarationen
tragen jetzt feste `expectedStarts` je Methode/Variante; sie stammen aus
dem deklarierten Bestand, nicht aus Erfolgsergebnissen. Dieselbe isolierte
Reduktion wird mit `Reduced scenario contract` abgewiesen. Prozessfamilien
können zudem nicht still auf null Starts reduziert werden; entfernte
Methodenklassifikationen fallen gegen die vorhandenen Java-Quellen auf.
Es gibt keinen Ist-zu-Soll-Generator.

Die Negativsuite deckt E01–E12 ab. Zusätzlich zu TST3 prüft sie fehlende
JUnit-Kandidatenbindung, Failure und Error, Maven- und Auditfehler sowie
echtes SIGTERM am Matrixstarter. Bei der Signalprobe läuft ein tatsächlich
gelabelter eigener Container im eigenen Netz. Der Gateexit bleibt 143,
Maven wird reap-ed, eigene Ressourcen verschwinden, ein anders markierter
Kontrollcontainer bleibt lebendig. Nur anschliessend wird auch dieser
selbst erzeugte Kontrollcontainer entfernt. Keine globale Dockerbereinigung.

Entwicklungsnachweise: 21 IMAGE/full-Negativtests und 17 SCRIPT/full-
Negativtests bestanden, jeweils zuerst gültige unveränderte Archivkopie.
Vier Publikationskontrollen, 73 Dokumentationswerkzeugtests, 21 Node-
Reviewstartertests, Linkprüfung und YAML-/Workflowgraphprüfung grün.
Rohbelege privat unter `/Users/stefan/sources/gretl-script-first-evidence/`,
u. a. `tst4-e12-red`, `tst4-negatives-dev.log`,
`tst4-script-negatives-dev.log` und `tst4-publication-control-final.log`.

## Gewöhnlicher Mavenweg und aktive Anleitungen

Ein direkter `./mvnw -B clean verify` bekommt eine gemeinsame UTC-Build-Run-ID
mit Millisekunden aus der
[Maven-Buildzeit](https://maven.apache.org/guides/introduction/introduction-to-the-pom.html).
Surefire/Failsafe erhalten sie ausdrücklich. Der Audit bindet alle
JUnit-Dateien und Prozesse an denselben nichtleeren Run. Der Matrixweg
setzt weiterhin UUID und expliziten Kandidaten-SHA; fehlt diese Bindung bei
angefordertem Matrixrun, scheitert die Prüfung. Der gewöhnliche Audit ohne
expliziten Matrixrun bindet den Quellen-SHA über die tatsächlich geprüfte
Distribution und die Ausführungsbytes. Fremde oder gemischte JUnit-Runs
bleiben verboten.

Die gezielte Defaultprobe führte zwei Komponentenfälle und einen wirklichen
externen Projektprozess aus; JUnit und Prozess tragen dieselbe tatsächlich
expandierte Maven-Run-ID ohne Kandidatenproperty. Eine erste falsch
benannte Methodenauswahl führte null ITs aus und wurde ausdrücklich nicht
als Nachweis gewertet; die korrigierte Auswahl führte genau den vorhandenen
`externalProjectNeedsNoPackageDescriptor` aus, Exit 0.

Root-README, Build, Teststrategie, Jobtest-Verträge und Publishanleitung
beschreiben jetzt SCRIPT als Default, reale IMAGE-Ausführung und separate
JAR-Verträge. Die OpenShift-Vorlage gibt dem unveränderten 30-Sekunden-
Runtimeabschlussbudget 45 Sekunden Pod-Grace. Unbekannte UID/Gruppe 0,
read-only Root/Quelle, frische Schreibvolumes und Native-Arbeit bleiben
explizit. Eine echte Clusteradmission/PVC oder AMD64-native Imageprüfung
wird nicht behauptet. Lokal: macOS/ARM64, Docker Linux/ARM64, PostGIS
teilweise AMD64-emuliert; Linux-x86-64-CI bleibt eingerichtet.

Nächste Schritte: sauberer Produktkandidat, frische Dockerfreie Standard-
prüfung und ALL/full, erhaltene zusätzliche Distributions-/Paketstartprobe,
Dokumentationsbeispiele sowie unabhängiger OpenCode-Gesamtreview aller
38 Kriterien. Keine frühere Phasenabnahme wird auf einen neuen SHA umetikettiert.
