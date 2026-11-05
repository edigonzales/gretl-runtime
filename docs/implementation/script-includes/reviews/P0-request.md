# Reviewrequest script-includes / 1.3 / P0

Phasebase und assignment_base_sha: `0bfc8f0fccf43989d751d4f5490202311d200628`.
Toolingbasis: `54bb123070fc7bd81aea58b4e1191beecf568cbf`; `MAIN_WITH_ACCEPTED_TOOLING`.

Prüfe den vollständigen P0-Umfang nach EXECUTION_PLAN §6 und OPENCODE_REVIEW_PROMPT §3: Basis/Isolation, Testausnahme, tatsächliche kleine Baseline, echte autonome CLI-/Modell-/Sessionidentität, eigene Testausführung und neue eigene Baselinegegenprobe. Keine P1-Implementierung behaupten oder verlangen. P0 hat keine vorgezogene Teilmenge fertiger S-/C-Produktverträge; requirements_checked nennt `P0`.

Führe selbst mindestens die zwei gezielten GroovyLoaderTest-Fälle, GroovyProjectLoaderTest und ApplyPartialCoverageTest über `-pl gretl-groovy -am` mit eigenem explizitem Maven-Cache aus. Prüfe frische XMLs je Zielmodul; failIfNoSpecifiedTests=false gilt nur für unbeteiligte Reactorabhängigkeiten. Erfinde und führe zusätzlich eine neue fachliche Baselinegegenprobe aus, in externem Probeverzeichnis oder separater Kopie desselben SHA. Keine Änderungen getrackter Dateien. Keine Vollbuilds/alte 81-Kriterien-Plattform.

Kandidat, Digest, frischer detached Worktree und externe Evidenz-/Reportziele werden erst nach Commit übergeben. Keine vorgefüllte Ergebnisdatei. Schreibe Originalbericht review.md und verdict.json mit den Identitätsfeldern aus Plan §9.4, eigenen Prüfbefehlen/Belegen/Gegenprobe und fehlenden Pflichtnachweisen. Prüfe den getrackten Zustand vorher/nachher. Nur ein tatsächlich belegtes Urteil kann P1 freigeben.
