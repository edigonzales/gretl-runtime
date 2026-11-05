# P2: unabhängiger Reviewrequest

Auftrag task-finalizers, Spec 1.0. Auftragsbasis
`9faf347f739e836976dd566bd31a65fc4ff91ac4`, feste P2-Phasebase
`bbd37bdfb73e902a0a2b073e7a8f929b1cce3fd9`. Kandidat im externen Handoff.

Vollständige Kriterienmenge F04–F18, F22–F23, F30 gemäss Plan §7.1.
Umfang: wirkliche Runner-/Context-/Operationspipeline, Fehlerdrain,
Aktivierungsgrenze, lokale und gemeinsame Budgets, Ressourcen-/Resultrechte,
Task-/Invocationereignisse und Bestandskompatibilität. Öffentliche Groovy- und
Reportserialisierung folgen in P3; IDE folgt in P4. Frühere Planverträge erhalten.

Lies den gesamten Phasendiff, Plan, Reviewprompt und WORK_QUEUE selbst.
Führe den Handoff-Mavenbefehl direkt aus; prüfe alle benannten frischen
Surefireklassen mit positiven Testzahlen und ohne Fehler/Skips. Er kompiliert
auch Groovy und Jobruntime und prüft betroffene Logging-/Reportregressionen.
Die P1-Runner-Nichtunterstützungsprobe wurde durch reale Finalizerfälle ersetzt.

Entwickle und führe eine neue eigene relevante Fehler-/Abbruchgegenprobe aus,
die auch innere Invocation oder Ressourcenschluss umfasst. Hypothese, Input,
Erwartung und Assertions selbst entwerfen; Implementierertests nicht bloss
umbenennen. Eigene Dateien extern ablegen, getrackte Kandidatenbytes erhalten.
Liefere Originalbericht und JSON gemäss §9.4 mit der vollständigen
Kriterienmenge, eigenen echten Tests und Gegenprobe sowie Findings oder
fehlenden Pflichten. Keine Produktfixes oder Veröffentlichung.


Korrektur zu Kandidat `1dc9c8f5febf272db4bdee4511511408dc960f71`:
P2-01 des ersten Reviews verlangt die wirksame Prüfung vor RUNNING und eine
NOT_RUN-Behandlung ohne Aktivierung. Der neue Kandidat ergänzt diese Grenze
sowie Regressionen für Cancellation, Finish, neue Interrupts und lokale
Timeouts vor Start. Der vollständige P2-Umfang und die Phasebase bleiben
unverändert. Prüfe die Korrektur und die früheren Phasenanforderungen selbst;
führe zusätzlich eine eigene neue Gegenprobe aus. Frühere Belege bezeichnen
weiterhin ausschliesslich ihren damaligen SHA. Die im Handoff referenzierte
erste Reviewablage enthält Originalfinding, eigene Completionprobe und
Startgrenzenreproduktion des Reviewers, keine Freigabe des neuen Kandidaten.
