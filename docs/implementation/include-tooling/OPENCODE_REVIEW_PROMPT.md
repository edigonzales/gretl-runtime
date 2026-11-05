# Unabhängiger Review: GRETL Include-aware Tooling

**Fassung 1.0 · 5. November 2026**  
**Auftrag:** `include-tooling`  
**Reviewer:** OpenCode 2.x mit DeepSeek Flash 1017

> Du bist der zweite Agent, nicht Codex. Prüfe den bereits committeten Kandidaten gegen [EXECUTION_PLAN.md](EXECUTION_PLAN.md). Du beurteilst **Code und Zielerreichung**, führst selbst notwendige Tests aus und entwickelst eine eigene neue Gegenprobe. Keine Produktfixes, Scopeänderungen, Testlockerungen oder selbst vergebene Implementiererabnahmen.

## 1. Prüfidentität und Unabhängigkeit

Der externe Handoff nennt `assignment_id=include-tooling`, `spec_version=1.0`, den Digest der drei aktiven Auftragsdateien, Phase, Auftragsbasis, feste Phasebase, vollen Kandidaten-SHA, Worktree, erlaubte Test-/Probe-/Evidenzorte und erforderliche Kriterien. Der Request ist kein vorgegebenes Ergebnis. Ein eigener Commit-SHA ist absichtlich erst nach Commit extern eingetragen.

Lies `pwd`, HEAD, Gitstatus und tatsächlichen Diff selbst. Prüfe auch erhaltene Runtime-Includes und frühere Sicherheitsgrenzen. Recherchepin `ae0a2630156280e0e7f6bebf2354884f555d8d7e` ist keine Reset-Anweisung. Ein archivierter Runtime-/Tooling-PASS belegt die Ausgangsbasis, nicht diesen Folgeauftrag.

Frischer Reviewkontext und eigener Checkout sind beide notwendig. Tatsächliche OpenCode2-/Modellidentität durch Aufruf-/Sessionmetadaten prüfen; gewünschte Modellversion DeepSeek-V4.1-Flash-1017. Selbstauskunft des LLM reicht nicht. Vorhandene Anmeldung nutzen, keine Authdateien/Secrets auslesen oder exportieren. Kein stiller Provider-/Modellwechsel.

Originale getrackte Produktions-, Test-, Konfigurations- und Erwartungsdateien im Reviewcheckout nicht ändern. Eigene neue Tests in einem externen Probeverzeichnis oder einer separaten Probekopie **desselben SHA** ausführen; Originalprodukt-/Originaltestbytes müssen identisch bleiben. Builds dürfen reguläre Ausgaben erzeugen. Keine Änderungen an anderen Worktrees, globalen Profilen oder produktiven Daten. Kein Push, Merge, Commit oder Release durch den Reviewer.

## 2. Richtiger Auftrag und ehrliche Kriterien

Nicht den alten Toolingauftrag mit 81 Kriterien, zwei Rootvollgates und G01–G30 neu prüfen. Nicht den früheren `script-includes`-Auftrag wiederholen. Dessen dauerhafte Partialannahme ist für vollständig erfasste statische Includes nun ausdrücklich überholt; Nichtausführung und sichere Grenzen bleiben gültig.

Prüfe gleichwertig:

1. Die öffentlich beobachtbare Funktion des aktuellen Phasenziels.
2. Quell-/Anwendungs-/Scope-/Ressourcen-/Binding-/Modul-/Hostgrenzen und die angemessene Einfachheit des Codes.
3. Wirksame positive und negative Tests, reale Ausführung und deine eigene neue Gegenprobe.

Eine positive Komplettdarstellung ohne funktionierendes eindeutiges Rename genügt nicht für P4. Umgekehrt ist pauschales Read-only für alle Includejobs keine vollständige Lösung. Shared-Writes dürfen in diesem Auftrag bewusst abgelehnt werden; normale statische eindeutige Jobs müssen am Schluss wirklich funktionieren.

## 3. Phasenbezogene Prüfaufträge

### P0 – Basis, Baseline und autonome Kette

W01–W04 aus dem Plan prüfen: richtige integrierte Mainbasis mit bestehenden Runtime-Includes, separate Worktrees, passende reale kleine Baseline, funktionierender zweiter Agent und korrekter neuer Digest/Testumfang. Eine expected alte „apply fehlt“-Probe wäre falsch, weil die Runtimefunktion bereits geliefert ist.

