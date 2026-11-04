# Unabhängiger Review: GRETL-Scriptfragmente / OpenCode 2.x

**Fassung 1.3 · 4. November 2026 · Runtime-Includes auf integriertem Main mit sicherer partieller Tooling-Koexistenz.**

> Für **OpenCode / DeepSeek-V4.1-Flash-1017**, nicht für den Implementierer. Prüfe den konkret committeten Kandidaten gegen [EXECUTION_PLAN.md](EXECUTION_PLAN.md). Du bist unabhängiger Codeprüfer und Gegenbeispiel-Sucher. Keine Produktivfixes, keine eigenen Scopeänderungen und keine Selbstfreigabe des Implementierers.

## 1. Identität und isolierter Prüfstand

Der externe Handoff muss Auftrag `script-includes`, Specversion `1.3`, Phase, Requirement-Digest, Featurebasis, unveränderte Phasebase, vollständigen Kandidaten-SHA, Toolingbasis-SHA und dessen Abnahmestatus, deinen Worktree sowie Test-/Reportziele nennen. Eine versionierte Anfrage enthält absichtlich nicht den SHA ihres eigenen künftigen Commits.

Prüfe selbst `pwd`, `git rev-parse HEAD`, `git status --short` und den relevanten Diff. Der Checkout muss genau dem angegebenen Kandidaten entsprechen. Der Implementierungsbericht ist eine zu überprüfende Behauptung, kein Urteil. Lies Plan, unabhängigen Prompt, konkrete Phasenanforderungen und erforderlichen Code selbst.

Der aktuelle Main-Recherchepin ist `0bfc8f0fccf43989d751d4f5490202311d200628`; der Toolingauftrag P0–P8 ist dort abgeschlossen und integriert. Das archivierte P8-PASS bezieht sich auf den enthaltenen Produktkandidaten `54bb123070fc7bd81aea58b4e1191beecf568cbf`. Ausser `docs/` sind die Git-Root-Einträge zwischen diesem Produktpin und Main identisch. Prüfe den tatsächlich gewählten Featureausgangspunkt und die Abstammung selbst. Den früheren Review weder auf Main umetikettieren noch als Include-Abnahme benutzen.

Deine wirkliche OpenCode-2.x-/Modellauswahl muss durch externe Aufruf-/Sessionmetadaten belegt sein. Gewünschtes Modell: **DeepSeek-V4.1-Flash-1017**. Ein tatsächlich passend zugeordneter lokaler APIalias ist zulässig; Selbstauskunft des LLM allein nicht. Keine Authdateien lesen/ausgeben, Credentials exportieren oder auf ein anderes Modell wechseln.

Getrackte Produkt-, Test-, Konfigurations- und Erwartungsdateien im geprüften Checkout nicht ändern oder committen. Eigene Gegenproben in einem externen Testverzeichnis bzw. einer getrennten Probe-Kopie **desselben SHA** anlegen; Produktionsbytes müssen identisch bleiben. Builds dürfen ihre regulären Ausgaben schreiben. Vor/nach Prüfung getrackten Zustand kontrollieren. Keine anderen Worktrees, globalen Profile, produktiven Jobs, Pushes, Merges oder Releases anfassen.

## 2. Auftrag von alter Toolingabnahme unterscheiden

Der bestehende `tools/gretl-tooling/review.mjs` ist an den alten Toolingauftrag, dessen 81 Kriterien, zwei Rootvollgates und GUI-Fallmengen gebunden. Diese Verträge weder für den neuen Auftrag abschwächen noch unverändert als falsche Zusatzpflicht übernehmen.

Prüfe den **Feature-Request** mit seinen S-/C-Kriterien und seiner risikobasierten Testauswahl. Ein korrekt wiederverwendeter Transport darf alte Label technisch abbilden, nicht alte Kriterien als Featureerfüllung ausgeben. Eine Anfrage mit fremder Auftrags-ID, falschem Digest/SHA oder fehlenden relevanten Kriterien ist nicht gültig.

Du beantwortest drei Fragen gleichwertig:

1. Funktioniert das öffentlich beobachtbare Phasenziel tatsächlich?
2. Sind Code, Source-/Scope-/Binding-/Lifecycle-/Paket-/Toolinggrenzen korrekt und angemessen einfach?
3. Sind notwendige Tests wirksam, wirklich ausgeführt und durch eine eigene neue Gegenprobe ergänzt?

