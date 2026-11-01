# TST2 – gemeinsame Fachfälle und unabhängiger Szenarionachweis

Phasenbasis: `6bf473df13f1c33677e1a4a4f33fd49776aa6546`.
Auftragsbasis: `520b59be12f7966be46d0eaaca046c6ee3dc04f7`.
Voraussetzung: gültiges [TST1-PASS](../reviews/TST1-r1-52ca5bee2e52.md)
am Produktstand `52ca5bee2e52f7c07106d305e071add1c3c3724a`.
Arbeitszustand: Entwicklung; Kandidatengates und unabhängiger TST2-Review
noch fällig. TST3 ist nicht begonnen.

## Methoden und Varianten

Der fest versionierte [Szenariokatalog](../../../../gretl-integration-tests/src/test/resources/qualification/scenarios.json)
benennt jede JUnit-Methode samt Parameterindex sowie jeden tatsächlichen
Start innerhalb der Methoden. Die Variante referenziert die unabhängigen
Java-Assertions, die unverändert für den gewählten COMMON-Modus gelten.
Der Katalog wird nicht aus erfolgreichen Laufnachweisen erzeugt.

| Betroffene Methoden/Varianten | Bisheriger Startweg | Familie und erhaltenes Orakel |
| --- | --- | --- |
| `IoLocalJobIT`, `ValidatorLocalJobIT`, `FormatLocalJobIT`, `InterlisLocalJobIT`, `DuckDbLocalJobIT`, `FunctionLocalJobIT`, jeweils sämtliche Methoden und Parameter-/Schleifenvarianten | Gemeinsamer Runner, seit TST1 SCRIPT-Default | COMMON: dieselben Quellen, Dateibytes, Requestdetails, semantischen Leser und präzisen positiven/negativen Fachdiagnosen in SCRIPT und JAR. |
| `DatabaseArtifactIT`, `FormatArtifactPostgresIT`, `InterlisArtifactIT`, `ArtifactServicesIT`, `S3OriginalArtifactIT`, sämtliche Methoden und Schleifenvarianten | Gemeinsamer Runner mit echten Dienstfixtures | COMMON: unabhängige JDBC-/PostGIS-, Datei-, FTP- und S3-Prüfungen; Schema-/Dataset-/Batch-/Scalar-/List-/Login-/Missing-Bucket-Varianten erhalten. |
| `CancellationLocalJobIT`, beide Signalvarianten und Fehler-/Timeoutmethoden | Explizite SCRIPT-/JAR-Booleanvarianten | COMMON: gewählter Modus; ursprüngliche Checkpoints, Cleanup, Nachfolger-, Signal-, Fehlerprioritäts- und Reportassertions unverändert. |
| `RuntimeSafetyLocalJobIT`, alle Ziel-/Ursachenvarianten | Explizite SCRIPT-/JAR-Booleanvarianten | COMMON: gewählter Modus; identische Rechte-, Ursachen-, Budget-, Setup- und Fehlstartprüfungen. |
| `PlaintextSecretLocalJobIT` | Explizite SCRIPT-/JAR-Booleanvarianten | COMMON: gleiches synthetisches Secret, rohe Produktlogs vor Harnessredaktion, unabhängige SQLite-/Digestprüfungen. |
| `ReferencePathLocalJobIT`, beide Methoden | Gemeinsamer Runner | COMMON: unveränderte externe Modell-/Exportpfadprüfungen. |
| `ProjectArtifactLocalJobIT#prj209InnerHandlerFailures...` und `#prj209TargetSelectionAndNoData...`, jeweils beide Schleifenvarianten | Gemischte explizite Prozesse bzw. gemeinsamer Runner | COMMON: ursprüngliche innere Exceptionquellen, Cause, Task- und Cleanupzustände; dieselbe Fixture. |
| Übrige `ProjectArtifactLocalJobIT`-Methoden, alle Manipulationsvarianten | Explizite JAR- und technische Quellenvergleiche | PACKAGE_CONTRACT: Schema-3-Inventar, Klassen-/Ressourcenauswahl, Toplevelfreiheit, isolierter Start, entfernte Quelltexte, Version/Hilfe/Plan und beschädigte Metadaten/Ressourcen erhalten. Der explizite Vergleichsstart bleibt SCRIPT. Tatsächlich gestartete manipulierte JAR-Dateien werden unter ihrem eigenen Namen gehasht und erhalten. |
| `ProjectScriptLocalJobIT`, sämtliche Methoden und sieben Starts | Eigener ProcessBuilder und eigener Metadatenwriter | SCRIPT_CONTRACT: gemeinsamer Runner/Writer; ursprüngliche Graph-, Teilziel-, Single-File-, Originalquellen-/Zeilen- und Ereignisassertions erhalten. |
| `LibraryScopeLocalJobIT`, beide Starts | Eigene technische Extensiondistribution und JAR | PACKAGE_CONTRACT mit explizitem SCRIPT-Vergleich: unveränderte SLF4J-/JUL-Scope-Orakel. |
| `SelectedPackageLocalJobIT`, `SelectedPackageArtifactIT`, alle Varianten | Explizite isolierte JAR-Paketqualifikation | PACKAGE_CONTRACT: unverändert; Paketierungsfehler bleiben JUnit-geprüfte Authoringproben ohne erfundenen Jobprozess. |
| `ScriptProcessLocalJobIT`, sämtliche Starts | Neuer TST1-Quellenvertrag | SCRIPT_CONTRACT; bewusst ausgewählte Cache-/Descriptor-Gegenproben PACKAGE_CONTRACT. Keine COMMON-Modusauffüllung durch diese Zusatzfälle. |
| Ältere direkte Komponenten-/Diensttests und `ValidatorClasspathLocalJobIT` | In-process bzw. gezielte Classpathprobe | COMPONENT; bleiben ausgeführt und JUnit-geprüft, zählen nicht als zusätzliche Produktprozess-Operationsabdeckung. `PackagedSqlJobPostgresIT` bleibt sein älterer expliziter Paketvertrag. |
| Neue `ProjectProcessLocalJobIT` | Neu | COMMON: Projekt-DAG, gleiche lokale SQL-Namen, echte Ergebnisabhängigkeit, Teilziel, Parameter-Namensräume, Definitionsfehler sowie 3×3 Outcome-/Retentionmatrix. Expliziter SCRIPT-Vertrag zusätzlich ohne Descriptor. |
| Neue `JobProcessLifecycleLocalJobIT` | Neu | COMMON: vollständige grosse Streams, erforderlicher Reportschreibfehler, relativer Pfadparameter gegen fremdes CWD und relative Config-/Secretpfade. |
| Neue reale Host-Abbruchprobe in `ArtifactJobRunnerTest` | Bisher nur Fake-Process | COMPONENT: lebender Kindprozess mit blockierendem Shutdownhook, tatsächlicher begrenzter Force-kill und Reaping; kein Produkt-CANCELLED behauptet. |