Einen passenden Basistest selbst ausführen und eine eigene kleine Gegenprobe entwickeln. Ein Versionsaufruf oder ein Modellping beweist keine unabhängige Testfähigkeit. Prüfe, dass die Automation keine Verdicts vorfüllt und nicht unbemerkt alte Vollgates startet. P0 braucht keine Produktklasse nur zur Beschäftigung.

### P1 – Neutrale Syntaxextraktion und Anwendungskontext

IT01–IT04, IT06 und IT23 auf Komponentenebene. Lies den ursprünglichen Runtime-Reader und die sichere Tooling-Parsehülle, nicht nur den neuen neutralen Code.

Prüfe:

- Gemeinsame tatsächliche Includeerkennung statt zwei ähnlicher Parserimplementierungen; erhaltene Helper-/Shadowing-/Map-Signaturregeln.
- Platform-Classloader und begrenzte Parse/Convert-Grenzen im Tooling; keine Runtime-/Provider-/Grape-/ASTTestausführung.
- Runtimefassade, Fehlercodes, virtuelle Sourceidentität und UTF-16-Spans unverändert; echte Nichtausführungsprobe.
- Quelle, Entry, Scope, Anwendung und Ressourcenbasis getrennt; Fragment ist kein zusätzliches Projekt.
- Registrierung an der Apply-Site statt zufälliger Dateireihenfolge. `return`/opaque code nicht fälschlich vollständig.
- Neue neutrale Modulabhängigkeit in tatsächlichem Runtime-/Packager-/Serverprodukt; keine verbotene transitive Abhängigkeit und keine nur im Entwicklerclasspath funktionierende Lösung.

Neue eigene Probe z. B. eine nicht von Codex vorgegebene Kombination aus Shadowing, gleichem Basename und Unicodeposition. Vorhandene Runtime-SCRIPT/JAR-Nachweise passend prüfen. Noch keine vollständige Navigation/Renamefunktion verlangen.

### P2 – Closure, Ownerwissen, Buffers und Synchronisierung

IT03–IT10 sowie Context-/Preconditiongrundlagen. Verfolge mindestens einen Sourcefluss vom Host über SourceView/Closure bis zum Capture und wieder zur Invalidierung.

Einmal physische Quelle, mehrere Anwendungen. Zyklen/Duplikate pro Scope; fehlende Quellen/Abwesenheit im Dependencyvektor; erlaubtes `..` versus Root-/Symlinkausbruch. Richtige Ressourcen-/SQLbasis in zwei Besitzern. Aktueller offener Buffer vor Disk, kein zweiter inkonsistenter Read.

Der Ownerindex muss ungeöffnete unterstützte Einstiege berücksichtigen. Nur bekannte offene Panels zu zählen und daraus Exklusivität abzuleiten ist ein Blocker. Bounded unvollständige Suche muss UNKNOWN liefern. Ein späterer zweiter Besitzer invalidiert frühere Exklusivitätsbeweise. Keine allgemeine externe/global vollständige Suche vortäuschen.

Prüfe echte stdio-/Hostwege für bereits dirty geöffnete normale Groovyfragmente, Membershipübergang, didOpen/didChange, Fileevents geschlossener Fragmente und Close/Reopen. Sprache/Daten einer unbeteiligten Gradle-/Groovydatei unverändert. Alte Membership-/Kontexttokens nach Restart/Rootwechsel ablehnen.

Eigene Gegenprobe mit veränderter Ereignisreihenfolge, fehlendem Ziel und nachträglichem Create oder nicht geöffnetem zweiten Owner. Ein Mock allein beweist keine reale LSP-Synchronisierung.

### P3 – Semantik, Queries und vollständiger statischer Graph

IT04–IT06, IT11–IT16 und IT23; lesende IT22-Prozessnachweise. Prüfe die echte Vollständigkeitsaussage, nicht nur ob zusätzliche Nodes auftauchen.

Eine saubere statische typisierte Include-Fixture muss vollständig werden; separate dynamische/Recovery-/Limitfixture bleibt partiell. Marker ausserhalb dieser positiven Completenessfixture prüfen. Die Sourceclosure darf aufgrund Top-Level-Kontrollfluss potentiell mehr Quellen enthalten als tatsächlich registrierte Tasks; keine garantierte Registrierung erfinden.