Keine Abnahme aufgrund von Klassenzahl, grünem Buildbanner, fremdem PASS oder einer blossen Claim-Tabelle.

## 3. Phasenbezogene Prüfung

### P0 – Basis und funktionierende autonome Qualitätskette

Prüfe separate Worktrees, erhaltene fremde Änderungen, den integrierten Main-Ausgangspunkt mit enthaltenem akzeptiertem Tooling-Produkt-SHA und die kleine sinnvolle Baseline. Für diesen Auftrag gilt nach geprüfter Abstammung `MAIN_WITH_ACCEPTED_TOOLING`; keine provisorische Toolingbasis und keine noch ausstehende P8-Freigabe. Bei Fortsetzung eines älteren Include-Branches dessen echte Integration und bestehende Teilbelege prüfen, nicht ohne fachlichen Grund alle Phasen neu verlangen.

Führe einen passenden bestehenden Basistest und eine selbst entworfene kleine Baselinegegenprobe aus. Ein noch nicht unterstütztes `apply` darf in P0 erwartungsgemäss scheitern; das ist keine fertige Featurewirkung. Stelle fest, dass du tatsächlich in einem neuen unabhängigen Kontext aufgerufen wurdest, selbst Werkzeuge/Tests nutzen und ein prüfbares Urteil liefern kannst. Ein reiner Modell-Ping genügt nicht.

Prüfe insbesondere, dass die Featureautomation nicht versehentlich die alten Toolingvollgates/Kriterien erzwingt und keine Ergebnisdateien/Urteile vorbefüllt. Keine neue allgemeine Reviewplattform fordern.

### P1 – Statische Erkennung, sichere Quellen und Hash

Prüfe den passenden Komponentenvertrag S01–S03, S09–S10, S12 und S14–S15 im P1-Umfang:

- AST-/Tokenbasierte Map-first-/Literal-/Kontextprüfung. Keine Regex-Erkennung, die Kommentare, Strings oder fremde Receiver als Include wertet.
- Parse/Convert ohne Toplevelausführung, AST-Test-/Projektplugininitialisierung, Grape oder freie Auswertung. Eigene Nichtausführungsprobe wirklich ausführen.
- Include-Basis ist die einbindende Quelldatei. Zulässiges internes `..` von Rootausbruch unterscheiden; reguläre lokale Dateien, Symlink-/URI-/Absolute-Pfadgrenzen und bounded Reads.
- DFS-Cycles und doppelte Anwendung pro besitzendem Scope; gleiche physische Quelle in unterschiedlichen Subprojekten bleibt zulässig. Echte Projekteinstiege nicht zu Fragmenten umdeuten.
- Konsistente einmal erfasste Bytes für Resolver/Compiler/Hash, alle verschachtelten Fragmente im Hash, portable Identität ohne absolute Hostpfade. Kein globaler veralteter Sourcecache.

Fordere nicht die erst in P2/P3 lieferbare vollständige CLI-/JAR-Wirkung, aber keine ungeprüften Parser-/Resolverattrappen akzeptieren.

### P2 – SCRIPT, gemeinsame Definition und getrennte Bindings

Prüfe gemeinsam genutzten Builder **und** TaskRef-/Operationsauflösung. Ein Builder allein bei separaten Einzeljob-Maps ist eine mögliche Fehlerquelle. Kein gemeinsames Binding als Abkürzung.

Tatsächliche Ausführung mit Vorwärtsreferenz und Ergebnissen über Dateien, getrennten lokalen Variablen/Imports, einem finalen Freeze/Runbericht und gemeinsamem Cleanup nachweisen. Konfiguration bleibt spät; `apply` registriert, führt aber keine Tasks aus. `after` bleibt reine Reihenfolge und verleiht keine Ergebnisrechte.

Pfade und spätere Fehler prüfen: Include-Basis versus `file`/`ctx.file`, fremdes CWD, gleicher Fragmentpfad in zwei Subprojekten mit unterschiedlichen Ressourcen und qualifizierten Tasknamen. Ein Fehler aus einer später ausgeführten Closure muss auf die echte Fragmentdatei/Zeile zeigen. Doppelregistrierung und Fragmentdefaults dürfen nicht still überschreiben.

Kleine reale `run/check/plan/help`-Probe durchführen. Bei normalem Definitionsladen darf vertrauenswürdiger Toplevelcode laufen; Taskkonfiguration, Tasks und Cleanup bei Introspektion nicht. Keine allgemeine Groovy-Sandbox fordern. Jobs ohne Includes und ihre bisherigen Identitätsverträge bleiben kompatibel.

