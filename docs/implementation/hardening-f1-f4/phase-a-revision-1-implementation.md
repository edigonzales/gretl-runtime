# Phase A – Wiederaufnahme nach Spezifikation 1.1

Ausgangsstand: bc044302829ede67ead27038bfdf2be90d0b0063, Branch bei Übernahme main,
Arbeitsbaum bis auf die vom Nutzer neu bereitgestellte spec.md sauber. Arbeitsbranch:
codex/hardening-v11. Die Spezifikation wurde unverändert übernommen (SHA-256 siehe
acceptance-matrix.md). Die historischen Baseline-/Implementierungsberichte bleiben erhalten.

F1: geeignete Implementierung beibehalten (ALREADY_FIXED). Native Linux-Proben
unter UID/GID 65534 prüfen verweigerte Schreibrechte, Eigentümer/Gruppe, Symlinkroots,
private Run-/Diagnosebereiche und Nicht-POSIX-Fallback mit dem JDK-ZIP-Dateisystem.
Kein neuer produktiver Dateisystemprovider und kein GRETL-IMAGE-Lauf.

R1/F2: reine Abfragen sind nun nebenwirkungsfrei, global und lokal. Token und Budget
lesen nach Schliessung nur einen eigenen terminalen Snapshot. Bindende Altzugriffe
scheitern mit CONTEXT_CLOSED ohne Uhr-/Run-/Interruptänderung. Snapshotbildung folgt
Freigabe und Statusentscheidung. Task und Invocation verwenden denselben Arbiter.
Fehlergraphen werden erst nach Anbindung der Closefehler eingefroren.

R2/F2: interne Completion-/Outcome-Records tragen eine typisierte CancelReason und
validieren CANCELLED genau gegen deren Anwesenheit. Report cancelReason und
Terminalattribut cancel_reason stammen vom eigenen Outcome. reason für Skip/NOT_RUN
bleibt erhalten; frühere Erfolgsoutcomes übernehmen keine spätere Abbruchursache.

R3/F3: die exklusive Run-Leaf und Umgebung müssen vor run.started bereitstehen.
RunStartException wird im gemeinsamen Einstieg zu Code 2; kein Runabschluss,
kein Job-Cleanup und kein fremder Retentionbesitz. Eine package-private Run-ID-Factory
ermöglicht deterministische Kollisionstests, ohne neuen CLI-Schalter. Nach Runbeginn
bleiben Task-/Invocation-I/O-Fehler kontrolliert; Tasksetup hat phase/failurePhase
task-setup. Cleanup ohne Dateibedarf funktioniert nach verlorenem Workspace.

Orakeländerung: SetupFailureTest.failedRunWorkspaceStillRunsNonFileCleanupsAndFinishes
war bezüglich F3-01 falsch. Er wurde in failedRunWorkspaceIsAStartErrorWithoutCleanup
mit Startfehlertyp und null Run-/Cleanupereignissen korrigiert. Der legitime Cleanupfall
wird separat nach erfolgreichem run.started mit beschädigtem Workspace getestet.
Das historische Review-FAIL bezüglich zustandswirksamer reiner Budgetabfragen wird
nicht gelöscht. Version 1.1 F2-02/F2-08/TC-F2-17 entscheidet ausdrücklich für reine
Beobachtung; die damalige gegenteilige Sollinterpretation wird nicht implementiert.

Selbst ausgeführte rote Gegenproben: scoped-control-red.log (10 Fälle, 8 Assertionfehler)
und start-boundary-red.log (Kollision kehrte fälschlich als JobOutcome zurück).
R2 ist durch den bereitgestellten historischen Prozessreport ohne cancelReason belegt;
die neuen Report-/Prozessprüfungen verlangen die tatsächlichen JSON-Felder.
Die frühen Komponentenlogs 1/3 enthalten behobene Compilefehler und sind keine Gates.
Rohbelege liegen unter /tmp/gretl-hardening-v11-evidence, grosse Artefakte nicht in Git.

Die JDK-/Maven-Versionen, Kandidaten-SHA, Standardprüfung, Artefaktaudit, Linuxprobe
und unabhängiges Review werden nach tatsächlicher Ausführung separat dokumentiert.
F4 wurde in dieser Phase nicht begonnen.