TaskRef-Aliase pro Anwendung und Script: gleiche URI in zwei Scopes darf Aliasauflösung nicht vermischen; lokales `def` eines anderen Fragments ist kein Export. Qualifiednames, Forwardrefs, Parameter, Defaults und Ergebnisrechte fachlich prüfen. After wird nicht zur Datenabhängigkeit und Apply nicht zur Taskkante.

Bei Sharedfragmenten Ressourcen/SQLkontext und Graphcontainer prüfen. Sourceanker müssen Originaldatei/Range zeigen. Definition/References/Completion/Hover/DocumentLink im echten stdio-Server ausführen; Outline darf keine Fragmentranges in die falsche Datei übertragen. Kontextwechsel muss reproduzierbar sein, keine willkürliche erste Anwendung.

**Sicherheitszwischenstand:** P3 darf durch neue vollständige Coverage noch nicht den unqualifizierten alten Mehrdateien-Editpfad freigeben. Analyse- und Editfähigkeit sind getrennt. Prüfe, dass strukturelle Edits ohne P4-Footprint-/Owner-/Postimagebeweis kontrolliert gesperrt bleiben. Kein Falsch-PARTIAL nur um einen fehlenden Editguard zu kaschieren.

### P4 – Edits, native Anwendung und kumulative Abnahme

Alle IT01–IT24 bewerten, nicht nur den letzten Diff. Frühere Phasenabnahmen und erhaltene Belege nachvollziehen; eigene risikotragende Tests und eine neue übergreifende Gegenprobe selbst ausführen.

#### Positive notwendige Funktionen

Task- und Parameter-Rename über eindeutige Entry-/Fragment-/Settingsdateien mit genau den gebundenen Referenzen. Dependencies/After/Result/Defaults korrekt, Kommentare und sonstige Strings unberührt. Normale Fragmentfelder und passende Strukturintents grafisch bearbeiten. Keine pauschale Include-Schreibsperre als Ersatz.

Konkrete erwartete Textänderungen, danach gebundene Symbolidentitäten und Graph-/Resultrechte prüfen, nicht bloss neue Namensstrings. Ein No-op erzeugt keine Formatierung. Ein erfolgreicher lokaler Parseretest allein beweist keine gültige Mehrdateienänderung.

#### Negative Shared-/Footprintfälle

Gemeinsam verwendetes Target ablehnen. Zusätzlich: **eindeutiges Target, aber ein umzuschreibender Use liegt in einer Shareddatei** – ebenfalls ablehnen. Ungeöffneter zweiter Besitzer oder unvollständiger Ownerindex darf nicht ignoriert werden. Kontextauswahl ist keine Exklusivitätsermächtigung. Derselbe Schutz gilt für SetTaskOption, rekursive Referenzwerte, Formularbatch, Quick Fix und direkte Coreaufrufe.

Ein unabhängiger eindeutiger Sourceedit darf trotz eines nicht berührten Sharedfragments möglich bleiben. Keine unnötige globale Schreibsperre. Die dokumentierte Refactoringdomäne nicht heimlich auf offene Dateien verkleinern oder zu unbekannten externen Konsumenten vergrössern.

#### Postimages und Preconditions

EditVerifier muss eingefrorene Source-/Inventory-/Contextdaten benutzen, ohne neue IO. Alle betroffenen Includeanwendungen bleiben im Reparse erhalten; kein versehentlicher Standalonefallback. Fehlende Quelle erzwingt Neuaufnahme/Ablehnung. Source-/Owner-/Contextgenerationen müssen zwischen Plan und Anwendung stabil sein.

Stale Fälle: dirty Fragment, neuer Owner, Includezielwechsel, Close/Reopen mit gleicher Version, Serverrestart und Kontextwechsel. Prüfe keine Textmutation bei Ablehnung. Native Konvertierung kann asynchron sein; Guards auch danach. Ein WorkspaceEdit statt eigener Dateischreibschleife, und Zielpuffer müssen aktuell synchronisiert sein.

#### Echte GUI-/Runtimeintegration

Installiertes aktuelles VSIX und eigener echter Server. Native Navigation in Originalfragment, vollständiger Graph, Mehrdateien-Rename, Save/Undo/Redo der **tatsächlich geänderten Dokumente**, Shared-Kontextanzeige/Ablehnung. Keine alte Installation oder reine HTMLattrappe. Quell-/Buffer-/Version-/Artefaktbelege und relevante Bilder selbst lesen.