## Evidenz und Ausführung

Die JUnit-Erweiterung ordnet jeden Start der konkreten ausgeführten Methode
und Parameterinstanz zu. Ein stabiler Startindex unterscheidet mehrere
Starts derselben Methode. Vorbereitung/Fehlstart und tatsächliche Prozesse
bleiben getrennt. Der abgeschlossene Report wird mit Run-/Command-ID und
seinen tatsächlichen Bytes verbunden.

Der Audit verlangt den festen Sollbestand, erfolgreiche zugehörige
JUnit-Fälle, eindeutige Suite-Run-ID, passenden Startweg, Input-/Runtime-/JAR-
Identität, passende Report-/Eventidentität und Reportzeit innerhalb der
Prozesslebensdauer. Frühe Fehler und Controls brauchen ausdrücklich keinen
Runreport. Alle 44 Standardoperationen werden je vollständigem Hauptmodus
gefordert; technische Zusatzfälle anderer Modi können diese Menge nicht füllen.

Der kleine Matrixstarter verwendet den bestehenden Maven-Lifecycle,
propagiert Maven-/Audit-/Ausgabefehler und sichert vor `clean` eine private
Proofmenge mit XML, Reports, redigierten Logs, Inventaren, tatsächlichen
JAR-/Distributionsbytes und fachlichen Ausgaben. Runtimekonfiguration,
Secretdateien, Inputquellbäume und Workspaces werden nicht pauschal archiviert.
Der Auditor kann diesen erhaltenen Bestand ohne erneuten Jobstart prüfen.

Entwicklung: gezielte SCRIPT-/JAR-Prüfungen der geänderten Projekt-, Abbruch-,
Secret- und Safety-Bereiche erfolgreich. Der erste lokale Entwicklungslauf
fand eine Fake-Reportannahme im RecordingEngine-Test; der Writer erfindet für
solche Komponentenbelege keine Produktidentität. Der Auditor verlangt sie
weiterhin für einen echten qualifizierten Run. Anschliessende lokale
Entwicklungsregression erfolgreich. Ein Audit vor Ausführung neu ergänzter
Tests wies genau deren zwei fehlende JUnit-Varianten zurück.

