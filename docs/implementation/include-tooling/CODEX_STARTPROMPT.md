# Startprompt für Codex: GRETL Include-aware Tooling

**Fassung 1.0 · 5. November 2026**  
**Auftrag:** `include-tooling` · **Repository:** `edigonzales/gretl-runtime`

> Diesen Text als ausdrücklichen Implementierungsauftrag übergeben. Die Dateien gehören nach `docs/implementation/include-tooling/` im Repository. Alle Repositorypfade sind relativ zu dessen Root. Dieser Auftrag ersetzt keine historischen Reviews und startet die abgeschlossenen Runtime-/Toolingaufträge nicht nochmals.

Du bist **Codex, der verantwortliche Implementierungsagent**. Implementiere die Include-Unterstützung im bestehenden GRETL-LSP und grafischen Modeler gemäss [EXECUTION_PLAN.md](EXECUTION_PLAN.md). Liefere echten Code, passende Tests, lokale Commits und nach jeder Phase eine **autonome unabhängige Prüfung durch OpenCode 2.x mit DeepSeek Flash 1017**. Keine erneute allgemeine Planung anstelle der Umsetzung.

## 1. Ergebnis und Arbeitsbasis

`apply from:` funktioniert bereits in der Runtime. Jetzt soll ein normaler statisch beschreibbarer Includejob auch in der IDE vollständig analysierbar sein: Fragmenttasks im richtigen Graphen, Navigation/Completion über Dateien, ungespeicherte Fragmenttexte korrekt berücksichtigt und sichere Mehrdateien-Edits einschliesslich Rename.

Recherchebasis ist `origin/main` bei **`ae0a2630156280e0e7f6bebf2354884f555d8d7e`**. Aktuellen lokalen/Remote-Stand prüfen und dessen relevante Deltas lesen; nicht auf den Pin zurücksetzen. Neuer eigener Branch vorzugsweise **`codex/include-tooling`**, eigener Worktree. Vorhandenen Featurebestand erhalten. Kein Stash, Reset, Clean oder Branchwechsel in fremden Arbeitsbäumen.

Die Runtime-Semantik S01–S19 bleibt unverändert. Die frühere IDEgrenze „wegen apply immer PARTIAL“ wird für den **vollständig erfassten statischen Fall** gezielt abgelöst. Dynamische/unvollständige Fälle bleiben konservativ und sicher. Historische C01–C08-Reviews gelten für den damaligen Umfang, nicht als Freigabe der neuen Funktionen.

**Bewusste Grenze:** Shared-Fragmente lesend in allen erfassten Kontexten unterstützen. In dieser Lieferung keine automatisch geplanten Writes in eine mehrfach verwendete oder nicht hinreichend eindeutig zugeordnete physische Quelle. Der ganze Write-Footprint zählt: Auch ein eindeutiges Renameziel darf keinen Use in einer Shareddatei unbemerkt verändern. Unabhängige Edits in anderen eindeutigen Dateien bleiben möglich. Keine beliebige Groovy-Interpretation, kein globales Sharedrefactoring und kein `finalizedBy`.

## 2. Sofortige Schritte

1. `pwd`, `git status --short`, HEAD, Worktrees, lokale/Remote-Refs, JDK25, Mavenwrapper und Node prüfen. Fremde Änderungen und Benutzerprofile erhalten. Eigene saubere Featurebasis festhalten.
2. Root-`AGENTS.md`, [EXECUTION_PLAN.md](EXECUTION_PLAN.md), [OPENCODE_REVIEW_PROMPT.md](OPENCODE_REVIEW_PROMPT.md) und [SOURCE_BASELINE.md](SOURCE_BASELINE.md) lesen. Danach Include-README/Handoff und nur die einschlägigen aktuellen Klassen/Tests. Alte abgeschlossene Agentenpläne sind keine fortzusetzenden Aufträge.
3. Eine kurze `WORK_QUEUE.md` in diesem Featureordner anlegen: aktueller Stand oben, Basis, Phasen, notwendige Tests, Kandidaten-/Review-SHAs und konkret nächste Arbeit. Keine neue grosse Evidence-/Kriterienplattform.
4. Kleine echte Baseline ausführen: vorhandener erfolgreicher Runtime-Includefall, aktuell noch partielle Toolingfixture und passende Edit-/Serverprobe. Nicht den veralteten Fall „apply existiert noch nicht“ übernehmen.
5. Vor grösseren Änderungen den autonomen unabhängigen Reviewweg real beweisen. Existierende lokale Include-Reviewmechanik nach Prüfung verwenden; andernfalls kleinen Adapter gemäss Plan §9. Eine reine Versions-/Modellabfrage ist kein ausreichender Nachweis.
6. P0 unabhängig abnehmen lassen. Danach phasenweise umsetzen, ohne den Benutzer um reguläre Weiterfreigaben zu bitten.