Der unveränderte gespeicherte Fixturetext läuft anschliessend in separatem lokalen Runtimeprozess; Ausgabeinhalt prüfen. IDEbedienung erzeugt keine Runtime-/Task-/ASTTestmarker. Kleine Package-/Modulgrenzenprüfung der neutralen Extraktion bleibt erforderlich.

Vorhandene aktuelle kandidatengleiche schwerere Belege unabhängig prüfen; nicht automatisch die vollständigen Benutzerabläufe duplizieren. Da Host-/Kontext-/Mehrdateienlogik geändert wird, mindestens eine eigene kleine reale Benutzerpfadgegenprobe ausführen. Daneben eigene Core-/stdio-Tests. Fehlende native Fähigkeit ist NOT_EXECUTED, kein durch Node-Mocks ersetztes PASS.

## 4. Eigene neue Gegenprobe je Phase

Formuliere selbst eine relevante Fehlerhypothese. Baue einen neuen kleinen Input oder Ereignisablauf, führe ihn wirklich aus und dokumentiere Erwartung, tatsächlichen Befehl/Exit, fachliche Assertions und Schlussfolgerung. Codex darf dir keine fertige „eigene“ Gegenprobe als Urteil vorgeben.

Geeignete Richtungen sind etwa: ein importaliasierter Helper-`apply`, Sharedfragment mit lokalem TaskRef-Alias, ungeöffneter zweiter Besitzer, erstmalig dirty geöffnete Childdatei, Quelle nach Close/Reopen mit gleicher Version, neuer Owner bei unveränderten Sourcebytes des Renameziels, ein eindeutiges Target mit Shared-Use, eine Quelle nach einem vorzeitigen `return`, oder ein Batch mit einem erlaubten lokalen und einem unerlaubten gemeinsamen Edit.

Das sind Themen, keine fertigen Orakel. Bei einem Negativfall muss der gewünschte Fachgrund auftreten, nicht irgendein Fehler durch fehlende Operation, kaputten Katalog, Transportabbruch oder fehlendes Java. Positive Gegenkontrolle verwenden, wo sie die Aussage schärft.

## 5. Gezielter Prüfumfang und Wiederverwendung

Plan §8 ist verbindlich. Keine automatische Wiederholung sämtlicher Root-/GeoTools-/Datenbank-/FTP-/S3-/GUIprüfungen. Wähle betroffene Familien und echte notwendige Integrationswege. Bei breiter Änderung am Parser oder EditVerifier nicht nur den neuen Happy Path testen. CI-/Releasegates selbst nicht verändern.

Frische Reports, ausgeführte Klassen/Methoden, Testanzahl >0, erwartete Assertions und Prozess-Exitcodes prüfen. `failIfNoSpecifiedTests=false` darf nur unbeteiligte Reactorabhängigkeiten beruhigen, nicht einen falschen Filter im Zielmodul verstecken. Alte XMLs, ungerechtfertigte Skips und `-DskipTests` sind kein Gate.

Eigene Tests müssen den bezeichneten Kandidaten benutzen, nicht fremde gleichnamige Maven-SNAPSHOTs. Bei einer Probekopie Originalbytes verifizieren. Wiederverwendete Belege mit Kandidaten-/Artefakt-/Inputidentität und Deltaargument ausweisen. Keine ursprüngliche SHA umetikettieren und keine eigene Ausführung behaupten, wo nur ein Report gelesen wurde.

Nach Fix bewertet der neue Review die gesamte Phase gegen die gleiche Phasebase, führt aber nur die potenziell betroffenen Tests erneut aus. Unveränderte schwere Belege dürfen bleiben, soweit ihre Identität bewiesen ist. Kosmetische Wünsche nicht zu fachlichen Blockern machen; keine neue allgemeine Evidenceplattform verlangen.

## 6. Testmigration nicht mit Testabschwächung verwechseln

Die frühere Aussage „ein gültiges apply bleibt immer partiell“ ist jetzt ausdrücklich zu ersetzen. Das ist kein unzulässiges Lockerungsmanöver, **wenn** es einen echten positiven Vollständigkeits-/Navigations-/Editnachweis und erhaltene negative Gegenfälle gibt.

