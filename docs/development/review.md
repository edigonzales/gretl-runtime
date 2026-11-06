# Unabhängige Kandidatenprüfung

`review.py` startet OpenCode 2.x in einem neuen detached Worktree auf einem
lokalen Commit. Der Request und die normativen Quellen liegen ausserhalb des
Produktcheckouts. OpenCode verwendet die vorhandene direkte DeepSeek-Anmeldung.

```sh
python3 tools/review/review.py /absolute/external/request.json \
  --opencode /absolute/path/to/opencode-cli
```

Der JSON-Request nennt `assignment_id`, `spec_version`, `phase`, den erwarteten
DeepSeek-`checkpoint`, die drei vollständigen SHAs `assignment_base_sha`,
`phase_base_sha`, `candidate_sha` sowie die fälligen `criteria` als Liste
eindeutiger IDs. `requirement_files` enthält die relativen Namen in
`requirements_directory`. `requirements_digest` ist der SHA-256 über das
Manifest: nach Dateiname sortierte Zeilen mit SHA-256, zwei Leerzeichen,
relativem Dateinamen und LF.

`review_worktree` und `evidence_directory` sind neue, voneinander getrennte
absolute Pfade. Optionale externe `read_paths` erlauben Zugriff auf ergänzende
Belege. Jeder Eintrag in `required_checks` benennt `id`, das auszuführende
`argv` und `expected_exit` (0). Maven-Aufrufe brauchen zusätzlich `reports`
mit Worktree-relativen Selektoren für die erwarteten Surefire-/Failsafe-XMLs.
Für Prüfungen mit Textausgabe können `output_matches` reguläre Ausdrücke
angeben, etwa eine positive Testzahl und null Skips in einer Node-Teststatistik.

Der Reviewer führt diese Befehle selbst aus und entwickelt eine eigene
Gegenprobe. Der Launcher sichert Originalbericht, Urteil, Sessionexport,
Prozessdaten und `validation.json` extern. Er prüft die tatsächliche Sitzung,
Identitäten, vollständige Kriterien, frische Rohberichte und die ausgeführte
Gegenprobe. Exit 0 des OpenCode-Prozesses genügt nicht. Die lokale OpenCode-Policy
beschränkt die angebotenen Aktionen; sie ist keine Betriebssystem-Sandbox.