## 3. Phasen und harte Gates

| Phase | Lieferung |
|---|---|
| **P0** | Separate Arbeitsbasis, kleine funktionierende Baseline, tatsächlicher autonomer Zweitreview und richtige Testauswahl. |
| **P1** | Gemeinsame sichere Include-AST-Erkennung, unveränderte Runtimefassade, Quellen-/Anwendungsmodell und Ordnungsvertrag. |
| **P2** | Transitive Captures, Buffervorrang, Ownerindex, Ressourcenbasis, Kontextregistry und Fragment-/Watcher-Synchronisierung. |
| **P3** | Vollständige statische Semantik, Navigation, Completion, Kontextwahl, Graph und Zielvorschau. |
| **P4** | Sichere Mehrdateien-Edits/Rename, Shared-/Footprintguards, native VSIX-Abnahme und kumulativer Schlussreview. |

Je Phase: reproduzierender Test bzw. neuer Vertrag → minimale Implementierung → notwendige Regression → lokaler Kandidatencommit → **neue unabhängige OpenCode-Session am exakten SHA** → konkrete Findings korrigieren und neuen SHA erneut prüfen. Nur `ABGENOMMEN` ohne fehlende Pflichtnachweise erlaubt die nächste Phase.

Der Reviewer prüft Code **und Zielerreichung**, führt selbst notwendige Tests und mindestens eine selbst entwickelte neue Gegenprobe aus. Eigene Codex-Prüfung ist kein Zweitreview. Bei Korrekturen nur die potenziell betroffenen Tests neu ausführen; der Reviewer betrachtet trotzdem die ganze Phase gegen ihre unveränderte Phasebase. Alte Läufe nicht als frisch am neuen SHA ausgeben.

## 4. Architektur, die du beibehalten musst

**Gemeinsame Syntax, keine verbotenen Abhängigkeiten.** Die komplexe Includeerkennung nicht separat im LSP nachprogrammieren. Vorgesehen ist die eng begrenzte Extraktion in das neue neutrale `gretl-script-syntax` mit Groovy/JDK und Datenverträgen; einen inzwischen vorhandenen gleichwertigen Ort verwenden statt duplizieren. `gretl-language-contract` bleibt JDK-only. Die Runtime behält `ApplyDirectiveReader` als kompatible Fassade. Der Toolingparser behält seine sichere Classloader-/Recovery-/Budgethülle und übergibt nur den bereits konvertierten AST an den gemeinsamen Inspector. Kein Runtime-/Runner-/Taskprovider-/Packagerimport in Core oder LSP, keine Tooling-Core-Produktionsabhängigkeit der Runtime.

**Quelle ist nicht Verwendung.** Eine physische URI hat ein Parseergebnis, aber möglicherweise mehrere Anwendungen mit Projekt, Entry, Scope und Ressourcenbasis. Fragmente nicht als zusätzliche Subprojekte modellieren. Sourceanker zeigen weiterhin auf Originaldateien. TaskRef-Aliase sind anwendungs- und dateilokal, nicht URI-global und nicht über Fragmente exportiert.

**Zwei Pfadbasen.** Include relativ zur enthaltenden Quelldatei; `file`/`ctx.file` und SQLressourcen relativ zum besitzenden Job/Subprojekt. `ProjectAnalyzer` und SQL-/Link-/Completionpfade ausdrücklich anpassen; bloss zusätzliche Dokumente einzulesen genügt nicht.

**Reihenfolge und Wahrheit.** Deklarationen an der Apply-Site depth-first in die Registrierungsfolge einfügen; bestehende `PlanSemantics` verwenden. Keine Include-DAGkante. `return`, `throw`, Recovery und opaker Kontrollfluss dürfen nicht plötzlich COMPLETE werden. Keine allgemeine Groovy-Kontrollflussengine bauen.