### P3 – Paketierung und quellenfreie Job-JARs

Prüfe exakte erlaubte Scriptmenge. Pauschales Entfernen von `PACKAGE_MULTIPLE_SCRIPTS` ohne neue korrekte Mengenkontrolle ist ein Blocker. Fragmente sind keine gefälschten Subprojekte; Klassen können gemeinsam kompiliert, müssen aber pro Anwendung korrekt instanziiert werden.

Benutzerdeskriptorschema, interne Jobmetadaten, Dependencyinventar und LSP-Wireversion getrennt halten. Neue Metadaten validieren IDs, Klassen-/Kantenziele, Pfade, Scope-/Moduskombinationen und Versionen strikt; alte Formate bleiben tatsächlich startbar.

Einen Einzeljob und ein Projekt als **wirkliche** `java -jar`-Prozesse ohne ursprünglichen Quellbaum ausführen. Eine zufällig im CWD verfügbare `.groovy`-Datei darf den Test nicht retten. Keine Nachkompilation oder Settingsausführung beim Start. Paketieren startet keine Scriptbodies/Tasks. Ausgabeinhalt, konsistenten Sourcehash und einen sourcegenauen Fehler prüfen. Nicht sämtliche Fachoperationen neu paketieren.

### P4 – Gemeinsamer Schlussstand und sichere bestehende Produktoberfläche

Prüfe kumulativ S01–S19 und C01–C08, nicht nur den letzten Diff. Der Schlusskandidat enthält die in P0 verifizierte integrierte Mainbasis; fremde Worktrees und historische Reviews sind unverändert. Bei direktem Start von Main keine zusätzliche Tooling-Mergeprobe fordern. Eine wirklich später notwendige Upstreamintegration gesondert prüfen. Der vorhandene P8-PASS ersetzt die fälligen Feature-/Kompatibilitätstests nicht.

#### Core und wirksame Editregeln

Führe die ansonsten geschlossene Apply-Fixture und ihre Kontrolle ohne `apply` aus. Das unbekannte Include verursacht partielle Coverage und einen opaken Graphbereich, nicht ein zweiter Marker oder unbekannter Operationstyp. Nichtausführung durch eine getrennte Markerfixture prüfen.

Prüfe Rename/Delete/Add-/RemoveDependency/BindResult gegen passende Coverage-Sperren. **Zusätzlich** gleichwirkende Zugänge über `SetTaskOption(dependsOn/after)`, Formularbatches und referenzbildende Feldsetter prüfen. Die vorhandene allgemeine Optionsfähigkeit wird aus einem Description-Intent ermittelt und ist allein keine Freigabe jeder Graphmutation. Den Gesamtpfad einschliesslich Verifier lesen: eine fehlende lokale Prüfung ist noch kein nachgewiesener End-to-end-Defekt.

Bei Defaults keine unbekannten Ziele verlieren oder Auflösung vortäuschen. Unbelegte sicherheitsrelevante Änderungen ablehnen; bewiesen sichere lokale Änderungen nicht pauschal verbieten. Eine klare skalare Feldänderung muss funktionieren und den Rest des Textes samt Includezeile/Fragmentbytes bytegenau erhalten. Kein Flatten, kein Full-file-Rewrite, keine versehentliche Dateierstellung.

#### Realer Server und installierte GUI

C04 über den frisch gebauten stdio-Server ausführen: Graph/konkreter Edit/Validierung, richtige Server-/Snapshot-/Sourceidentität, stale Plan nach Einstiegs- oder Include-Literaländerung. Passende fachliche Ablehnung von fehlender Dependency, kaputtem Katalog, Protokollfehler oder Prozessabsturz unterscheiden.

C05 benötigt einen echten installierten VSIX-Ablauf mit partieller Anzeige, lokaler Änderung, verständlicher Ablehnung, Speichern und Undo/Redo. Bestehende aktuelle kandidatengleiche Rohbelege darfst du unabhängig verifizieren; zusätzlich eigene relevante Core-/Wiretests und eine neue Gegenprobe selbst ausführen. Bei einer Änderung der tatsächlichen GUI-/Hostlogik eine passende eigene Benutzerpfadgegenprobe durchführen. Nicht bloss einen Unit-Mock als GUItest akzeptieren und nicht aus Routine alle G01–G30 wiederholen.

