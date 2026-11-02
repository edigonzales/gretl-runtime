# TST3 – externe SCRIPT-Jobs im generischen Runtime-Image

Phasenbasis: `ad8d21dc55a0b71f85ff8fde47845678ee1f4d3f`.
Auftragsbasis: `520b59be12f7966be46d0eaaca046c6ee3dc04f7`.
Voraussetzung: gültiges [TST2-PASS](../reviews/TST2-r1-b1440bfd678d.md)
am Produktstand `b1440bfd678d4ea83d70e51778b09997c441aace`.
Arbeitszustand: Entwicklung; saubere Kandidatengates und unabhängiger
TST3-Review noch fällig. TST4 ist nicht begonnen.

## Umsetzung und erhaltene Orakel

Der [Dockerfile](../../../../gretl-dist/src/main/docker/Dockerfile) enthält
nur die reguläre Distribution. Java 25 auf Ubuntu 24.04 ist per tatsächlich
aufgelöstem OCI-Indexdigest gepinnt; der normale Entrypoint ist der
Produktlauncher. Der Builder vergleicht alle tatsächlichen Runtimebytes
aus dem Image mit dem geprüften Distributionsinventar. Labels allein
reichen nicht. Quellenrevision, Manifest, Image-ID, Plattform und
Basisreferenz werden separat aufgezeichnet.

`job-image` baut nach der Distribution und wählt wirkliche IMAGE-Prozesse.
Der Standard bleibt SCRIPT ohne Docker. Der bestehende Adapter verwendet
den unveränderten Imageentrypoint mit externem `/job`, read-only Root,
non-root UID/GID und expliziten beschreibbaren Volumes. Secrets und
Businessstrings behalten ihre bisherige Bedeutung. Nur deklarierte Pfade
und Dienstendpoints werden übersetzt. Der Dockerclient behält seinen
Hostkontext; Container-HOME und Temp sind ausdrücklich gesetzt.

Die gemeinsamen Methoden und fachlichen Assertions bleiben dieselben.
PostgreSQL/PostGIS, FTP und S3 verwenden ein eigenes Testnetz; HTTP wird
über einen ausdrücklichen Hostendpoint erreicht. Quellen-/Projektverträge
laufen zusätzlich im Image, soweit sie keine ausdrücklich hostgebundene
ZIP-/Startfehler- oder Paketierungsprobe darstellen. Erwartete Quellstellen
werden auf `/job` abgebildet; Ergebnis-, Ursachen-, Task-, Retention- und
Cleanupassertions bleiben erhalten.

Die neuen `ImageRuntimeIT`-Fälle prüfen echte Container: nach Imagebau
angelegter Unicode-Job, zweiter Job samt kaputtem unbenutztem Descriptor,
geänderter erster Job mit derselben Image-ID; native SQLite-/DuckDB-Arbeit;
beliebige OpenShift-UID mit Gruppe 0; tatsächlicher erzwungener Abbruch
mit Exit 137; vor Start veränderte Quelle wird als Infrastrukturfehler
abgewiesen. Erzwungener Abbruch zählt nicht als kontrolliert beendet.

Container-ID, tatsächliche Image-ID, Benutzer, read-only Root,
Prozessabschluss, OOM-Zustand und erfolgte Entfernung ergänzen die
bestehenden JUnit-/Szenario-/Reportnachweise. Der Audit verlangt diese
realen Fakten; ein RecordingEngine-Protokoll erfüllt IMAGE nicht.
Der Matrixstarter unterstützt IMAGE/local und IMAGE/full sowie die
sequentielle Gesamtausführung ALL. Er erhält Maven-/Auditexits und
bereinigt nur seine eigenen markierten Container und sein eigenes Netz.

## OpenShift und Entwicklungsgegenfälle

Die [OpenShift-Anleitung](../../../../gretl-dist/src/main/docker/README.md)
und [Jobvorlage](../../../../gretl-dist/src/main/docker/openshift-job.yaml)
verwenden keinen fest zugewiesenen Clusterbenutzer und keine privilegierte
SCC. Runtime/Jobquelle bleiben read-only; eigene Schreibbereiche verwenden
Volumes. Eine unbekannte UID `1001234567:0` führt native Arbeit mit
`no-new-privileges` und ohne Capabilities tatsächlich aus. Kein Schreiben
nach `/etc/passwd`, keine pauschalen 777-Rechte.