**Captures und Ownership.** Offene Buffer vor Disk, negative Reads und Listings erfassen, Source-/Owner-/Contextgenerationen prüfen. Sharedänderungen invalidieren alle Besitzer. Ein neuer ungeöffneter Besitzer darf nicht an einem nur auf offenen Panels beruhenden Guard vorbeigehen. Die Refactoringdomäne umfasst die unterstützten/registrierten Workspace-Einstiege; unbekannte externe Konsumenten nicht als garantiert erfasst darstellen.

## 5. LSP und Client gehören beide zum Auftrag

Der aktuelle Client synchronisiert `gretl-runtime` und beobachtet noch nicht jeden beliebig benannten Groovyfragmentpfad. Ergänze serverseitige Membership, gezielte Languageübergänge, relevante Fileevents und bestätigte Kontextwahl gemeinsam. Keine globale `*.groovy`-Sprachassoziation und keine automatische Speicherung.

Besonders prüfen: Ein Fragment ist schon als normales Groovy mit ungespeichertem Text geöffnet, bevor ein Entry es einbindet. Die IDE darf weder den Text verlieren noch auf der älteren Diskversion einen editierbar frischen Graphen präsentieren. Sprachwechsel können Dokumentinkarnationen ändern; alte Pläne invalidieren.

Nutze die bestehenden Standard-LSP-Queries und `gretl/graph`, `gretl/planEdit`, `gretl/validateEdit`, `gretl/navigation`. Neue Context-/Membershiprequests im Plan sind ausdrücklich neue kleine Erweiterungen mit Capabilityaushandlung; nicht als vorhandene APIs voraussetzen. Keine ungeprüften privaten Felder in Standard-LSP-Requests.

Bei Shared-Fragmenteinzelansicht Kontext explizit auswählen oder Mehrdeutigkeit anzeigen. Pinned Panels halten ihre eigene Auswahl; ein anderer Request darf sie nicht still überschreiben. Ursprungssource und Kontext müssen in Navigation/Graph/Form zusammenpassen.

## 6. Mehrdateien-Edits erst nach vollständigem Beweis freigeben

Eine in P3 vollständige lesende Analyse öffnet noch nicht automatisch alle alten Editpfade. Bis der P4-Footprint-/Ownership-/Postimagepfad implementiert und geprüft ist, strukturelle Include-Edits kontrolliert gesperrt halten. Coverage und Editfähigkeit sind unterschiedliche Aussagen.

In P4 müssen **positive** eindeutige Rename-/Dependency-/Result-/Formularfälle wirklich funktionieren, nicht bloss alle Änderungen abgewiesen werden. Alle gleichwirkenden Setter und Formularbatches prüfen. Ein eindeutiger Target mit zu schreibender Referenz in einer Shareddatei bleibt abgelehnt. Ein anderer unabhängiger eindeutiger Sourceedit darf trotzdem erlaubt sein.

`EditVerifier` analysiert die virtuellen Postimages aus eingefrorenen Quellen und Ownerinformationen erneut, ohne neue IO oder Verlust des Einbinderkontexts. Originalranges, Kommentare, CRLF, Unicode und unberührte Dateien erhalten. Keine Flattenkopie und kein Full-file-Rewrite.

Host-/Serverguards berücksichtigen alle Quellen, Inkarnationen, Owner- und Kontextgenerationen. Alle Zielpuffer vor Mehrdateienänderung synchronisieren und neu planen; einen nativen WorkspaceEdit verwenden. Save/Undo/Redo muss tatsächlich geänderte Fragmentdateien behandeln, nicht nur den gepinnten Einstieg. Keine eigene Rückpatchengine oder behauptete atomare Filesystemtransaktion.

## 7. Tests ausreichend, aber gezielt

Plan §1.3/8 ist die ausdrückliche lokale Testausnahme dieses Nutzerauftrags. Keine automatischen beiden Rootvollgates nach jeder Phase, keine komplette GeoTools-/Dienst-/Operationen-/GUI-Matrix. Bestehende CI-/Releasegates selbst unverändert lassen. Bei einem breit wirkenden Parser-/EditVerifier-Eingriff die gesamte kleine betroffene Familie prüfen, nicht nur Happy Path.

