# Arbeitsanweisungen für diesen GRETL-Tooling-Auftrag

Dieses Verzeichnis enthält den ausdrücklich beauftragten neuen Auftrag für Sprachkern, LSP und Modeler. Es ersetzt keine Root-Repositoryregeln. Lies zuerst Root-AGENTS, `docs/README.md` und dann EXECUTION_PLAN, ARCHITECTURE, TEST_MATRIX und den zur Rolle passenden Prompt.

- Codex implementiert P0–P8 in Reihenfolge; jede nächste Phase erfordert unabhängiges gültiges OpenCode-/DeepSeek-PASS auf dem aktuellen Produkt-SHA.
- Ein Repository: `edigonzales/gretl-runtime`. Keine aktive Dependency auf andere GRETL-Varianten. Kontrollierte lizenzierte Quellübernahme mit Provenienz erlaubt.
- Keine Ausführung von Job-/Plugin-/Toplevelcode beim Analysieren oder Modellieren. Groovy bleibt Source of Truth; ein Java-Core entscheidet Semantik und Edits.
- Zuerst Kern, danach LSP/Texteditor, zuletzt React-Flow-Modeler. Keine leeren zukünftigen Module oder vorgezogene UIarbeit.
- Rootpflichtgates erhalten; gezielte neue Tests und ab P5 echte installierte GUItests ergänzen sie. Bestehende Pythonharnesses nicht aufgrund fremder Vorlagenregeln entfernen.
- Fremde Änderungen, Benutzerprofile und Credentials schützen. Keine Veröffentlichung/produktiven Jobs.
- Nur STATUS.md ist aktive Phasenübersicht; Implementierungsberichte kurz, Originalreviews unverändert. Grosse Artefakte nicht unkontrolliert ins Git aufnehmen.
- Nach PASS autonom fortsetzen. FAIL korrigieren und erneut unabhängig prüfen; nicht autonom behebbares BLOCKED konkret melden. Keine Selbstfreigabe oder gefälschten Belege.

Musskriterien nicht still abschwächen. Die Kriterienregistry ist aus EXECUTION_PLAN abgeleitet und muss mit dessen Tabellen übereinstimmen. Schema-/Registry-/Specsänderung macht den Kandidaten neu prüfpflichtig.