Die zusätzlichen Grenzprüfungen sind mit Exit 0 abgeschlossen: sieben
Runner-Tests einschliesslich echtem Force-kill und vier gemeinsame
Prozessgrenzfälle. Der lokale Soll-/Ist-Abgleich umfasst genau 135 Starts,
ohne fehlende oder zusätzliche Varianten. Die mehrfach gezielt neu gebaute
Distribution besitzt jeweils neue Manifestbytes; ein Audit alter Mischbelege
scheitert deshalb korrekt. Diese Entwicklungsbelege werden nicht als
unveränderliche Kandidatenqualifikation ausgegeben.

Nächster Schritt: sauberer Kandidatencommit, frische SCRIPT/local-,
SCRIPT/full- und JAR/full-Gates, isolierte Audit-Negativkopien sowie
unabhängiger Review mit eigenen Gegenproben.


## Erster Kandidat und Archivkorrektur

Am Kandidaten `e61d85237d2fdb6b84030c3701d2003748fc7a3c` bestehen
die frische Dockerfreie Standardprüfung und der Live-Audit: 693 Tests,
0 Failures/Errors/Skips, 135 erwartete Starts und 126 konkrete
JUnit-Varianten im Integrationstestmodul. Fremde Java-/Classpath-Overrides
wurden zusätzlich gesetzt; die Produktprozesse bereinigen sie.

Die nachfolgende positive Archivgegenprobe fand einen Sicherungsfehler:
Die private Extensiondistribution liegt neben ihrem Prozessverzeichnis
und fehlte im erhaltenen Bestand. Der Archivierer bewahrt nun auch diese
tatsächlich referenzierten Runtimebytes. Der Audit liest sein unabhängiges
Operationssoll aus dem versionierten Checkout statt aus einem Archiv,
das bewusst keine komplette Repositorykopie enthält. Nur aktive Profile
werden archiviert; alte GeoTools-Ausgaben gehören nicht zu LOCAL.

Die korrigierte unveränderte Archivkopie besteht; die lokalen
Auditmutationen scheitern wie erwartet. Eine positive Archivprüfung ist
jetzt zwingende Voraussetzung vor jeder Mutationssuite. E07 wird nur im
vollen Scope ausgewählt: Die manipulierte Vereinigung enthält dann alle
44 Standardoperationen, der angeforderte Modus selbst jedoch keine.
Damit prüft die Gegenprobe tatsächlich eine unzulässige Modusvereinigung.
Der erste Kandidat wird nicht zum Review eingereicht; der korrigierte
Kandidat bekommt frische Phasengates. Phasenbasis unverändert.


## Fester Kandidat und unabhängige Abnahme

Geprüfter Produktkandidat: `b1440bfd678d4ea83d70e51778b09997c441aace`.
Eigene Standardprüfung 693 Tests, vollständige SCRIPT-/JAR-Prüfungen je
749 Tests, überall Exit 0 und 0 Failures/Errors/Skips. Beide Vollmodi decken
alle 44 Standardoperationen separat positiv ab; 174 gemeinsame Inputs sind
bytegleich. Alle lokalen und vollständigen Auditproben, Reviewstarter- und
Dokumentationsprüfungen bestanden. Die Archive wurden zusätzlich nach den
nächsten clean-Läufen erfolgreich erneut auditiert.

[Unabhängiger Review](../reviews/TST2-r1-b1440bfd678d.md) und
[strukturiertes Urteil](../reviews/TST2-r1-b1440bfd678d.verdict.json): PASS.
Standard und zusätzliche JAR/local-Prüfung je 693 Tests, beide Vollmodi je
749 Tests, keine Failures/Errors/Skips. Die eigene neue Projektgegenprobe
liefert feste Erwartungsbytes in SCRIPT/JAR; Entfernen eines erwarteten
Szenariobelegs aus einer zuvor positiven Auditkopie scheitert korrekt.

Agent A hat Prozessabschluss, strukturierte Kriterien/Checks, saubere
Revisionen, tatsächliche XML-Zahlen, alle erhaltenen Distributionsmanifeste,
tatsächlichen JAR- und Reportbytes sowie Gegenprobenausgaben nachgeprüft.
OpenCode und äusserer Starter sind beendet, Exit 0; keine relevanten
unbeendeten Review-Jobprozesse. Keine offenen Muss-Findings.

Präzisierung der früheren Kurzangabe „Starts“: LOCAL umfasst 135 erwartete
Szenarien, davon 133 tatsächliche CLI-Prozessabschlüsse und zwei
Infrastruktur-Negative; FULL entsprechend 213/211/2. Kein Infrastrukturfehler
wird als positiver Fachoperationsbeleg gezählt. TST3 ist damit freigegeben.