Positive Completenessfixture mit statischen typisierten Tasks; Nichtausführung durch separate Markerfixture. Shared-/Owner-/Dirty-/Staletests möglichst in Core/stdio; zwei kleine zusammenhängende installierte VSIX-Abläufe am Ende. Ein Mock ist kein nativer GUI- oder echter Servernachweis.

Der neue Syntaxmodulclasspath erfordert eine echte kleine SCRIPT/JAR-/Paketregression. Unveränderte Fachoperationen danach nicht bei jedem Guardfix neu qualifizieren. Den am Ende grafisch bearbeiteten **gespeicherten** Fixturetext getrennt in der Runtime ausführen und tatsächliche Ausgabe prüfen.

Aktuelle Surefire-/Failsafe-/Node-Harnesses benutzen; Nulltest-PASS, stale XML, unbemerkte Skips und Testskips als Nachweis verhindern. Maven-SNAPSHOTs verschiedener Worktrees nicht mischen. Rohberichte vor `clean` sichern. Wiederverwendete identische Belege ausdrücklich von neuen Läufen unterscheiden.

## 8. Autonome Prüfung mit OpenCode 2.x / DeepSeek Flash 1017

Tatsächlichen Binarypfad, 2.x-Version, `run --help`, gespeicherte Anmeldung und Modell-ID prüfen. Eine GUI2-Version beweist keine CLI2 im PATH. Vorhandene Credentials verwenden; **keine neue Pflicht-ENV für den DeepSeek-Key**, keine Authdateien ausgeben. `deepseek-flash` wird laut offizieller Modellseite am Recherchetag DeepSeek-V4.1-Flash-1017 zugeordnet; beweglichen Alias vor dem Lauf erneut verifizieren. Kein stiller Modell-/Providerwechsel.

Der alte `tools/gretl-tooling/review.mjs` ist kein neutraler Featurelauncher. Vorhandene lokale Include-Reviewsequenz prüfen oder einen kleinen `tools/include-tooling/`-Adapter schaffen; keine neue 81-Kriterien-/Vollgateplattform. Der neue Handoff nennt `assignment_id: include-tooling`, `spec_version: 1.0` und den Digest dieser drei Auftragsdateien.

Pro Phase den stabilen Request vor Commit schreiben, Kandidaten-SHA erst **nach** Commit extern erfassen. Neuer detached Reviewworktree am SHA, frische Session ohne Continue, passende Location/PWD, externe Evidenz. Lokale eingeschränkte V2-Policy erlaubt notwendige Reads/Tests/Builds und eigene Proben ohne Nutzerklicks, verbietet Produktfixes, Secrets, fremde Worktrees und Publikation. Getrackte Bytes vor/nach Review prüfen.

Auf tatsächlichen Prozess-/Streamabschluss und eigene beendete Kinder achten. Originalbericht, Session-/Modellmetadaten, echte Testlogs, unabhängige Gegenprobe, Kriterienabdeckung und Urteil maschinell prüfen. Exit0 oder PASS im Text allein genügt nicht. Bei Findings korrigieren und neuen Kandidaten unabhängig prüfen, nicht den Reviewer zum Implementierer machen.

Bei einem echten Blocker begrenzt vorhandene legitime Wege prüfen, dann Kandidat/Ursache erhalten und `BLOCKED_REVIEW_INFRA` bzw. `BLOCKED_TEST` melden. Keine fingierte Abnahme, keine nächste Phase und kein manueller Prompttransport als vermeintlich erfolgreich erfüllte Autonomie.

## 9. Abschluss

Liefere tatsächliche Basis, Branch, final unabhängig geprüften Code-/Doc-SHA, eventuell separaten Statuscommit, funktionierendes Beispiel, Test-/Reviewbelege und bewusste Grenzen. Endstatus: **`STATIC_INCLUDE_AWARE_TOOLING_WITH_GUARDED_SHARED_EDITS`**. Normale statische eindeutige Includejobs sollen voll nutzbar sein; dynamische/mehrdeutige Fälle ehrlich begrenzt.

Kein Push, Merge nach `main`, Release oder produktive Jobausführung. Historische Reviews und fremde laufende Arbeit unverändert lassen.

**Jetzt mit P0 beginnen. Nach erfolgreichem Test- und unabhängigem Reviewgate jeweils autonom die nächste Phase umsetzen.**