Die Gegenprobe fand reale Probleme: private Hostverzeichnismodi wurden
beim Imagecopy auf Runtimeverzeichnisse übertragen; frische Volumes
überdeckten vorbereitete HOME-/Temp-Unterverzeichnisse; ein `noexec`-Temp
verhinderte JNI. Die Runtime erhält deshalb öffentliche Leserechte,
HOME liegt direkt auf `/work`, Native-Temp auf dem ausführbaren `/tmp`.
Die Probe sichert flüchtige tmpfs-Ausgaben vor Containerende über einen
begrenzten Archivstream. Anschliessend werden feste Datenbankwerte und
Kernel-UID/GID-/Capabilityinformationen unabhängig gelesen.

Gezielte Prüfungen von Source-/Projekt-, Safety-, Cancellation- und
Adaptergrenzen fanden zudem synchrone Signalzustellung und irrtümliche
Workspaceauswahl durch neue HOME-Verzeichnisse. Signalzustellung ist
jetzt nicht blockierend; Assertions lesen das tatsächliche Runverzeichnis
und unterscheiden Hilfsverzeichnisse von Produktworkspaces.

Plattform: macOS/ARM64, Docker Linux/ARM64, PostGIS teils AMD64-emuliert.
Ein realer OpenShift-Cluster, SCC-Admission oder PVC wurde nicht ausgeführt.
Die Dockerprüfung belegt die genannten Laufzeitbedingungen, keine
pauschale Freigabe weiterer Plattformen.

Nächster Schritt: vollständige IMAGE-Entwicklungsprüfung und Auditproben,
sauberer Kandidat, frische Phasengates, unabhängiger OpenCode-Review.

## Erster vollständiger Entwicklungslauf und gezielte Korrekturen

Befehl mit JDK 25, in einem selbst angelegten Docker-Testnetz:

```sh
./mvnw -B -Pgeotools-probe,backing-services,job-image clean verify \
  -Dgretl.test.imageNetwork=gretl-tst3-development \
  -Dgretl.test.imageUser=501:20 -Dgretl.test.imageExtraHost= \
  -Dgretl.test.httpImageHost=host.docker.internal \
  -Dgretl.test.runId=tst3-development-full
```

757 Tests, genau ein fachlicher Fehlschlag, keine Errors/Skips; Maven
Exit 1. Alle übrigen Fälle einschliesslich echter PostgreSQL-/FTP-/S3-
Prozesse erfolgreich. Der Audit lehnt die gesicherte fehlerhafte Suite
korrekt wegen des fehlgeschlagenen JUnit-Falls ab. Ursache: Die bestehende
DuckDB-SQL-Fixture verwendete `LOAD spatial` und vertraute auf den durch
vorherige Hosttests gefüllten Extensioncache. Ein frisches Container-HOME
hat diesen Cache nicht. Die gemeinsame SQL-Datei führt jetzt ausdrücklich
`INSTALL spatial` vor `LOAD spatial` aus. Fachliche Erwartungen bleiben
unverändert; alle drei Modi verwenden dieselben korrigierten Quellen.

Die verschärfte echte Timeoutgegenprobe reproduzierte anschliessend einen
separaten Harnessfehler nach 38 Sekunden: Der Client war beendet, der
blockierende Container lief noch. Der Abschluss verweigerte deshalb
korrekt einen erfundenen Containererfolg. Der Timeoutpfad beendet jetzt
zuerst den tatsächlichen Container mit begrenzter 35-Sekunden-Grace und
wartet danach begrenzt auf den Dockerclient. Explizite kooperative
Signalzustellung bleibt nicht blockierend. Die Gegenprobe verwendet jetzt
den wirklichen Runner-Timeout statt eines vorher separat gestoppten
Containers.

Gezieltes Korrekturgate ohne clean: 14 Adaptertests sowie sechs reale
IMAGE-Tests (blockierender Timeout, vier kooperative Cancellationfälle und
DuckDB mit frischem HOME), Exit 0, keine Failures/Errors/Skips. Der echte
Timeout liefert Exit 137, `forciblyKilled=true`, keinen OOM und einen
nachweislich entfernten Container. Zusätzliche reale Negativproben:
korrekte Labels bei manipuliertem Launcher werden anhand tatsächlicher
Imagebytes abgewiesen; fehlendes Netz und fehlender read-only Quellmount
enden mit Docker-Exit 125 ohne Produktlauf.

Rohbelege liegen privat ausserhalb von `target` unter
`/Users/stefan/sources/gretl-script-first-evidence/`, insbesondere
`tst3-image-full-development-proof`, `tst3-timeout-red-proof`,
`tst3-timeout-spatial-green.log` und `tst3-development-real-negatives-2`.
Diese Entwicklungsstände sind keine unveränderliche Kandidatenabnahme.
73 Dokumentationswerkzeugtests, 21 Reviewstartertests, Linkprüfung und
Quarto-Render erfolgreich. OpenCode 2.2.5 Bereitschaftsprobe abgeschlossen;
direkte offizielle DeepSeek-Flash-1017-Zuordnung am 01.11.2026 erneut geprüft.