C06–C08 prüfen: keine Runtimeausführung/-dependency im IDEpfad, bestehende gemeinsame Regeln/IDs/Protokolle erhalten, stabiler gemeinsamer Kandidat und ehrlich begrenzte Include-Unterstützung. Der aktuelle Resolver liest keine vollständige Fragmentclosure; deren vollständige Navigation/Invalidierung darf nicht plötzlich als geliefert gelten. Ein allein geöffnetes Shared-Fragment besitzt nicht automatisch den richtigen Anwendungskontext.

#### Grenzen zulässiger Korrekturen

In P4 sind im eigenen Featurebranch auf Main **kleine reproduzierte Kompatibilitätsguards und zugehörige Tests ausdrücklich beauftragt**. Sie sind nicht allein wegen ihres Tooling-Dateipfads ein Scopeverstoss. Prüfe jedoch Red/Green-Begründung, minimalen Diff, erhaltene sichere lokale Edits und direkte Regression. Keine Änderung am ursprünglichen Toolingreviewverfahren oder unaufgeforderte vollständige Include-IDE-Engine akzeptieren. Der Reviewer repariert selbst nichts.

## 4. Eigene Gegenprobe

Entwickle mindestens eine **neue fachliche Gegenprobe je Phase**, nicht nur die Codex-Fixture unter anderem Namen. Dokumentiere Fehlerhypothese, vollständigen reproduzierbaren Input, erwartete Wirkung aus dem Vertrag, wirklich ausgeführten Befehl, Exitstatus, relevante Ausgabe/Assertion und Schlussfolgerung.

Mögliche Richtungen: gleiche Basenames mit verschiedenen Bytes; shared Fragment in zwei Scopes; Änderung nur im Enkel-Fragment; erlaubtes internes `..` bei abweichender Ressourcenbasis; verspäteter Closurefehler; falsche Metadatenklasse bei zufällig noch vorhandener Quelldatei; generischer Optionssetter statt explizitem Dependency-Intent; Batch mit sicherem und unzulässigem Bestandteil; Apply-Literaländerung zwischen Editplanung und Validierung.

Die Themen sind Orientierung, kein von Codex vorab festgelegtes Testorakel. In P4 eine echte Datei-/Modus-/Toolinginteraktion wählen. Ohne ausgeführte eigene Probe keine vollständige Abnahme. Ein beliebiger Fehler oder Infrastrukturabbruch ist keine erfolgreiche fachliche Rejection.

## 5. Risikobasierter Prüfumfang und Belege

Nur notwendige Tests nach Plan §8. Keine automatische vollständige Operationen-, PostGIS-, FTP-, S3-, GeoTools-, Browser- oder Tooling-P8-Qualifikation. Die für diesen Featureauftrag begrenzten lokalen Phasengates ändern keine bestehenden CI-/Releaseverträge. Der kleine C05-GUI-Nachweis ist dennoch fällig.

Prüfe frische XML-/Prozessberichte, tatsächlich ausgeführte Klassen/Fälle, Exitcodes und Inhalt. Kein Nulltest-PASS durch zu lockere Filter, keine alten XMLs, unerklärten Skips oder `-DskipTests` als Testbeweis. Fachliche Assertions nicht passend lockern. Ein aktueller unveränderter schwererer Beleg muss nicht sinnlos nochmals ausgeführt werden; sein Zusammenhang mit Kandidat/Artefakten muss unabhängig verifiziert sein.

Eigene Testläufe müssen den bezeichneten Code benutzen, nicht die gleichnamigen SNAPSHOTs eines parallel laufenden Worktrees. Relevante produktive Bytes und Artefakte identifizieren. Review- und Probeumgebung getrennt ausweisen. Notwendige Infrastrukturfehler als `NOT_EXECUTED`; tatsächlich nicht erforderliche Fremdtests begründet `NOT_REQUIRED`.

Bei Nacharbeit die gesamte Phase inhaltlich erneut bewerten, aber nur vom Fix potenziell betroffene Tests neu starten. Unveränderte Teilbelege nicht als neue Ausführung ausgeben. Kein neues allgemeines Review-/Evidenceframework fordern und keine kosmetischen Wünsche zu fachlichen BLOCKERn machen.

## 6. Bericht und maschinenprüfbares Urteil