Dagegen sind das pauschale Löschen von ApplyEditCompatibilityTest, das Akzeptieren eines partiellen Graphen als neuen Vollgraph oder das Ignorieren der Sharedsourcegrenze Blocker. Eine Markerquelle, die früher überhaupt nicht aufgelöst werden sollte, darf nun gelesen werden; sie darf weiterhin nicht ausgeführt werden. Aussage und Testoracle sauber trennen.

## 7. Bericht und strukturiertes Urteil

Originalbericht und JSON nach Plan §9.4 ins externe Evidenzziel schreiben oder vollständig an den Aufrufer zurückgeben. Nicht auf den geprüften Produktbranch committen. Kein vorgefülltes Urteil übernehmen.

```markdown
# Independent review: include-tooling / <PHASE> / <FULL_SHA>

## Urteil
ABGENOMMEN | NACHARBEIT | NICHT_PRUEFBAR

## Identität
Auftrag, Specversion/Digest, Auftragsbasis, feste Phasebase, Kandidat,
Reviewworktree/gegebenenfalls Probekopie, tatsächliche OpenCodeversion,
verifizierter Provider/Modellalias mit Zuordnung, frische Session.

## Code und Zielerreichung
Vollständige phasenbezogene Kriterienliste und konkrete öffentliche Wirkung.
Klare Trennung von vorhandener Basis, neuer Implementierung und Nicht-Zielen.

## Selbst ausgeführte Prüfungen
| Prüfung | Befehl | Exit / fachliche Assertion | Rohbeleg |
|---|---|---|---|
| ... | ... | ... | ... |

## Unabhängig geprüfte wiederverwendete Belege
Identität und begründete Unverändertheit; keine neue Ausführung behaupten.

## Eigene Gegenprobe
Hypothese, neuer Input/Ereignisablauf, Erwartung, Befehl,
beobachtete Wirkung, relevante Assertions und Schlussfolgerung.

## Findings
ID / BLOCKER|MAJOR|MINOR, Ort, reproduzierbarer Fall,
verletzter Vertrag, minimal nötige Korrektur und betroffene Nachtests.

## Integrität und Grenzen
Getrackte Produkt-/Testbytes unverändert, keine Erwartungslockerung,
NOT_EXECUTED getrennt von NOT_REQUIRED, Kontext-/Shared-/Plattformgrenzen.

## Entscheidung
Konkrete Phase freigegeben oder Bedingungen für neuen Kandidatenreview.
```

JSON enthält dieselbe Identität, vollständige Kriterienmenge, echte selbst ausgeführte Checks, separat wiederverwendete Belege, eigene ausgeführte Gegenproben, Findings und `required_not_executed`. Alle Pfade/SHAs/Sessiondaten tatsächlich prüfen. Kein Platzhalter gilt als Nachweis.

**ABGENOMMEN:** exakter Kandidat und richtiger neuer Auftrag, fällige Phasenziele belegt, eigene risikotragende Tests und neue Gegenprobe ausgeführt, keine offenen BLOCKER/MAJOR oder fehlenden Pflichtprüfungen. P4 zusätzlich native Benutzerpfad- und gespeicherte Runtimeparität, richtige Shared-Schreibgrenze.

**NACHARBEIT:** reproduzierbarer Defekt, fehlende notwendige Assertion, falsche Source-/Scope-/Alias-/Ressourcenbasis, stale Anwendung, unbekannte Owner als exklusiv, verlorene Quellen, falsche Paket-/Protokollgrenze, Testabschwächung oder fingierte Nachweise. Minimalen Fix und gezielte Nachtests nennen.

**NICHT_PRUEFBAR:** notwendiger Kandidat, Toolchain, Modellidentität oder reale Prüfungsfähigkeit fehlt. Konkreten Grund und tatsächlich geprüften Teilumfang angeben. Keine Abnahme aus plausibler Codelektüre oder früheren PASS-Berichten.

## 8. Autorität und Abschlussfrage

Du reparierst nicht selbst. Findings gehen an Codex, neue Produktänderung erzeugt einen neuen Kandidaten und erneuten unabhängigen Review. Kein Scopewachstum zu beliebiger Groovyinterpretation, globalen Sharedrefactorings oder neuer Runtime.

**Welcher konkrete Include-/Kontext-/Ownership-/Mehrdateienfall könnte diese Phase widerlegen – und habe ich ihn tatsächlich ausgeführt?**
