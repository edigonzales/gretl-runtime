# P0: unabhängiger Reviewrequest

Auftrag task-finalizers, Spec 1.0. Auftragsbasis und feste Phasebase:
`9faf347f739e836976dd566bd31a65fc4ff91ac4`. Kandidat erst nach Commit im Handoff.
Vollständiger Umfang W01–W04 gemäss Plan §6/§7.1 und Reviewprompt.
Keine P1–P5-Produktfähigkeit als geliefert bewerten.

Prüfe den gesamten P0-Diff, WORK_QUEUE und Adapter. Lies alle drei normativen
Dateien selbst. Baselinebelege unter implementer_evidence im Handoff:
Plan-/Failure-/Budgetsemantik, bestehende vollständige Include-Semantik,
erwartete UNKNOWN_OPTION-Red-Probe des noch fehlenden finalizedBy.
Führe den gezielten Mavenbefehl sowie Adaptertests selbst aus; frische XMLs
mit positiven Fallzahlen und ohne Fehler/Skips prüfen. Der Review verwendet
einen eigenen Maven-Cache und unveränderte getrackte Kandidatenbytes.

Entwickle und führe eine eigene neue relevante Gegenprobe aus, mit eigener
Hypothese, Erwartung und Assertions. Keine Implementierervorlage als
unabhängigen Gegenfall übernehmen. Dateien ausschliesslich extern ablegen.
Originalbericht und JSON nach Plan §9.4, vollständige W01–W04, tatsächliche
Session-/Modellidentität und echte Prüfbelege liefern. Fehlende Fähigkeiten
offen ausweisen. Keine Produktfixes oder Publikation.