Originalbericht und JSON nach Plan §9.4 in das externe Reportziel schreiben; nicht auf den geprüften Branch committen. Falls nur Rückgabe möglich ist, beide vollständig zurückgeben und unverändert vom Aufrufer speichern lassen. Keine vorgefüllten Urteile übernehmen.

```markdown
# Independent review: script-includes / <PHASE> / <FULL_HEAD_SHA>

## Urteil
ABGENOMMEN | NACHARBEIT | NICHT_PRUEFBAR

## Identität
Specversion, Requirement-Digest, Featurebasis, Phasebase, Kandidaten-SHA,
Main-/Featurebasis, akzeptierter Tooling-Produkt-SHA und Basisstatus, tatsächlicher Worktree,
OpenCode-Version, verifizierter provider/model-Wert, frische Session-ID.

## Code und Zielerreichung
Geprüfte Phasenziele/S-/C-Verträge, tatsächliche öffentliche Wirkung,
relevante Grenzen; keine Behauptung vollständiger Include-IDE-Unterstützung.

## Selbst ausgeführte Prüfungen
| Prüfung | Tatsächliches Kommando | Exit und fachliche Assertion | Rohbeleg |
|---|---|---|---|
| ... | ... | ... | ... |

## Unabhängig geprüfte vorhandene Belege
Kandidaten-/Artefaktidentität, weshalb Wiederverwendung zulässig ist;
klar von eigenen neuen Ausführungen getrennt.

## Eigene Gegenprobe
Hypothese, neuer Input, erwartete Wirkung, Befehl/Exit/beobachtete Wirkung.

## Findings
<ID> / BLOCKER|MAJOR|MINOR: Ort, Reproduktion, verletzter Vertrag,
minimal notwendige Korrektur und betroffene Regression.

## Integrität und offene Grenzen
Getrackter Stand unverändert? Keine Testlockerung?
Fälliges NOT_EXECUTED getrennt von begründetem NOT_REQUIRED.
Bei P4: tatsächlicher gemeinsamer Stand und GUI-/Wirebeleg.

## Entscheidung
Konkrete Freigabe oder Bedingungen für den nächsten Kandidatenreview.
```

Das JSON enthält die Identitätsfelder aus dem Plan, `verdict`, den vollständig abgeprüften Phasenumfang, selbst ausgeführte Tests mit Reports, eine eigene ausgeführte Gegenprobe, Findings und fällige fehlende Prüfungen. Wiederverwendete Belege separat auflisten. Alle Pfade/SHAs/IDs tatsächlich prüfen; keine Platzhalter und keine erfundenen Sessions/Tests. Codex muss das Urteil zusätzlich gegen echte Aufruf-/Sessionmetadaten prüfen.

**ABGENOMMEN:** exakter Kandidat, alle fälligen Phasenziele belegt, notwendige eigene Tests und neue Gegenprobe ausgeführt, keine offenen BLOCKER/MAJOR oder fehlenden Pflichtnachweise. P4 zusätzlich akzeptierte gemeinsame Basis und C01–C08; Ergebnis ist sichere **partielle**, nicht vollständige Include-IDE-Unterstützung.

**NACHARBEIT:** konkreter reproduzierbarer Defekt oder fehlende notwendige Assertion, Source-/Scope-/Bindingverlust, falsche Paketparität, unsichere IDEmutation, unzulässige fremde Änderungen, Testabschwächung oder fingierte Nachweise. Minimalen Fix und gezielte Nachtests nennen.

**NICHT_PRUEFBAR:** benötigte Revision, Toolchain, Reviewerfähigkeit oder fällige Ausführungsumgebung fehlt. Ein aus alten Dokumenten übernommenes Warten auf die bereits abgeschlossene Tooling-P8 ist kein gültiger Grund. Nicht durch ältere Reports oder plausible Codelektüre ersetzen. Ursache und verbleibenden geprüften Teilumfang präzise nennen.

## 7. Autorität

Keine Produktivfixes, Anforderungsänderungen, Testlockerungen, globalen Provideränderungen oder Publikation. Findings gehen an Codex; nach Fix entsteht ein neuer Kandidat und eine neue unabhängige Session. Freigaben gelten nur für den tatsächlichen SHA und den bezeichneten Umfang.

**Leitfrage:** Welcher konkrete neue Include-/Scope-/Pfad-/Paket-/Editfall könnte diesen Kandidaten widerlegen – und habe ich ihn tatsächlich ausgeführt?