## Nanosekundengenaue Auditkorrektur

Erster sauberer Kandidat: `dc9b58f5114a91eee1f2dd1ea4437d47f9e9e5f4`.
Frische Standardprüfung 696 Tests; SCRIPT/full und JAR/full je 752 Tests,
alle Mavenläufe Exit 0, keine Failures/Errors/Skips. Beide Vollmodi besitzen
44 positive Standardoperationen und 174 bytegleiche COMMON-Inputs.
Die Archive bestehen nach weiterem clean. Auch IMAGE/full besteht mit
757 Tests ohne Failures/Errors/Skips; anschliessend scheitert das Gate
jedoch korrekt, weil der Audit eine gültige Linux-Zeit mit neun
Nachkommastellen auf dem vorhandenen Python 3.9 nicht parsen kann.
Dieser Kandidat wird deshalb nicht zum unabhängigen Review eingereicht.

Der Audit wandelt jetzt RFC-3339-Zeiten exakt in ganzzahlige Nanosekunden
um. Die Reihenfolge von Prozessstart, Reportstart, Reportende und
Prozessende bleibt strikt. Kein Abschneiden auf Mikrosekunden und keine
Toleranzerweiterung. Die neue Gegenprobe verschiebt ein tatsächliches
Reportende um genau eine Nanosekunde hinter das zugehörige Prozessende
und aktualisiert seinen Bytehash; der Audit lehnt genau die Lebensdauer
ab. So kann eine blosse Präzisionsreduktion die Probe nicht erfüllen.

Der unveränderte gesicherte IMAGE-Vollnachweis besteht mit dem korrigierten
Auditor: 757 Tests, 220 erwartete Szenarien, 44 Standardoperationen.
Alle 16 IMAGE/full-Negativproben bestehen, darunter falsche tatsächliche
Imagebytes bei korrekter Provenienz, verändertes Inputinventar, fehlende
reale Containerfakten und die Nanosekunden-Grenzverletzung. Der frühere
äussere Gateexit bleibt 1; es wird keine erfolgreiche Kandidatenabnahme
rückwirkend erzeugt. Neuer Kandidat und frische Phasengates folgen.
Die Negativauswahl umfasst nun SOURCE/JAR local zwölf, full dreizehn sowie
IMAGE local fünfzehn und full sechzehn Prüfungen.


## Feste eigene Gates und unabhängiger Abschluss

Korrigierter Kandidat `17dca71d8344095eefc586ed1c4f610c02675f22`:
Standard 696, SCRIPT/full und JAR/full je 752, IMAGE/full 757 und
IMAGE/local 701 Tests. Eigene und unabhängige Läufe jeweils vollständig
mit Exit 0 und ohne Failures/Errors/Skips. Alle 44 Standardoperationen
pro Vollmodus, 174 identische COMMON-Inputs über drei Modi. Alle fälligen
Negativproben und Dokumentationsprüfungen bestanden. Die Archivkopien
wurden nach clean erneut geprüft; tatsächliche Runtime-/JAR-/Imagebytes,
Reporthashes und XML-Zahlen wurden nachgelesen.

[Unabhängiger Bericht](../reviews/TST3-r1-17dca71d8344.md) und
[Urteil](../reviews/TST3-r1-17dca71d8344.verdict.json): gültiges PASS.
Bericht SHA-256:
`1f1af0d29fa4f6d19ceb54f19ef5decd771df0679ae3d04fe88e0c2338d7cb27`.
Die eigene externe Quellwechselprobe liefert im unveränderten Image
`quelle-ü\n|CP-A-v1-λ` und nach reinem Scriptedit
`quelle-ü\n|CP-A-v2-ω`. Zusätzlich unbekannte UID `1007654321:0` mit
nativer SQLite-/DuckDB-Arbeit, tatsächlichem kooperativem SIGTERM sowie
isolierten falschen Imagebelegen geprüft. Alle 36 eigenen Assertions
bestanden; Gegenproben und Rohbelege wurden vom Orchestrator nachgeprüft.
OpenCode-Prozess und Wrapper beendet (0), alle zehn Kriterien erfüllt,
keine offenen Muss-Findings und keine relevanten Reviewkinder übrig.
TST3 ist abgeschlossen; TST4 folgt an der Basis dieses Nachweiscommits.
