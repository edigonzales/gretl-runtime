Independent OpenCode / direct DeepSeek review. Automatically copied, no editorial changes.

GRETL_REVIEW_JSON_BEGIN
{
  "schemaVersion": 1,
  "phase": "P3",
  "phaseBaseSha": "27aa194f47412a9ac6f0a69dbc55f5a22adf12d6",
  "candidateSha": "79a1c0678a9b382d99789a651d96f193ec9b26fd",
  "verdict": "PASS",
  "scopeComplete": true,
  "requirements": [
    {
      "id": "P0-01",
      "status": "PASS",
      "evidence": [
        {
          "path": "docs/maintenance/cleanup/inventory.json",
          "observation": "Das eingefrorene P0-Inventar bindet 191 Ausgangsdokumente, 294 Produktionsdateien, 44 Operation-IDs und die Deskriptor-Schemas 1/2/3 an den Ausgangscommit; P3 ergänzt die Restinventur als eigene ARCHIVE/P3-Klassifikation. Der aktuelle Check meldet keine unklassifizierte aktuelle Dokumentation.",
          "commandOrReference": "python3 scripts/cleanup_inventory.py --check; target/cleanup-review-evidence/invariants/stdout.log: contentReviewedDocuments 191, operationIdsUnchanged 44, descriptorSchemas [1,2,3]"
        },
        {
          "path": "docs/maintenance/cleanup/content-assessment.json",
          "observation": "Jede Baseline-Datei besitzt eine quellengebundene Begründung mit Baseline-SHA-256, Verbrauchern, Zitatzeilen oder Binärbeleg; der Checker weist fehlende, doppelte, unbekannte oder nicht mit dem Git-Blob übereinstimmende Einträge zurück.",
          "commandOrReference": "python3 scripts/cleanup_inventory.py --check; scripts/cleanup_inventory.py: content_assessments"
        }
      ]
    },
    {
      "id": "P0-02",
      "status": "PASS",
      "evidence": [
        {
          "path": "target/cleanup-review-evidence/provider.json",
          "observation": "Der qualifizierte Live-Lauf belegt opencode v2.2.5, das Modell deepseek/deepseek-flash, die API-ID deepseek-flash, den direkten Provider deepseek, den Endpoint https://api.deepseek.com und die Zuordnung DeepSeek-V4.1-Flash-1017.",
          "commandOrReference": "target/cleanup-review-evidence/provider.json; target/cleanup-review-evidence/smoke-provenance/session-provenance.json"
        },
        {
          "path": "target/cleanup-review-evidence/preflight.json",
          "observation": "Der nicht als Phasenreview zählende Smoke-Test ist mit QUALIFIED, einer echten Session-ID und der wirksamen Verweigerung von edit und shell belegt; die Sessionprovenienz bestätigt Modell und Endpoint.",
          "commandOrReference": "target/cleanup-review-evidence/preflight.json; target/cleanup-review-evidence/smoke/stdout.log; target/cleanup-review-evidence/deny-edit/stdout.log; target/cleanup-review-evidence/deny-shell/stdout.log"
        }
      ]
    },
    {
      "id": "P0-03",
      "status": "PASS",
      "evidence": [
        {
          "path": "tests/cleanup_review/test_gate.py",
          "observation": "Die unabhängige Python-Suite deckt den gebundenen PASS sowie falsche SHA/Phase, fehlende oder doppelte Kriterien, BLOCKED/FAIL, ungebundene Zwischentexte, Fehler-Events, ungültige JSONL/UTF-8, Provider-/Modellfehler, Worktree-Mutationen, Docker-/Buildfehler und P3-Kumulativpfade ab.",
          "commandOrReference": "python3 -m unittest discover -s tests/cleanup_review -v; target/cleanup-review-evidence/python/stderr.log: Ran 73 tests, OK"
        },
        {
          "path": "scripts/cleanup_review.py",
          "observation": "parse_stream und final_review_text akzeptieren nur einen vollständigen JSONL-Strom mit genau einer finalen stop-Nachricht und einem Markerpaar; evaluate_review prüft Schema, exakte P3-Kriterienmenge, Evidenzpfade und die Abdeckung aller kumulativen Änderungen.",
          "commandOrReference": "scripts/cleanup_review.py: parse_stream, final_review_text, parse_final_review, evaluate_review, changed_paths"
        }
      ]
    },
    {
      "id": "P0-04",
      "status": "PASS",
      "evidence": [
        {
          "path": "target/cleanup-review-evidence/source.json",
          "observation": "Der unabhängige Gate-Lauf ist an HEAD 79a1c0678a9b382d99789a651d96f193ec9b26fd gebunden, der Arbeitsbaum ist im aufgezeichneten Lauf sauber, und alle Gates liefen im detached Review-Worktree statt im Implementierungscheckout.",
          "commandOrReference": "target/cleanup-review-evidence/source.json; target/cleanup-review-evidence/manifest.json"
        },
        {
          "path": "target/cleanup-review-evidence/standard/reports.json",
          "observation": "Das Standard-Gate zählt 674 Tests mit 0 Failures, 0 Errors und 0 Skipped; der zugehörige SHA-gebundene Artefaktaudit bestätigt dieselbe Kandidaten-SHA und sourceDirty false.",
          "commandOrReference": "target/cleanup-review-evidence/standard/reports.json; target/cleanup-review-evidence/standard-audit/stdout.log"
        },
        {
          "path": "target/cleanup-review-evidence/full/reports.json",
          "observation": "Das Voll-Gate zählt 730 Tests mit 0 Failures, 0 Errors und 0 Skipped einschließlich GeoTools-Probe; der Vollaudit bestätigt dieselbe Kandidaten-SHA und sourceDirty false.",
          "commandOrReference": "target/cleanup-review-evidence/full/reports.json; target/cleanup-review-evidence/full-audit/stdout.log"
        }
      ]
    },
    {
      "id": "P0-05",
      "status": "PASS",
      "evidence": [
        {
          "path": "target/cleanup-review-evidence/preflight.json",
          "observation": "Die Live-Negativprobe beobachtet den tatsächlichen Shellaufruf printf DENY_TEST als Fehler mit Permission denied und keine verbotene Datei; der echte V2-Permission-Evaluator liefert für edit und shell jeweils effect=deny.",
          "commandOrReference": "target/cleanup-review-evidence/preflight.json; target/cleanup-review-evidence/smoke/stdout.log; target/cleanup-review-evidence/deny-edit/stdout.log; target/cleanup-review-evidence/deny-shell/stdout.log"
        },
        {
          "path": "scripts/cleanup_review.py",
          "observation": "Die Reviewkonfiguration verweigert grundsätzlich alle Aktionen und erlaubt nur Lesen, Suchen und exakte Git-Lesebefehle; Secretredaktion, Worktree-Integritätsprüfung, Prozessgruppen-Timeout und Aufräumen sind implementiert und durch Negativtests belegt.",
          "commandOrReference": "scripts/cleanup_review.py: review_config, redact, verify_worktree_unchanged, run_command; tests/cleanup_review/test_gate.py"
        }
      ]
    },
    {
      "id": "P0-06",
      "status": "PASS",
      "evidence": [
        {
          "path": "target/cleanup-review-evidence/standard/reports.json",
          "observation": "Das unabhängige Standard-Gate endet mit 674 Tests ohne Failures, Errors oder Skips; der Build ist an den Kandidaten gebunden und der Artefaktaudit ist grün.",
          "commandOrReference": "target/cleanup-review-evidence/standard/reports.json; target/cleanup-review-evidence/standard/stdout.log: BUILD SUCCESS"
        },
        {
          "path": "target/cleanup-review-evidence/full/reports.json",
          "observation": "Das unabhängige Voll-Gate endet mit 730 Tests ohne Failures, Errors oder Skips einschließlich GeoTools- und Backing-Service-Tests; der Vollaudit ist an dieselbe Kandidaten-SHA gebunden.",
          "commandOrReference": "target/cleanup-review-evidence/full/reports.json; target/cleanup-review-evidence/full/stdout.log: BUILD SUCCESS"
        },
        {
          "path": "docs/maintenance/cleanup/reviews/P0-76b92e1-review.json",
          "observation": "Der frühere unabhängige P0-Review ist als PASS an candidateSha 76b92e156d8155057122eb7a8c2fd30e3dc22758 gebunden; der P3-Review beurteilt P0-01 bis P0-06 erneut anhand der aktuellen Belege.",
          "commandOrReference": "docs/maintenance/cleanup/reviews/P0-76b92e1-review.json; docs/maintenance/cleanup/reviews/P0-76b92e1-review.md"
        }
      ]
    },
    {
      "id": "P1-01",
      "status": "PASS",
      "evidence": [
        {
          "path": "README.md",
          "observation": "Der Root-README trennt Git-Checkout, entpackte Scriptdistribution und Job-JAR ausdrücklich und enthält für alle drei Kontexte ausführbare Befehle; die cleanup-example-Blöcke wurden im unabhängigen Lauf mit exitCode 0 ausgeführt.",
          "commandOrReference": "README.md; target/cleanup-review-evidence/docs-runtime/result.json: checkout, distribution, jar jeweils exitCode 0"
        },
        {
          "path": "AGENTS.md",
          "observation": "AGENTS.md enthält nur aktuelle Repository-Regeln, beide vollständigen Maven-Gates, JDK 25, Dockerpflicht, Evidenzsicherung und das Verbot automatischer Veröffentlichung; abgeschlossene Spezifikationen und Agentenprompts werden als historisch eingeordnet.",
          "commandOrReference": "AGENTS.md; docs/README.md"
        },
        {
          "path": "docs/README.md",
          "observation": "Die aktive Navigationsseite führt den Einstieg, Build, Tests, Module, Paketierung, Subprojekte, Taskreferenz, Runtimeverträge, Backlog und historische Quellen als getrennte aktuelle Ziele.",
          "commandOrReference": "docs/README.md; target/cleanup-review-evidence/docs-audit/stdout.log: STRICT checked 185 broken 0"
        }
      ]
    },
    {
      "id": "P1-02",
      "status": "PASS",
      "evidence": [
        {
          "path": "docs/implementation/build.md",
          "observation": "Die Buildseite nennt JDK 25, Maven 3.10.0, beide vollständigen Gates und den SHA-gebundenen Artefaktaudit; dauerhafte alte Testzahlen und eine M0-M7-Erzählung als aktueller Status fehlen.",
          "commandOrReference": "docs/implementation/build.md; target/cleanup-review-evidence/standard/reports.json; target/cleanup-review-evidence/full/reports.json"
        },
        {
          "path": "docs/implementation/testing.md",
          "observation": "Die Teststrategie beschreibt den implementierten Standard-Reaktor und das Dienstprofil, konkrete aktuelle Testklassen, die Aussagegrenzen von IMAGE und Coverage sowie die P3-Beispielquellen und Driftprüfung.",
          "commandOrReference": "docs/implementation/testing.md; target/cleanup-review-evidence/standard/reports.json"
        },
        {
          "path": "docs/implementation/module-boundaries.md",
          "observation": "Die Modulseite beschreibt die vier Taskmodule mit 13, 3, 27 und 1 Operation, die aufgelösten Enforcer-Grenzen und die erhaltene Standarddistribution ohne historische Phasenaussage.",
          "commandOrReference": "docs/implementation/module-boundaries.md; target/cleanup-review-evidence/invariants/stdout.log"
        },
        {
          "path": "docs/implementation/job-packaging.md",
          "observation": "Der Paketierungsvertrag beschreibt Schemas 1/2/3, die explizite Modul- und Funktionsauswahl, die Compilerisolation und die tatsächlichen JAR-Nachweise, ohne alte Phasenberichte als aktuellen Status zu verwenden.",
          "commandOrReference": "docs/implementation/job-packaging.md; target/cleanup-review-evidence/standard/reports.json"
        },
        {
          "path": "docs/implementation/project-subprojects/README.md",
          "observation": "Die Subprojektanleitung beschreibt den implementierten gemeinsamen Taskgraphen, qualifizierte Ziele, settings.gretl und Schema-3-JARs statt einer noch ausstehenden Phase.",
          "commandOrReference": "docs/implementation/project-subprojects/README.md; target/cleanup-review-evidence/docs-runtime/result.json: subsource und subchecks exitCode 0"
        },
        {
          "path": "examples/project-subprojects/README.md",
          "observation": "Das aktuelle Beispiel-README dokumentiert den tatsächlich ausführbaren gemeinsamen Projektlauf; die Pfade und Kommandos entsprechen den im unabhängigen Lauf erfolgreichen :source:run- und :checks:local:run-Aufrufen.",
          "commandOrReference": "target/cleanup-review-evidence/docs-runtime/result.json; target/cleanup-review-evidence/docs-runtime-command/stdout.log"
        }
      ]
    },
    {
      "id": "P1-03",
      "status": "PASS",
      "evidence": [
        {
          "path": "website/getting-started.qmd",
          "observation": "Die Seite enthält keine Gradle-Plugin-Syntax und keinen ./gradlew-Start; sie beschreibt den Maven-Build und führt den Script- sowie JAR-Quickstart aus dem Distributionsroot aus.",
          "commandOrReference": "website/getting-started.qmd; target/cleanup-review-evidence/docs-runtime/result.json: website und website-jar exitCode 0"
        },
        {
          "path": "website/features.qmd",
          "observation": "Die Funktionsseite nennt die realen 44 Operationen, sequenzielle Taskgraphen, Script-/JAR-Jobs und Diagnosefunktionen; Scheduling sowie Publisher, XtfToGpkg und Native Image werden als nicht geliefert getrennt.",
          "commandOrReference": "website/features.qmd; target/cleanup-review-evidence/docs-runtime/result.json: quarto exitCode 0"
        },
        {
          "path": "website/docs.qmd",
          "observation": "Die Dokumentationsseite enthält keine Platzhalter oder erfundene Versionsnavigation; sie verlinkt die vorhandenen aktuellen Dokumentationsziele und nennt die 44 Operationenkapitel.",
          "commandOrReference": "website/docs.qmd; target/cleanup-review-evidence/docs-audit/stdout.log: STRICT checked 185 broken 0"
        },
        {
          "path": "website/index.qmd",
          "observation": "Die Startseite bewirbt keine Control Plane mehr und verwendet ein ausführbares action-Beispiel; der unabhängige landing-Lauf endet mit exitCode 0.",
          "commandOrReference": "website/index.qmd; target/cleanup-review-evidence/docs-runtime/result.json: landing exitCode 0"
        }
      ]
    },
    {
      "id": "P1-04",
      "status": "PASS",
      "evidence": [
        {
          "path": "target/cleanup-review-evidence/docs-audit/stdout.log",
          "observation": "Der strikte Dokumentationsaudit prüft 185 aktive lokale Ziele und meldet 0 gebrochene Links; die Prüfung umfasst Markdown-, Quarto- und AsciiDoc-Ziele, Includes und Anker.",
          "commandOrReference": "python3 scripts/verify_cleanup_docs.py; target/cleanup-review-evidence/docs-audit/stdout.log"
        },
        {
          "path": "docs/README.md",
          "observation": "Alle von der Navigationsseite verlinkten lokalen aktuellen Ziele existieren im Kandidaten; die historischen Quellen sind als solche getrennt verlinkt.",
          "commandOrReference": "docs/README.md; target/cleanup-review-evidence/docs-audit/stdout.log"
        },
        {
          "path": "target/cleanup-review-evidence/docs-runtime/result.json",
          "observation": "Die unabhängige Dokumentationsprüfung führt Referenzgenerierung, Quarto-Render, Checkout-, Distributions- und JAR-Kontexte aus; die Referenzgenerierung erzeugt 44 Tabellen, und Quarto rendert die Website erfolgreich.",
          "commandOrReference": "target/cleanup-review-evidence/docs-runtime/result.json; target/cleanup-review-evidence/docs-runtime-command/stdout.log"
        }
      ]
    },
    {
      "id": "P1-05",
      "status": "PASS",
      "evidence": [
        {
          "path": "README.md",
          "observation": "Der README trennt die realen 44 Operationen und vorhandenen Job-/Diagnosefunktionen ausdrücklich von Publisher, XtfToGpkg, Native Image, Scheduler und Control Plane als nicht gelieferten Funktionen.",
          "commandOrReference": "README.md: Abschnitt Dokumentation und Grenzen"
        },
        {
          "path": "website/features.qmd",
          "observation": "Die Website trennt die implementierten Operationen und Jobfunktionen von Scheduling, Publisher, XtfToGpkg und Native Image; keine fehlende Funktion wird als vorhanden dargestellt.",
          "commandOrReference": "website/features.qmd; docs/backlog/publisher-and-native.md"
        },
        {
          "path": "target/cleanup-review-evidence/invariants/stdout.log",
          "observation": "Der Invariantencheck bestätigt 44 unveränderte Operation-IDs, die Deskriptor-Schemas 1, 2 und 3 sowie 294 byteidentische Produktionsdateien; der P3-Diff enthält keinen API-, DSL- oder Schema-Bruch.",
          "commandOrReference": "target/cleanup-review-evidence/invariants/stdout.log; git diff --name-status --find-renames 27aa194f47412a9ac6f0a69dbc55f5a22adf12d6..79a1c0678a9b382d99789a651d96f193ec9b26fd"
        }
      ]
    },
    {
      "id": "P1-06",
      "status": "PASS",
      "evidence": [
        {
          "path": "target/cleanup-review-evidence/standard/reports.json",
          "observation": "Das unabhängige Standard-Gate ist an die Kandidaten-SHA gebunden und meldet 674 Tests mit 0 Failures, 0 Errors und 0 Skipped; der zugehörige Audit ist grün.",
          "commandOrReference": "target/cleanup-review-evidence/standard/reports.json; target/cleanup-review-evidence/standard-audit/stdout.log"
        },
        {
          "path": "target/cleanup-review-evidence/full/reports.json",
          "observation": "Das unabhängige Voll-Gate meldet 730 Tests mit 0 Failures, 0 Errors und 0 Skipped; die tatsächlichen Distributions-, GeoTools- und Backing-Service-Prüfungen sind enthalten.",
          "commandOrReference": "target/cleanup-review-evidence/full/reports.json; target/cleanup-review-evidence/full-audit/stdout.log"
        },
        {
          "path": "target/cleanup-review-evidence/docs-runtime/result.json",
          "observation": "Der Dokumentationslauf ist an candidateSha 79a1c0678a9b382d99789a651d96f193ec9b26fd gebunden und führt die Quickstarts in getrennten Checkout-, Distributions-, JAR-, Website- und Subprojektkontexten tatsächlich aus.",
          "commandOrReference": "target/cleanup-review-evidence/docs-runtime/result.json; target/cleanup-review-evidence/docs-runtime-command/stdout.log"
        },
        {
          "path": "docs/maintenance/cleanup/reviews/P1-3a55b85-review.json",
          "observation": "Der frühere unabhängige P1-Review ist als PASS an candidateSha 3a55b85a278f1cb0678a1d9cc6c3d2678e52b246 gebunden; der P3-Review beurteilt P1-01 bis P1-06 erneut anhand der aktuellen Dateien und Gates.",
          "commandOrReference": "docs/maintenance/cleanup/reviews/P1-3a55b85-review.json; docs/maintenance/cleanup/reviews/P1-3a55b85-review.md"
        }
      ]
    },
    {
      "id": "P2-01",
      "status": "PASS",
      "evidence": [
        {
          "path": "docs/maintenance/cleanup/cleanup-actions.json",
          "observation": "Die Aktionsliste enthält 195 eindeutige Baseline-Entscheidungen mit previousPath, Aktion, Begründung, Quellhash, Bytezahl und Replacement; der History-Check zählt 76 KEEP, 6 REWRITE, 10 MOVE und 103 DELETE.",
          "commandOrReference": "python3 scripts/cleanup_history_check.py; target/cleanup-review-evidence/history-check/stdout.log"
        },
        {
          "path": "docs/maintenance/cleanup/inventory.json",
          "observation": "Das Inventar bindet die 191 Ausgangsdokumente an ihren Baseline-SHA und führt die aktuelle Dokumentation über einen expliziten, additiven Index; keine aktuelle Dokumentationsdatei bleibt unklassifiziert.",
          "commandOrReference": "target/cleanup-review-evidence/invariants/stdout.log: currentDocumentationCovered 104, contentReviewedDocuments 191"
        },
        {
          "path": "docs/maintenance/cleanup/content-assessment.json",
          "observation": "Die Inhaltsbewertung enthält für jede Baseline-Datei eine substantive Begründung, Quellhash, Zitatzeilen oder Binärbeleg und die geplante Phase; der Checker lehnt fehlende, doppelte oder nicht quellengebundene Einträge ab.",
          "commandOrReference": "python3 scripts/cleanup_inventory.py --check; tests/cleanup_review/test_gate.py::test_content_assessment_is_complete_and_source_bound"
        }
      ]
    },
    {
      "id": "P2-02",
      "status": "PASS",
      "evidence": [
        {
          "path": "docs/README.md",
          "observation": "Die aktive Navigation verweist nicht mehr auf docs/spec als Pflichtlektüre und führt ausschliesslich aktuelle Anleitungen sowie getrennte technische Verträge, Backlog und historische Quellen.",
          "commandOrReference": "docs/README.md; target/cleanup-review-evidence/docs-audit/stdout.log"
        },
        {
          "path": "docs/history/README.md",
          "observation": "Die abgeschlossenen Prompt- und Iterationsserien sind nur über einen ausdrücklich historischen Index mit Git-SHA-Permalinks erreichbar; der Text stellt klar, dass sie keine Arbeitsaufträge und keine aktuelle Releasefreigabe sind.",
          "commandOrReference": "docs/history/README.md; docs/maintenance/cleanup/cleanup-actions.json"
        },
        {
          "path": "AGENTS.md",
          "observation": "Die aktiven Repository-Regeln stehen ausschliesslich in AGENTS.md; docs/spec/AGENTS.md und die abgeschlossenen Startprompts sind aus dem aktiven Baum entfernt und nur historisch belegt.",
          "commandOrReference": "AGENTS.md; target/cleanup-review-evidence/history-check/stdout.log"
        }
      ]
    },
    {
      "id": "P2-03",
      "status": "PASS",
      "evidence": [
        {
          "path": "docs/architecture/runtime-contracts.md",
          "observation": "Die aktive Vertragsseite dokumentiert öffentliche Java-SPI, DSL-Auswertungszeitpunkte, Runner-/Fehler-/Cleanup-Semantik, Logging, Secretredaktion, Pfad-/Ownership-Regeln, Lokalisierung und Plangraphen.",
          "commandOrReference": "docs/architecture/runtime-contracts.md; target/cleanup-review-evidence/docs-runtime/result.json"
        },
        {
          "path": "docs/backlog/publisher-and-native.md",
          "observation": "Publisher, XtfToGpkg und Native Image bleiben als ausdrücklich nicht implementierter Backlog mit Entscheidung, Status, Liefergrenze, Feldern und TC-P01 bis TC-P09 erhalten; die Seite behauptet keine vorhandene Operation.",
          "commandOrReference": "docs/backlog/publisher-and-native.md; docs/history/README.md"
        },
        {
          "path": "docs/history/README.md",
          "observation": "Operationsbaseline, Java-Typumzüge, Lizenzen, Szenarioprovenienz, Fixtureherkunft, lokale Modelle und der Linux-CI-Beleg bleiben mit unveränderten SHA-256-Werten auffindbar; die historischen Kandidaten-SHAs sind nachvollziehbar.",
          "commandOrReference": "target/cleanup-review-evidence/history-check/stdout.log: unchangedMovedFiles; docs/history/README.md"
        },
        {
          "path": "gretl-integration-tests/src/test/java/ch/so/agi/gretl/it/OperationMetadataTest.java",
          "observation": "Der Test liest die Operationsbaseline und die Java-Typ-Move-Map aus docs/history/contracts; die Änderung ist auf die zwei erlaubten Pfadzeichenketten begrenzt und wird durch den P2-Orakel-Check bytegenau abgesichert.",
          "commandOrReference": "scripts/cleanup_review.py: ORACLE_MOVES, path_only_oracle_test_move; tests/cleanup_review/test_gate.py::test_p2_oracle_paths_allow_no_assertion_or_other_source_changes"
        }
      ]
    },
    {
      "id": "P2-04",
      "status": "PASS",
      "evidence": [
        {
          "path": "target/cleanup-review-evidence/history-check/stdout.log",
          "observation": "Der maschinelle Vorher-/Nachher-Vergleich zählt vorher 191 Dokumentationsdateien mit 3.636.867 Bytes und nachher 101 Dateien mit 2.196.076 Bytes; der historische/Entscheidungsbestand sinkt von 108 auf 12 Dateien und von 3.027.122 auf 542.041 Bytes.",
          "commandOrReference": "python3 scripts/cleanup_history_check.py; target/cleanup-review-evidence/history-check/stdout.log"
        },
        {
          "path": "docs/maintenance/cleanup/cleanup-actions.json",
          "observation": "Die Vereinfachung ist nicht als 1:1-Verschiebung umgesetzt: 103 Dateien werden begründet gelöscht, 10 bytegleiche Orakel/Lizenzen/Provenienzen gezielt verschoben und 6 aktive Seiten an ihre kanonischen Ersatzseiten angepasst.",
          "commandOrReference": "docs/maintenance/cleanup/cleanup-actions.json; target/cleanup-review-evidence/history-check/stdout.log"
        },
        {
          "path": "docs/maintenance/cleanup/README.md",
          "observation": "Die Maintenance-Anleitung erklärt die Messung getrennt nach aktivem Produktbestand, historischem/Entscheidungsbestand und den vorgeschriebenen Inventar-/Nachweisdateien; der wachsende Maintenance-Anteil ist damit begründet.",
          "commandOrReference": "docs/maintenance/cleanup/README.md; target/cleanup-review-evidence/history-check/stdout.log"
        }
      ]
    },
    {
      "id": "P2-05",
      "status": "PASS",
      "evidence": [
        {
          "path": "target/cleanup-review-evidence/docs-audit/stdout.log",
          "observation": "Der strikte Dokumentationsaudit prüft 185 aktive lokale Ziele und meldet 0 gebrochene Links; historische Ausnahmen sind ausschliesslich an den bytegenau gehashten Spezifikationstext gebunden.",
          "commandOrReference": "python3 scripts/verify_cleanup_docs.py; target/cleanup-review-evidence/docs-audit/stdout.log"
        },
        {
          "path": "target/cleanup-review-evidence/docs-runtime/result.json",
          "observation": "Der unabhängige Dokumentationslauf führt Checkout-, Distributions-, JAR-, Website-, Landing-, Subprojekt-, Referenz-, Quarto- und Thoth-Kontexte aus; alle Kommandos enden mit exitCode 0 und die Referenzgenerierung erzeugt 44 Tabellen.",
          "commandOrReference": "target/cleanup-review-evidence/docs-runtime/result.json; target/cleanup-review-evidence/docs-runtime-command/stdout.log"
        },
        {
          "path": "target/cleanup-review-evidence/standard/reports.json",
          "observation": "Der Standardlauf enthält die Distributions-, Referenz- und Job-JAR-Prüfungen einschließlich DistributionIT, ReferenceExamplesTest, AsciiDocTaskRendererTest und ChecksumManifestTest mit 674 Tests ohne Failures, Errors oder Skips.",
          "commandOrReference": "target/cleanup-review-evidence/standard/reports.json; target/cleanup-review-evidence/standard/command.json"
        },
        {
          "path": "target/cleanup-review-evidence/full/reports.json",
          "observation": "Der Vollauf ergänzt GeoTools- und Backing-Service-Tests und meldet 730 Tests mit 0 Failures, Errors und Skips; die Verteilung bleibt konsistent mit den Standardberichten.",
          "commandOrReference": "target/cleanup-review-evidence/full/reports.json; target/cleanup-review-evidence/full/command.json"
        }
      ]
    },
    {
      "id": "P2-06",
      "status": "PASS",
      "evidence": [
        {
          "path": "target/cleanup-review-evidence/standard/reports.json",
          "observation": "Der unabhängig im Review-Worktree ausgeführte Standardbefehl ./mvnw -B clean verify ist an die Kandidaten-SHA gebunden und meldet 674 Tests, 0 Failures, 0 Errors und 0 Skips; command.json bestätigt return_code 0.",
          "commandOrReference": "target/cleanup-review-evidence/standard/reports.json; target/cleanup-review-evidence/standard/command.json; target/cleanup-review-evidence/source.json"
        },
        {
          "path": "target/cleanup-review-evidence/full/reports.json",
          "observation": "Der unabhängige Vollauf ./mvnw -B -Pgeotools-probe,backing-services clean verify meldet 730 Tests, 0 Failures, 0 Errors und 0 Skips; GeoTools und die echten PostgreSQL/PostGIS-, FTP- und S3-Tests sind enthalten.",
          "commandOrReference": "target/cleanup-review-evidence/full/reports.json; target/cleanup-review-evidence/full/command.json"
        },
        {
          "path": "docs/maintenance/cleanup/reviews/P2-37f8cde-review.json",
          "observation": "Der frühere unabhängige P2-Review ist als PASS an candidateSha 37f8cde018952c46fee7be4bed228a52ef1725b5 gebunden; der P3-Review beurteilt P2-01 bis P2-06 erneut und prüft die gelöschten Inhalte gegen die aktuellen Ersatzseiten.",
          "commandOrReference": "docs/maintenance/cleanup/reviews/P2-37f8cde-review.json; docs/maintenance/cleanup/reviews/P2-37f8cde-review.md"
        },
        {
          "path": "target/cleanup-review-evidence/python/stderr.log",
          "observation": "Die unabhängige Python-Suite läuft mit 73 Tests und endet mit OK; sie deckt die gebundenen PASS-Fälle, alle relevanten Negativfälle und die P3-Kumulativprüfung ab.",
          "commandOrReference": "target/cleanup-review-evidence/python/stderr.log; tests/cleanup_review/test_gate.py"
        }
      ]
    },
    {
      "id": "P3-01",
      "status": "PASS",
      "evidence": [
        {
          "path": "gretl-integration-tests/src/test/java/ch/so/agi/gretl/it/RuntimeSafetyLocalJobIT.java",
          "observation": "Die Testklasse ist fachlich als RuntimeSafetyLocalJobIT benannt; der unabhängige Final-Check vergleicht sie bytegenau mit der alten HardeningLocalJobIT-Datei plus ausschließlich den exakten Namenssubstitutionen und bestätigt damit unveränderte Testfälle und Assertions.",
          "commandOrReference": "python3 scripts/cleanup_final_check.py; target/cleanup-review-evidence/final-check/stdout.log: exactRuntimeSafetyRename true"
        },
        {
          "path": "gretl-integration-tests/src/test/resources/jobs/runtime-safety/job.groovy",
          "observation": "Die Fixture liegt unter jobs/runtime-safety und enthält dieselben Berechtigungs-, Timeout-, Cancellation-, Setupfehler-, Ursachen- und Cleanup-Prüfungen; nur die fachlichen Namen und Meldungen wurden exakt ersetzt.",
          "commandOrReference": "target/cleanup-review-evidence/final-check/stdout.log: exactSafetyFixtureRenames true; target/cleanup-review-evidence/standard/reports.json"
        },
        {
          "path": "gretl-integration-tests/src/test/resources/jobs/runtime-safety/job-package.json",
          "observation": "Der Deskriptor heisst runtime-safety statt hardening; der Final-Check bestätigt die bytegenaue Ersetzung ohne weitere Fixture-Änderung, und das Standard- sowie das Voll-Gate führen RuntimeSafetyLocalJobIT erfolgreich aus.",
          "commandOrReference": "target/cleanup-review-evidence/final-check/stdout.log; target/cleanup-review-evidence/standard/reports.json; target/cleanup-review-evidence/full/reports.json"
        }
      ]
    },
    {
      "id": "P3-02",
      "status": "PASS",
      "evidence": [
        {
          "path": "gretl-dist/src/main/dist/examples/ili-validator/models/GretlValidationExample.ili",
          "observation": "Das INTERLIS-2.3-Testmodell heisst durchgängig GretlValidationExample; MODEL- und END-Deklaration, Distribution, XTF-Header, Klassen-Tags und Java-Tests verwenden denselben fachlichen Namen.",
          "commandOrReference": "gretl-dist/src/main/dist/examples/ili-validator/models/GretlValidationExample.ili; target/cleanup-review-evidence/final-check/stdout.log"
        },
        {
          "path": "gretl-dist/src/main/dist/examples/ili-validator/data/valid.xtf",
          "observation": "Der gültige Transfer verwendet den neuen Modellnamen in HEADERSECTION und DATASECTION; der ungültige Transfer wurde atomar mit demselben Modell- und Klassen-Tag-Namen angepasst.",
          "commandOrReference": "gretl-dist/src/main/dist/examples/ili-validator/data/valid.xtf; gretl-dist/src/main/dist/examples/ili-validator/data/invalid.xtf"
        },
        {
          "path": "gretl-dist/src/test/java/ch/so/agi/gretl/dist/DistributionIT.java",
          "observation": "DistributionIT kopiert das neue Modell unter dem neuen Pfad und prüft im echten Distributionslauf sowohl die erfolgreiche gültige Validierung als auch den erwarteten Fehlerfall mit VALIDATION_FAILED.",
          "commandOrReference": "gretl-dist/src/test/java/ch/so/agi/gretl/dist/DistributionIT.java; target/cleanup-review-evidence/standard/reports.json"
        },
        {
          "path": "gretl-ilivalidator/src/test/java/ch/so/agi/gretl/tasks/ilivalidator/IliValidatorOperationTest.java",
          "observation": "Der Modultest schreibt das neue Modell, validiert einen gültigen Transfer erfolgreich und erwartet für den ungültigen Transfer eine IliValidatorTaskException mit konkreter Fehlerdiagnose; die Assertionzahl bleibt unverändert.",
          "commandOrReference": "gretl-ilivalidator/src/test/java/ch/so/agi/gretl/tasks/ilivalidator/IliValidatorOperationTest.java; target/cleanup-review-evidence/final-check/stdout.log: modifiedTestAssertions"
        },
        {
          "path": "target/cleanup-review-evidence/final-check/stdout.log",
          "observation": "Der Final-Check vergleicht alle 115 INTERLIS-/XTF-Dateien der ursprünglichen Baseline und bestätigt, dass ausschließlich das synthetische Distributionsmodell und seine Referenzen den verlangten neuen Namen erhalten haben; Kundenmodelle und Einheiten bleiben unverändert.",
          "commandOrReference": "target/cleanup-review-evidence/final-check/stdout.log: modelAndXtfFilesCompared 115"
        }
      ]
    },
    {
      "id": "P3-03",
      "status": "PASS",
      "evidence": [
        {
          "path": "gretl-geotools-probe/pom.xml",
          "observation": "Die POM-Bezeichnungen und Kommentare sind auf GeoTools-Kompatibilität neutralisiert; die Probe bleibt als eigenes optionales Profil erhalten und wird nicht in die Standarddistribution aufgenommen.",
          "commandOrReference": "gretl-geotools-probe/pom.xml; pom.xml: geotools-probe"
        },
        {
          "path": "gretl-geotools-probe/src/test/java/ch/so/agi/gretl/runtime/geotools/GeoToolsProbeTest.java",
          "observation": "Die Testdatei verwendet keine m0-points-, gretl_m0_points- oder M0/B-Marker mehr; CRS-, DBF/SHX- und Negativklassenpfad-Assertions bleiben erhalten, und der Voll-Lauf führt die Probe erfolgreich aus.",
          "commandOrReference": "gretl-geotools-probe/src/test/java/ch/so/agi/gretl/runtime/geotools/GeoToolsProbeTest.java; target/cleanup-review-evidence/full/reports.json"
        },
        {
          "path": "target/cleanup-review-evidence/full/reports.json",
          "observation": "Das Voll-Gate enthält GeoToolsProbeTest und meldet 730 Tests mit 0 Failures, 0 Errors und 0 Skipped; der unabhängige Vollaudit bestätigt dieselbe Kandidaten-SHA.",
          "commandOrReference": "target/cleanup-review-evidence/full/reports.json; target/cleanup-review-evidence/full-audit/stdout.log"
        }
      ]
    },
    {
      "id": "P3-04",
      "status": "PASS",
      "evidence": [
        {
          "path": "gretl-integration-tests/src/test/java/ch/so/agi/gretl/it/ExampleConsistencyTest.java",
          "observation": "ExampleConsistencyTest prüft die 16 vorhandenen Beispielpaare auf Vorhandensein, vergleicht alle gemeinsamen Dateien bytegenau und lässt eine absichtlich beschädigte oder fehlende Scriptkopie scheitern; nur die kontextbedingte project-pipeline-README ist begründet ausgenommen.",
          "commandOrReference": "gretl-integration-tests/src/test/java/ch/so/agi/gretl/it/ExampleConsistencyTest.java; target/cleanup-review-evidence/standard/reports.json"
        },
        {
          "path": "examples/download-extract/job.groovy",
          "observation": "Die kanonische Checkout-Kopie und die Distributionskopie enthalten denselben optionalen Overwrite-Kommentar und unterscheiden sich nicht mehr; der Drift-Test und der unabhängige Beispiel-Lauf bestätigen die Übereinstimmung.",
          "commandOrReference": "examples/download-extract/job.groovy; gretl-dist/src/main/dist/examples/download-extract/job.groovy; target/cleanup-review-evidence/docs-runtime/result.json"
        },
        {
          "path": "examples/project-pipeline/README.md",
          "observation": "Die Checkout- und Distributions-README unterscheiden sich nur durch den dokumentierten Ausführungskontext: Repository-Root mit gretl-dist/target/dist versus entpackter Distributionsroot mit bin/gretl; die Job-, Settings-, Ressourcen- und Paketdateien sind bytegleich.",
          "commandOrReference": "examples/project-pipeline/README.md; gretl-dist/src/main/dist/examples/project-pipeline/README.md; docs/implementation/testing.md"
        },
        {
          "path": "target/cleanup-review-evidence/docs-runtime/result.json",
          "observation": "Der unabhängige Dokumentationslauf führt Download/Extract mit lokalem HTTP-Testserver und die Projektpipeline jeweils aus Checkout und Distribution aus; alle vier Kontexte enden mit exitCode 0, und die extrahierten beziehungsweise erzeugten Bytes stimmen mit den kanonischen Quellen überein.",
          "commandOrReference": "target/cleanup-review-evidence/docs-runtime/result.json: checkout-download-extract, distribution-download-extract, checkout-project-pipeline, distribution-project-pipeline"
        },
        {
          "path": "docs/implementation/testing.md",
          "observation": "Die Teststrategie dokumentiert die kanonische examples-Quelle, die gezielten Distributionskopien, die einzige kontextbedingte README-Ausnahme und die tatsächlich ausgeführten Drift- und Beispielprüfungen.",
          "commandOrReference": "docs/implementation/testing.md: Beispielquellen und Drift"
        }
      ]
    },
    {
      "id": "P3-05",
      "status": "PASS",
      "evidence": [
        {
          "path": "docs/maintenance/cleanup/rest-inventory.json",
          "observation": "Die Restinventur erfasst 959 aktuelle Dateien mit SHA-256, Bytezahl und Klassifikation: 293 produktive Java-Dateien plus eine Beispiel-Extension, 169 Java-Tests, 209 Testressourcen, 115 INTERLIS-/XTF-Dateien, 64 Dokumentationsdateien, 13 Website-Dateien, 52 veröffentlichte Beispiele und 21 Build-/Lokalisierungsdateien.",
          "commandOrReference": "docs/maintenance/cleanup/rest-inventory.json; target/cleanup-review-evidence/final-check/stdout.log: filesByKind"
        },
        {
          "path": "scripts/cleanup_final_check.py",
          "observation": "Der Final-Check validiert die Restinventur gegen den gesamten Git-Pfadbestand, klassifiziert offene Marker und verweigert unklassifizierte historische Phasen, unbekannte M0-Dateinamen und neue nicht inventarisierte Dateien.",
          "commandOrReference": "python3 scripts/cleanup_final_check.py; target/cleanup-review-evidence/final-check/stdout.log: status PASS"
        },
        {
          "path": "tests/cleanup_review/test_final_check.py",
          "observation": "Die Negativtests decken nummerierte Phasen mit Leerzeichen, Bindestrich und Unterstrich, unbekannte M0-Dateinamen, Hardening-Marker, Binärdateien, veraltete Inventare und unklassifizierte neue Dateien ab; die Suite läuft mit 73 Tests und OK.",
          "commandOrReference": "python3 -m unittest discover -s tests/cleanup_review -v; target/cleanup-review-evidence/python/stderr.log"
        },
        {
          "path": "target/cleanup-review-evidence/invariants/stdout.log",
          "observation": "Der Invariantencheck bestätigt 44 unveränderte Operation-IDs, die Deskriptor-Schemas 1, 2 und 3 sowie 294 unveränderte Produktionsdateien; der P3-Diff enthält keine versehentliche Public-API-, Schema- oder Operation-Änderung.",
          "commandOrReference": "target/cleanup-review-evidence/invariants/stdout.log; git diff --name-status --find-renames 27aa194f47412a9ac6f0a69dbc55f5a22adf12d6..79a1c0678a9b382d99789a651d96f193ec9b26fd"
        },
        {
          "path": "target/cleanup-review-evidence/final-check/stdout.log",
          "observation": "Der Final-Check bestätigt fünf historische Kommentarzeilen in vier Dateien als ausdrücklich klassifizierte Ausnahmen, 294 unveränderte Produktionsdateien, 115 verglichene Modelle/XTFs und unveränderte Assertionzahlen der übrigen bearbeiteten Java-Tests.",
          "commandOrReference": "target/cleanup-review-evidence/final-check/stdout.log; docs/maintenance/cleanup/README.md"
        }
      ]
    },
    {
      "id": "P3-06",
      "status": "PASS",
      "evidence": [
        {
          "path": "target/cleanup-review-evidence/docs-runtime/result.json",
          "observation": "Die vollständige Referenzgenerierung, Quarto-Render, Checkout-, Distributions-, JAR-, Website-, Landing-, Subprojekt- und P3-Beispielkontexte sind an die Kandidaten-SHA gebunden und enden mit exitCode 0; 44 Referenztabellen werden erzeugt.",
          "commandOrReference": "target/cleanup-review-evidence/docs-runtime/result.json; target/cleanup-review-evidence/docs-runtime-command/stdout.log"
        },
        {
          "path": "target/cleanup-review-evidence/docs-audit/stdout.log",
          "observation": "Der strikte Link-/Include-/Quarto-Audit prüft 185 aktive lokale Ziele und meldet 0 gebrochene Links; die P3-spezifische Modellumbenennung erzeugt keinen neuen aktiven Linkfehler.",
          "commandOrReference": "python3 scripts/verify_cleanup_docs.py; target/cleanup-review-evidence/docs-audit/stdout.log"
        },
        {
          "path": "target/cleanup-review-evidence/standard/reports.json",
          "observation": "Das unabhängige Standard-Gate ist an die Kandidaten-SHA gebunden und meldet 674 Tests mit 0 Failures, 0 Errors und 0 Skipped; der SHA-gebundene Standardaudit bestätigt sourceDirty false und dieselben Zahlen.",
          "commandOrReference": "target/cleanup-review-evidence/standard/reports.json; target/cleanup-review-evidence/standard-audit/stdout.log"
        },
        {
          "path": "target/cleanup-review-evidence/full/reports.json",
          "observation": "Das unabhängige Voll-Gate ist an dieselbe Kandidaten-SHA gebunden und meldet 730 Tests mit 0 Failures, 0 Errors und 0 Skipped einschließlich GeoTools- und Backing-Service-Tests; der Vollaudit ist grün.",
          "commandOrReference": "target/cleanup-review-evidence/full/reports.json; target/cleanup-review-evidence/full-audit/stdout.log"
        },
        {
          "path": "target/cleanup-review-evidence/python/stderr.log",
          "observation": "Die unabhängige Python-Suite läuft mit 73 Tests und endet mit OK; sie deckt die P3-Kumulativkriterien, die früheren Löschungspfade, die exakte SHA-Bindung und alle relevanten Negativfälle ab.",
          "commandOrReference": "target/cleanup-review-evidence/python/stderr.log; tests/cleanup_review/test_gate.py"
        },
        {
          "path": "docs/maintenance/cleanup/reviews/P2-37f8cde-review.md",
          "observation": "Der P3-Review liest die früheren P0-, P1- und P2-Reviewnachweise und beurteilt sämtliche P0-01 bis P3-06 erneut anhand der aktuellen Dateien, Diffs und unabhängigen Logs statt frühere PASS-Werte zu übernehmen.",
          "commandOrReference": "docs/maintenance/cleanup/reviews/P0-76b92e1-review.md; docs/maintenance/cleanup/reviews/P1-3a55b85-review.md; docs/maintenance/cleanup/reviews/P2-37f8cde-review.md"
        }
      ]
    }
  ],
  "independentInspection": {
    "diffReviewed": true,
    "newAndDeletedFilesReviewed": true,
    "criticalPaths": [
      ".gitignore",
      "AGENTS.md",
      "README.md",
      "docs/README.md",
      "docs/architecture/runtime-contracts.md",
      "docs/backlog/publisher-and-native.md",
      "docs/history/README.md",
      "docs/history/contracts/java-type-moves.json",
      "docs/history/contracts/operation-baseline.json",
      "docs/history/licenses/gretl-next-MIT.txt",
      "docs/history/licenses/sogis-gretl-MIT.txt",
      "docs/history/provenance/fixture-files.json",
      "docs/history/provenance/local-models.json",
      "docs/history/provenance/original-scenarios.json",
      "docs/history/provenance/package-fixtures.json",
      "docs/history/provenance/scenario-mapping.json",
      "docs/history/qualification/linux-ci.json",
      "docs/implementation/build.md",
      "docs/implementation/hardening-f1-f4/acceptance-matrix.md",
      "docs/implementation/hardening-f1-f4/baseline.md",
      "docs/implementation/hardening-f1-f4/evidence-index.json",
      "docs/implementation/hardening-f1-f4/gretl-runtime-startprompt-f1-f4-neu.md",
      "docs/implementation/hardening-f1-f4/phase-a-implementation.md",
      "docs/implementation/hardening-f1-f4/phase-a-review-291a001.md",
      "docs/implementation/hardening-f1-f4/phase-a-review-bc044302-historical.md",
      "docs/implementation/hardening-f1-f4/phase-a-review-revision-1.md",
      "docs/implementation/hardening-f1-f4/phase-a-revision-1-implementation.md",
      "docs/implementation/hardening-f1-f4/phase-b-implementation.md",
      "docs/implementation/hardening-f1-f4/phase-b-review-initial.md",
      "docs/implementation/hardening-f1-f4/phase-b-review-revision-1.md",
      "docs/implementation/hardening-f1-f4/qualification.md",
      "docs/implementation/hardening-f1-f4/spec.md",
      "docs/implementation/hardening-f1-f4/status.md",
      "docs/implementation/improvement-plan/baseline.md",
      "docs/implementation/improvement-plan/gretl-runtime-umsetzungsplan.md",
      "docs/implementation/improvement-plan/licenses/gretl-next-MIT.txt",
      "docs/implementation/improvement-plan/licenses/sogis-gretl-MIT.txt",
      "docs/implementation/improvement-plan/operation-inventory.json",
      "docs/implementation/improvement-plan/original-scenarios.json",
      "docs/implementation/improvement-plan/original-scenarios.md",
      "docs/implementation/improvement-plan/phase-1-implementation.md",
      "docs/implementation/improvement-plan/phase-1-review-initial.md",
      "docs/implementation/improvement-plan/phase-1-review-revision-1.md",
      "docs/implementation/improvement-plan/phase-1-review.md",
      "docs/implementation/improvement-plan/phase-1-revision-1-implementation.md",
      "docs/implementation/improvement-plan/phase-1-revision-2-implementation.md",
      "docs/implementation/improvement-plan/phase-2-check-log-index.json",
      "docs/implementation/improvement-plan/phase-2-coverage.json",
      "docs/implementation/improvement-plan/phase-2-fixture-origins.md",
      "docs/implementation/improvement-plan/phase-2-fixture-provenance.json",
      "docs/implementation/improvement-plan/phase-2-implementation.md",
      "docs/implementation/improvement-plan/phase-2-jar-executions.json",
      "docs/implementation/improvement-plan/phase-2-java-moves.json",
      "docs/implementation/improvement-plan/phase-2-local-models.json",
      "docs/implementation/improvement-plan/phase-2-operation-job-mapping.json",
      "docs/implementation/improvement-plan/phase-2-operation-job-mapping.md",
      "docs/implementation/improvement-plan/phase-2-previous-public-api.txt",
      "docs/implementation/improvement-plan/phase-2-previous-public-entrypoints.json",
      "docs/implementation/improvement-plan/phase-2-production-dependencies.json",
      "docs/implementation/improvement-plan/phase-2-review-initial.md",
      "docs/implementation/improvement-plan/phase-2-review.md",
      "docs/implementation/improvement-plan/phase-2-revision-1-coverage.json",
      "docs/implementation/improvement-plan/phase-2-revision-1-implementation.md",
      "docs/implementation/improvement-plan/phase-2-revision-1-jar-executions.json",
      "docs/implementation/improvement-plan/phase-2-revision-1-operation-job-mapping.json",
      "docs/implementation/improvement-plan/phase-2-revision-1-operation-job-mapping.md",
      "docs/implementation/improvement-plan/phase-2-scenario-mapping.json",
      "docs/implementation/improvement-plan/phase-2-scenario-mapping.md",
      "docs/implementation/improvement-plan/phase-3-check-log-index.json",
      "docs/implementation/improvement-plan/phase-3-ci-verification.json",
      "docs/implementation/improvement-plan/phase-3-coverage.json",
      "docs/implementation/improvement-plan/phase-3-fixture-provenance.json",
      "docs/implementation/improvement-plan/phase-3-implementation.md",
      "docs/implementation/improvement-plan/phase-3-jar-executions.json",
      "docs/implementation/improvement-plan/phase-3-operation-job-mapping.json",
      "docs/implementation/improvement-plan/phase-3-package-selections.json",
      "docs/implementation/improvement-plan/phase-3-review-coverage.json",
      "docs/implementation/improvement-plan/phase-3-review.md",
      "docs/implementation/improvement-plan/requirement-tests.md",
      "docs/implementation/job-artifact-tests.md",
      "docs/implementation/job-packaging.md",
      "docs/implementation/localization.md",
      "docs/implementation/m0-report.md",
      "docs/implementation/m1-report.md",
      "docs/implementation/m2-report.md",
      "docs/implementation/m3-report.md",
      "docs/implementation/m4-report.md",
      "docs/implementation/m5-report.md",
      "docs/implementation/m5-user-acceptance.md",
      "docs/implementation/m6-report.md",
      "docs/implementation/m7-report.md",
      "docs/implementation/module-boundaries.md",
      "docs/implementation/plan-graphs.md",
      "docs/implementation/project-subprojects/README.md",
      "docs/implementation/project-subprojects/gretl-runtime-subprojekte-spezifikation.md",
      "docs/implementation/project-subprojects/phase-0-baseline.md",
      "docs/implementation/project-subprojects/phase-0-evidence.json",
      "docs/implementation/project-subprojects/phase-0-review.md",
      "docs/implementation/project-subprojects/phase-1-evidence.json",
      "docs/implementation/project-subprojects/phase-1-implementation.md",
      "docs/implementation/project-subprojects/phase-1-review-attempt-01.md",
      "docs/implementation/project-subprojects/phase-1-review-attempt-02.md",
      "docs/implementation/project-subprojects/phase-1-review-attempt-03.md",
      "docs/implementation/project-subprojects/phase-1-review.md",
      "docs/implementation/project-subprojects/phase-2-evidence.json",
      "docs/implementation/project-subprojects/phase-2-implementation.md",
      "docs/implementation/project-subprojects/phase-2-review.md",
      "docs/implementation/project-subprojects/phase-3-evidence.json",
      "docs/implementation/project-subprojects/phase-3-implementation.md",
      "docs/implementation/project-subprojects/phase-3-review-attempt-01.md",
      "docs/implementation/project-subprojects/phase-3-review.md",
      "docs/implementation/testing.md",
      "docs/implementation/validator-functions.md",
      "docs/maintenance/cleanup/README.md",
      "docs/maintenance/cleanup/cleanup-actions.json",
      "docs/maintenance/cleanup/content-assessment.json",
      "docs/maintenance/cleanup/inventory.json",
      "docs/maintenance/cleanup/rest-inventory.json",
      "docs/maintenance/cleanup/reviews/P0-76b92e1-review.json",
      "docs/maintenance/cleanup/reviews/P0-76b92e1-review.md",
      "docs/maintenance/cleanup/reviews/P1-3a55b85-review.json",
      "docs/maintenance/cleanup/reviews/P1-3a55b85-review.md",
      "docs/maintenance/cleanup/reviews/P2-37f8cde-review.json",
      "docs/maintenance/cleanup/reviews/P2-37f8cde-review.md",
      "docs/maintenance/cleanup/spec-v2.md",
      "docs/migration/core-tasks.md",
      "docs/migration/phase-2-java-maven.md",
      "docs/reference/README.md",
      "docs/spec/01-zielbild-und-mvp.md",
      "docs/spec/02-publisher-bestandsanalyse.md",
      "docs/spec/03-runtime-vertraege.md",
      "docs/spec/04-job-dsl-und-custom-tasks.md",
      "docs/spec/05-publisher-zielvertrag.md",
      "docs/spec/06-betrieb-und-auslieferung.md",
      "docs/spec/07-plattform-und-versionen.md",
      "docs/spec/08-native-image-strategie.md",
      "docs/spec/09-tests-und-abnahme.md",
      "docs/spec/10-implementierungsplan-fuer-coding-agenten.md",
      "docs/spec/11-quellen-entscheidungen-und-roadmap.md",
      "docs/spec/12-job-paketierung.md",
      "docs/spec/13-einstieg-fuer-jobautoren.md",
      "docs/spec/14-java-api-vertraege.md",
      "docs/spec/15-runner-klassendesign.md",
      "docs/spec/16-logging-und-telemetrie.md",
      "docs/spec/17-findings-und-pruefprotokoll.md",
      "docs/spec/AGENTS.md",
      "docs/spec/README.md",
      "examples/file-size-extension/pom.xml",
      "examples/project-subprojects/README.md",
      "gretl-api/src/test/java/ch/so/agi/gretl/runtime/api/ApiIsolationTest.java",
      "gretl-dist/src/main/dist/README.md",
      "gretl-dist/src/main/dist/examples/download-extract/job.groovy",
      "gretl-dist/src/main/dist/examples/ili-validator/data/invalid.xtf",
      "gretl-dist/src/main/dist/examples/ili-validator/data/valid.xtf",
      "gretl-dist/src/main/dist/examples/ili-validator/models/GretlM0Fixture.ili",
      "gretl-dist/src/main/dist/examples/ili-validator/models/GretlValidationExample.ili",
      "gretl-dist/src/test/java/ch/so/agi/gretl/dist/DistributionIT.java",
      "gretl-geotools-probe/pom.xml",
      "gretl-geotools-probe/src/test/java/ch/so/agi/gretl/runtime/geotools/GeoToolsProbeTest.java",
      "gretl-groovy/src/test/java/ch/so/agi/gretl/runtime/groovy/GroovyHarness.java",
      "gretl-groovy/src/test/java/ch/so/agi/gretl/runtime/groovy/TestGroovyOperations.java",
      "gretl-i18n/src/main/resources/ch/so/agi/gretl/i18n/messages.properties",
      "gretl-i18n/src/main/resources/ch/so/agi/gretl/i18n/messages_de.properties",
      "gretl-ilivalidator/src/test/java/ch/so/agi/gretl/tasks/ilivalidator/IliValidatorOperationTest.java",
      "gretl-integration-tests/src/test/java/ch/so/agi/gretl/it/ExampleConsistencyTest.java",
      "gretl-integration-tests/src/test/java/ch/so/agi/gretl/it/HardeningLocalJobIT.java",
      "gretl-integration-tests/src/test/java/ch/so/agi/gretl/it/OperationMetadataTest.java",
      "gretl-integration-tests/src/test/java/ch/so/agi/gretl/it/ProjectScriptLocalJobIT.java",
      "gretl-integration-tests/src/test/java/ch/so/agi/gretl/it/RuntimeSafetyLocalJobIT.java",
      "gretl-integration-tests/src/test/resources/jobs/hardening/job-package.json",
      "gretl-integration-tests/src/test/resources/jobs/hardening/job.groovy",
      "gretl-integration-tests/src/test/resources/jobs/runtime-safety/job-package.json",
      "gretl-integration-tests/src/test/resources/jobs/runtime-safety/job.groovy",
      "gretl-runner/pom.xml",
      "gretl-runner/src/test/java/ch/so/agi/gretl/runtime/runner/RecordingEventSink.java",
      "gretl-runner/src/test/java/ch/so/agi/gretl/runtime/runner/RunnerTestSupport.java",
      "gretl-runner/src/test/java/ch/so/agi/gretl/runtime/runner/SequentialJobRunnerTest.java",
      "gretl-runner/src/test/java/ch/so/agi/gretl/runtime/runner/TestOperationProvider.java",
      "gretl-runner/src/test/java/ch/so/agi/gretl/runtime/runner/TestOperations.java",
      "pom.xml",
      "scripts/audit-job-evidence.py",
      "scripts/cleanup_doc_checks.py",
      "scripts/cleanup_final_check.py",
      "scripts/cleanup_history_check.py",
      "scripts/cleanup_inventory.py",
      "scripts/cleanup_review.py",
      "scripts/verify_cleanup_docs.py",
      "tests/cleanup_review/test_final_check.py",
      "tests/cleanup_review/test_gate.py",
      "website/README.md",
      "website/_quarto.yml",
      "website/docs.qmd",
      "website/features.qmd",
      "website/getting-started.qmd",
      "website/index.qmd"
    ],
    "regressionAssessment": "Der kumulative Diff seit 8eec0318 entfernt ausschliesslich historische Dokumentationsartefakte, verschiebt bytegleiche Orakel und ersetzt P3-Test-/Modell-/Beispielnamen; der P3-Diff ändert keine öffentliche Java-SPI, keine Operation-ID und kein Deskriptor-Schema. Die 294 Produktionsdateien sind byteidentisch, die 44 Operation-IDs und Schemas 1/2/3 bleiben bestätigt, die RuntimeSafety-Prüfung ist eine exakte Namenssubstitution, und die Modell-/XTF-Prüfung bestätigt ausschliesslich die verlangte synthetische Umbenennung. Risiken liegen nur in der bewusst erhaltenen Historie und in externen GitHub-main-Verweisen; die beiden vollständigen Gates sowie die P3-Beispiel-, Link-, Referenz- und Driftprüfungen sind grün.",
    "objectivesAssessment": "P3 ist inhaltlich erreicht: Hardening-Test und -Fixtures sind fachlich neutralisiert, ohne Assertionverlust; GretlM0Fixture ist durchgängig durch GretlValidationExample ersetzt und die echte positive sowie negative INTERLIS-Validierung läuft in Distribution und Modultest; die GeoTools-M0-Marker sind bereinigt, die optionale Probe bleibt aktiv; die Beispiel-Duplikate sind dokumentiert und durch einen echten Drift-Test sowie vier ausgeführte Checkout-/Distributionskontexte abgesichert. Die statische Restinventur klassifiziert den geforderten Produkt-/Test-/Modell-/Doku-/Website-/Beispielumfang, die Python-Gates werden zusätzlich durch Diff und 73 Unit-Tests geprüft, und die kumulativen P0-, P1- und P2-Ziele bleiben nach erneuter Prüfung erreichbar. Keine nicht begründete öffentliche API-, Schema- oder Operation-Änderung und kein ausgelassenes P3-Ziel wurde gefunden."
  },
  "findings": [],
  "openQuestions": [],
  "recommendation": "PASS für P3 auf 79a1c0678a9b382d99789a651d96f193ec9b26fd. Die unabhängigen Standard-/Vollgates sind mit 674/730 Tests ohne Failures, Errors oder Skips an den Kandidaten gebunden, alle P3-Zusatzprüfungen sind grün, und die kumulativen P0-P3-Ziele wurden anhand der tatsächlichen Diffs, Dateien und früheren Reviewnachweise erneut beurteilt. Ausserhalb des Phasenumfangs verbleiben nur bewusst erhaltene historische Quellenkommentare, externe GitHub-main-Verweise vor einer Veröffentlichung und die bei künftigen OpenCode-V2-Versionen erneut zu qualifizierende Permission-Reihenfolge."
}
GRETL_REVIEW_JSON_END
