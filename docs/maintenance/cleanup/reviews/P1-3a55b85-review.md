Independent OpenCode / direct DeepSeek review. Automatically copied, no editorial changes.

GRETL_REVIEW_JSON_BEGIN
{
  "schemaVersion": 1,
  "phase": "P1",
  "phaseBaseSha": "41df7a012fa9f158c395161359d80c44c55b9eb0",
  "candidateSha": "3a55b85a278f1cb0678a1d9cc6c3d2678e52b246",
  "verdict": "PASS",
  "scopeComplete": true,
  "requirements": [
    {
      "id": "P1-01",
      "status": "PASS",
      "evidence": [
        {
          "path": "README.md",
          "observation": "Der Root-README trennt Git-Checkout, entpackte Scriptdistribution und Job-JAR ausdrücklich. Die drei markierten cleanup-example-Blöcke enthalten die tatsächlich ausgeführten Checkout-, Distributions- und JAR-Befehle; docs-runtime/result.json meldet für checkout, distribution und jar exitCode 0 und jeweils denselben outputSha256 d98bdfec68681e8f9adf261ac5aaf05496edd810938ca49e027590130ebb7c2e.",
          "commandOrReference": "git diff 41df7a012fa9f158c395161359d80c44c55b9eb0..3a55b85a278f1cb0678a1d9cc6c3d2678e52b246 -- README.md; target/cleanup-review-evidence/docs-runtime/result.json"
        },
        {
          "path": "AGENTS.md",
          "observation": "AGENTS.md verweist als Einstieg auf docs/README.md, nennt beide vollständigen Maven-Gates mit JDK 25 und Docker, verbietet stille Skips und abgeschwächte Assertions und kennzeichnet abgeschlossene Spezifikationen und Agentenprompts als historisch. Es enthält keine Pflichtlektüre der alten docs/spec-Sollkapitel und keinen Widerspruch zum Root-README.",
          "commandOrReference": "git diff 41df7a012fa9f158c395161359d80c44c55b9eb0..3a55b85a278f1cb0678a1d9cc6c3d2678e52b246 -- AGENTS.md; docs/README.md"
        },
        {
          "path": "docs/README.md",
          "observation": "Die neue aktive Navigationsseite führt den Einstieg, Build, Tests, Module, Paketierung, Subprojekte, Taskreferenz und Snapshot-Veröffentlichung als aktuelle Dokumentationsziele und trennt davon die historischen docs/spec- und Implementierungsserien. Die Verweise sind mit den vorhandenen Zielen im Kandidaten konsistent.",
          "commandOrReference": "target/cleanup-review-evidence/docs-audit/stdout.log: mode STRICT, checked 159, broken 0"
        }
      ]
    },
    {
      "id": "P1-02",
      "status": "PASS",
      "evidence": [
        {
          "path": "docs/implementation/build.md",
          "observation": "Die Buildseite nennt JDK 25, den gepinnten Maven-Wrapper 3.10.0, beide vollständigen Gates und den SHA-gebundenen Artefaktaudit. Die frühere Aussage von 416 Tests sowie die M0-M7-Erzählung als aktueller Status sind entfernt; Testzahlen werden ausdrücklich an den getesteten SHA und gesicherte Surefire-/Failsafe-Berichte gebunden.",
          "commandOrReference": "git diff 41df7a012fa9f158c395161359d80c44c55b9eb0..3a55b85a278f1cb0678a1d9cc6c3d2678e52b246 -- docs/implementation/build.md; target/cleanup-review-evidence/standard/reports.json; target/cleanup-review-evidence/full/reports.json"
        },
        {
          "path": "docs/implementation/testing.md",
          "observation": "Die aktive Teststrategie beschreibt den Standard-Reaktor mit Unit-, Distributions- und lokalen Job-JAR-Tests sowie das Dienstprofil mit echten PostgreSQL/PostGIS-, FTP- und S3-Tests. Sie nennt konkrete Testklassen wie ProjectArtifactLocalJobIT, DistributionIT und ValidatorLocalJobIT und verwendet keine dauerhafte alte Gesamtzahl als Ist-Aussage.",
          "commandOrReference": "target/cleanup-review-evidence/standard/reports.json; target/cleanup-review-evidence/full/reports.json; docs/implementation/testing.md"
        },
        {
          "path": "docs/implementation/module-boundaries.md",
          "observation": "Die Modulseite beschreibt die vier Taskmodule mit 13, 3, 27 und 1 Operation sowie die aufgelösten Enforcer-Grenzen. Der frühere aktive Link auf die historische Java-/Maven-Migrationsseite ist entfernt; die Seite verweist stattdessen auf die Jobtest-Verträge und den Paketierungsvertrag.",
          "commandOrReference": "git diff 41df7a012fa9f158c395161359d80c44c55b9eb0..3a55b85a278f1cb0678a1d9cc6c3d2678e52b246 -- docs/implementation/module-boundaries.md; target/cleanup-review-evidence/invariants/stdout.log"
        },
        {
          "path": "docs/implementation/job-packaging.md",
          "observation": "Der Paketierungsvertrag ergänzt Schema 3 mit settingsFile und derselben expliziten Modulauswahl wie Schema 2, ohne die Schemas 1 und 2 zu entwerten. Die Nachweise verweisen auf SelectedPackageLocalJobIT, SelectedPackageArtifactIT und DistributionIT statt auf historische Phasenberichte.",
          "commandOrReference": "git diff 41df7a012fa9f158c395161359d80c44c55b9eb0..3a55b85a278f1cb0678a1d9cc6c3d2678e52b246 -- docs/implementation/job-packaging.md; target/cleanup-review-evidence/standard/reports.json"
        },
        {
          "path": "docs/implementation/project-subprojects/README.md",
          "observation": "Die Subprojektanleitung beschreibt jetzt den implementierten gemeinsamen Taskgraphen, die Settings-Allowlist, qualifizierte Ziele und Schema-3-JARs. Die frühere Aussage, der gemeinsame Projektlauf folge erst in Phase 2, ist entfernt; die Beispiele verweisen auf die tatsächlich vorhandenen Projektpfade.",
          "commandOrReference": "target/cleanup-review-evidence/docs-runtime/result.json: subsource und subchecks exitCode 0; gretl-cli/src/test/java/ch/so/agi/gretl/cli/CliProjectRunTest.java"
        },
        {
          "path": "examples/project-subprojects/README.md",
          "observation": "Das Beispiel-README ist von der Phase-1-Erzählung auf die aktuelle Ausführung mit settings.gretl und qualifizierten Zielen umgestellt. Die dokumentierten run-Aufrufe für :source:run und :checks:local:run wurden im unabhängigen Dokumentationslauf erfolgreich ausgeführt und lasen jeweils die eigene input.txt.",
          "commandOrReference": "target/cleanup-review-evidence/docs-runtime/result.json; target/cleanup-review-evidence/docs-runtime/subsource.stderr; target/cleanup-review-evidence/docs-runtime/subchecks.stderr"
        },
        {
          "path": "docs/implementation/job-artifact-tests.md",
          "observation": "Die Artefaktjobseite bezeichnet den IMAGE-Lauf als noch nicht qualifiziert statt als gegenwärtigen Auftrag und entfernt die Phase-1-/Phase-2-Bezeichnungen aus den Scriptregressionen. Die bestehenden JAR- und IMAGE-Vertragstests bleiben inhaltlich erhalten.",
          "commandOrReference": "git diff 41df7a012fa9f158c395161359d80c44c55b9eb0..3a55b85a278f1cb0678a1d9cc6c3d2678e52b246 -- docs/implementation/job-artifact-tests.md"
        },
        {
          "path": "docs/implementation/plan-graphs.md",
          "observation": "Die Plangraph-Seite dokumentiert die aktuelle Einschränkung korrekt: Ein Plan führt keine Task-Closures und keine Work-/Report-Verzeichnisse aus, vertrauenswürdiger Groovy-Top-Level-Code kann jedoch beim Laden der Definition laufen. Damit wird die frühere pauschale Aussage präzisiert, ohne das Graphverhalten zu ändern.",
          "commandOrReference": "git diff 41df7a012fa9f158c395161359d80c44c55b9eb0..3a55b85a278f1cb0678a1d9cc6c3d2678e52b246 -- docs/implementation/plan-graphs.md"
        }
      ]
    },
    {
      "id": "P1-03",
      "status": "PASS",
      "evidence": [
        {
          "path": "website/getting-started.qmd",
          "observation": "Die Seite enthält kein plugins-/tasks.register-/./gradlew-Beispiel mehr. Sie beschreibt den Maven-Build, wechselt in gretl-dist/target/dist/ und führt dort Script- und JAR-Quickstart aus; docs-runtime/result.json belegt für website und website-jar exitCode 0, erfolgreiche Reports und bytegleiche message.txt.",
          "commandOrReference": "target/cleanup-review-evidence/docs-runtime/result.json: website und website-jar; target/cleanup-review-evidence/docs-runtime/website.stderr; target/cleanup-review-evidence/docs-runtime/website-jar.stderr"
        },
        {
          "path": "website/features.qmd",
          "observation": "Lorem ipsum, die Behauptung einer Gradle-Pipeline-Engine und die nicht vorhandene Control Plane sind entfernt. Die Seite beschreibt die 44 Operationen, sequenzielle Taskgraphen, DuckDB, Script-/JAR-Jobs und Diagnosefunktionen; Publisher, XtfToGpkg und Native Image werden ausdrücklich als zukünftige Arbeiten getrennt.",
          "commandOrReference": "git diff 41df7a012fa9f158c395161359d80c44c55b9eb0..3a55b85a278f1cb0678a1d9cc6c3d2678e52b246 -- website/features.qmd; target/cleanup-review-evidence/docs-runtime/quarto.stderr"
        },
        {
          "path": "website/docs.qmd",
          "observation": "Die Platzhalter, die erfundene Versionsnavigation und der Control-Plane-Eintrag sind entfernt. Die Seite verlinkt auf den Checkout-README, Build, Taskreferenz, Thoth-Anleitung, Paketierung, Subprojekte und Teststrategie und nennt die 44 Operationenkapitel.",
          "commandOrReference": "git diff 41df7a012fa9f158c395161359d80c44c55b9eb0..3a55b85a278f1cb0678a1d9cc6c3d2678e52b246 -- website/docs.qmd; target/cleanup-review-evidence/docs-audit/stdout.log"
        },
        {
          "path": "website/index.qmd",
          "observation": "Die Startseite bewirbt keine Control Plane mehr und zeigt statt des alten DuckDbSqlExecutor-Beispiels eine ausführbare action('hello'). Der unabhängige landing-Lauf endete mit exitCode 0, und stdout/stderr enthalten Hello GRETL Runtime; die Taskreferenz- und JAR-Kacheln entsprechen dem aktuellen Produkt.",
          "commandOrReference": "target/cleanup-review-evidence/docs-runtime/result.json: landing exitCode 0; target/cleanup-review-evidence/docs-runtime/landing.stderr"
        },
        {
          "path": "website/_quarto.yml",
          "observation": "Titel und Footer heissen GRETL Runtime, die Domain next.gretl.app bleibt unverändert, und die Navigation verweist auf die vier vorhandenen qmd-Seiten. quarto render website/ erzeugte index.html, features.html, getting-started.html und docs.html ohne Fehler.",
          "commandOrReference": "target/cleanup-review-evidence/docs-runtime/quarto.stderr; target/cleanup-review-evidence/docs-runtime/rendered-website/index.html"
        }
      ]
    },
    {
      "id": "P1-04",
      "status": "PASS",
      "evidence": [
        {
          "path": "docs/README.md",
          "observation": "Die neue aktive Navigationsseite und alle von ihr verlinkten lokalen Ziele existieren. Der strikte Dokumentationsaudit prüft 159 lokale Ziele aus den aktiven Quellen und meldet 0 gebrochene Links; historische, inventarisierte Quellen werden für P1 bewusst separat geführt.",
          "commandOrReference": "target/cleanup-review-evidence/docs-audit/stdout.log: mode STRICT, checked 159, broken 0"
        },
        {
          "path": "docs/reference/README.md",
          "observation": "Die Referenzanleitung beschreibt die tatsächliche Generierung der 44 Tasktabellen und den Thoth-Biblios-Build. Der unabhängige reference-generate-Lauf erzeugte 44 Tabellen mit unveränderten Hashes, und der Thoth-Lauf endete mit Site generation complete.",
          "commandOrReference": "target/cleanup-review-evidence/docs-runtime/result.json: reference-generate und thoth; target/cleanup-review-evidence/docs-runtime/thoth.stdout"
        },
        {
          "path": "docs/reference/reference.adoc",
          "observation": "Die AsciiDoc-Hauptdatei bindet die generierten task-*.adoc-Dateien und die ausgeführten Beispieldateien ein. Die 44 Includes sind im strikten Dokumentationsaudit enthalten und der Renderinglauf fand alle referenzierten Bestandteile.",
          "commandOrReference": "target/cleanup-review-evidence/docs-runtime/result.json: generatedTables 44; target/cleanup-review-evidence/docs-audit/stdout.log"
        },
        {
          "path": "docs/spec/AGENTS.md",
          "observation": "Die ehemalige Agentenanweisung trägt jetzt einen unmissverständlichen historischen Warnhinweis mit Vorrang vor den nachfolgenden Anweisungen und verlinkt auf die aktuellen Repository-Regeln und die aktuelle Dokumentation. Der Hinweis ist nicht als aktueller Implementierungsauftrag formuliert.",
          "commandOrReference": "git diff 41df7a012fa9f158c395161359d80c44c55b9eb0..3a55b85a278f1cb0678a1d9cc6c3d2678e52b246 -- docs/spec/AGENTS.md docs/spec/README.md"
        },
        {
          "path": "website/_quarto.yml",
          "observation": "Alle vier Navigationsziele index.qmd, features.qmd, getting-started.qmd und docs.qmd existieren und wurden von Quarto gerendert. Die lokalen Website-Ziele sind damit gültig; die externen GitHub-Dokumentationslinks zeigen auf reale Pfade des Kandidaten.",
          "commandOrReference": "target/cleanup-review-evidence/docs-runtime/quarto.stderr; target/cleanup-review-evidence/docs-runtime/result.json: quarto exitCode 0"
        },
        {
          "path": "docs/maintenance/cleanup/inventory.json",
          "observation": "Der Inventarcheck meldet 198 abgedeckte aktuelle Dokumentationspfade, 7 eingeführte Dokumente und 191 inhaltlich geprüfte Baseline-Dokumente. Die neue docs/README.md sowie die P0-Reviewbelege sind klassifiziert; der Check bindet die Einträge an Git-Inhalt und Ausgangs-SHA.",
          "commandOrReference": "target/cleanup-review-evidence/invariants/stdout.log: currentDocumentationCovered 198, introducedDocumentation 7, contentReviewedDocuments 191"
        }
      ]
    },
    {
      "id": "P1-05",
      "status": "PASS",
      "evidence": [
        {
          "path": "README.md",
          "observation": "Der README nennt 44 Operationen und trennt davon ausdrücklich die nicht gelieferten Funktionen Publisher, XtfToGpkg und Native Image sowie den nicht vorhandenen Scheduler und die nicht vorhandene Control Plane. Die Runtime- und CLI-Verträge werden nur beschrieben, nicht geändert.",
          "commandOrReference": "git diff 41df7a012fa9f158c395161359d80c44c55b9eb0..3a55b85a278f1cb0678a1d9cc6c3d2678e52b246 -- README.md"
        },
        {
          "path": "website/features.qmd",
          "observation": "Die Webseite trennt die implementierten 44 Operationen und die vorhandenen Job-/Diagnosefunktionen von Scheduling sowie den zukünftigen Arbeiten Publisher, XtfToGpkg und Native Image. Damit wird keine fehlende Funktion als vorhanden dargestellt.",
          "commandOrReference": "git diff 41df7a012fa9f158c395161359d80c44c55b9eb0..3a55b85a278f1cb0678a1d9cc6c3d2678e52b246 -- website/features.qmd website/docs.qmd"
        },
        {
          "path": "docs/maintenance/cleanup/inventory.json",
          "observation": "Der unabhängige Invariantencheck bestätigt operationIdsUnchanged 44, descriptorSchemas 1, 2 und 3 sowie 294 unveränderte produktive Java-Dateien. Der P1-Diff enthält keine Java-, POM- oder sonstigen Produktvertragsänderungen.",
          "commandOrReference": "target/cleanup-review-evidence/invariants/stdout.log; git diff --name-status --find-renames 41df7a012fa9f158c395161359d80c44c55b9eb0..3a55b85a278f1cb0678a1d9cc6c3d2678e52b246"
        },
        {
          "path": "tests/cleanup_review/test_gate.py",
          "observation": "Die Teständerung ist rein additiv: drei neue Prüfungen für aktive/historische Dokumentquellen, die begrenzte Spec-Ausnahme und die Proof-Inventarbindung. Bestehende Gate-Assertions wurden nicht entfernt oder abgeschwächt; die unabhängige Suite läuft mit 63 Tests und OK.",
          "commandOrReference": "target/cleanup-review-evidence/python/stderr.log: Ran 63 tests, OK; git diff 41df7a012fa9f158c395161359d80c44c55b9eb0..3a55b85a278f1cb0678a1d9cc6c3d2678e52b246 -- tests/cleanup_review/test_gate.py"
        }
      ]
    },
    {
      "id": "P1-06",
      "status": "PASS",
      "evidence": [
        {
          "path": "target/cleanup-review-evidence/standard/reports.json",
          "observation": "Der unabhängige Standardlauf ./mvnw -B clean verify ist an den Kandidaten gebunden und meldet 672 Tests, 0 Failures, 0 Errors und 0 Skips. command.json bestätigt return_code 0 und stdoutSha256 44f9ea8d591662cfd1f91b5ed22cb5543669493303995486391b33176fa9f560; die kopierten XML-Berichte enthalten dieselben Suite-Zählungen.",
          "commandOrReference": "target/cleanup-review-evidence/standard/reports.json; target/cleanup-review-evidence/standard/command.json; target/cleanup-review-evidence/standard/stdout.log"
        },
        {
          "path": "target/cleanup-review-evidence/full/reports.json",
          "observation": "Der unabhängige Vollauf ./mvnw -B -Pgeotools-probe,backing-services clean verify meldet 728 Tests, 0 Failures, 0 Errors und 0 Skips. command.json bestätigt return_code 0 und stdoutSha256 51427a0acd2645fdfc85b9f87d083a69bdcab1e00a68f268c2dd3ae3a8ef2bbe; die GeoTools- und Diensttests sind in den kopierten XML-Berichten enthalten.",
          "commandOrReference": "target/cleanup-review-evidence/full/reports.json; target/cleanup-review-evidence/full/command.json; target/cleanup-review-evidence/full/stdout.log"
        },
        {
          "path": "target/cleanup-review-evidence/implementer-own-gates.json",
          "observation": "Die eigenen Kandidatengates von A sind separat gespeichert und als Implementer-A-Beleg ausgewiesen. Sie binden dieselbe Kandidaten-SHA, melden 672/728 Tests ohne Fehler oder Skips und beanspruchen ausdrücklich keine unabhängige Reviewbewertung; sie ersetzen die B-Gates nicht.",
          "commandOrReference": "target/cleanup-review-evidence/implementer-own-gates.json; target/cleanup-review-evidence/implementer-own-gates/own-gates-result.json; target/cleanup-review-evidence/implementer-own-gates/source.json"
        },
        {
          "path": "target/cleanup-review-evidence/docs-runtime/result.json",
          "observation": "Der unabhängige Dokumentationslauf ist an candidateSha 3a55b85a278f1cb0678a1d9cc6c3d2678e52b246 gebunden und meldet status PASS. Er führte checkout, distribution, jar, website, website-jar, landing, subsource, subchecks, reference-generate, quarto und den Thoth-Build aus; alle 14 Kommandos haben exitCode 0 und 44 Referenztabellen blieben bytegleich.",
          "commandOrReference": "target/cleanup-review-evidence/docs-runtime/result.json; target/cleanup-review-evidence/docs-runtime-command/stdout.log"
        },
        {
          "path": "target/cleanup-review-evidence/python/stderr.log",
          "observation": "Die unabhängige Python-Suite für das Reviewgate lief mit 63 Tests und endete mit OK. Sie deckt den gebundenen PASS, falsche SHAs und Phasen, fehlende Kriterien, BLOCKED/FAIL-Fälle, Provider-/Modellprüfungen, Worktree-Mutationen, Docker-/Buildfehler und die neuen Dokument-/Proof-Prüfungen ab.",
          "commandOrReference": "target/cleanup-review-evidence/python/stderr.log; tests/cleanup_review/test_gate.py"
        },
        {
          "path": "target/cleanup-review-evidence/provider.json",
          "observation": "Der qualifizierte unabhängige Lauf verwendete opencode v2.2.5 über den direkten DeepSeek-Provider, Modell deepseek/deepseek-flash, API-ID deepseek-flash und Endpoint https://api.deepseek.com. preflight.json bestätigt die wirksame Verweigerung von edit und shell; die Modellzuordnung ist als DeepSeek-V4.1-Flash-1017 belegt.",
          "commandOrReference": "target/cleanup-review-evidence/provider.json; target/cleanup-review-evidence/preflight.json"
        },
        {
          "path": "docs/maintenance/cleanup/reviews/P0-76b92e1-review.json",
          "observation": "Der P0-Nachweis vor der P1-Basis ist als PASS auf candidateSha 76b92e156d8155057122eb7a8c2fd30e3dc22758 gebunden. Der Reviewbeleg und die zugehörige Markdown-Datei sind die erwartete reine Nachweiskante; die P1-Basis 41df7a012fa9f158c395161359d80c44c55b9eb0 enthält damit die freigegebene Vorphase.",
          "commandOrReference": "docs/maintenance/cleanup/reviews/P0-76b92e1-review.json; docs/maintenance/cleanup/reviews/P0-76b92e1-review.md"
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
      "docs/implementation/build.md",
      "docs/implementation/job-artifact-tests.md",
      "docs/implementation/job-packaging.md",
      "docs/implementation/module-boundaries.md",
      "docs/implementation/plan-graphs.md",
      "docs/implementation/project-subprojects/README.md",
      "docs/maintenance/cleanup/README.md",
      "docs/maintenance/cleanup/inventory.json",
      "docs/reference/README.md",
      "docs/spec/AGENTS.md",
      "docs/spec/README.md",
      "examples/project-subprojects/README.md",
      "gretl-dist/src/main/dist/README.md",
      "scripts/cleanup_doc_checks.py",
      "scripts/cleanup_inventory.py",
      "scripts/cleanup_review.py",
      "scripts/verify_cleanup_docs.py",
      "tests/cleanup_review/test_gate.py",
      "website/README.md",
      "website/_quarto.yml",
      "website/docs.qmd",
      "website/features.qmd",
      "website/getting-started.qmd",
      "website/index.qmd"
    ],
    "regressionAssessment": "Der P1-Diff ändert ausschliesslich Dokumentation, Prüfskripte, Reviewtests und .gitignore; produktive Java-Dateien, Maven-Koordinaten und POMs bleiben unverändert. Der Invariantencheck bestätigt 44 Operations-IDs, Deskriptor-Schemas 1/2/3 und 294 unveränderte Produktionsdateien. Die Teständerung ist additiv, beide unabhängigen Maven-Gates sowie 63 Python-Tests sind grün, und der Dokumentationslauf führte die Quickstarts in getrennten Checkout-, Distributions- und JAR-Kontexten aus. Ein CLI-/DSL-/API-Regressionsrisiko aus diesem Diff ist daher nicht erkennbar.",
    "objectivesAssessment": "Die P1-Ziele sind inhaltlich erreicht: Root-README und AGENTS.md beschreiben den aktuellen Einstieg und die Gates; Build-, Test-, Modul-, Paket- und Subprojektseiten verwenden implementierte Verträge statt historischer Phasen- und Testzahlen; die vier Webseiten enthalten weder Lorem-ipsum noch Gradle- oder Control-Plane-Behauptungen und ihre Quickstarts wurden tatsächlich ausgeführt. Die aktive Navigation und die 44 generierten Referenztabellen sind gültig, Quarto und Thoth rendern erfolgreich, und fehlende Zukunftsarbeiten sind klar von den 44 realen Operationen getrennt. Keine gelöschten Dateien und keine ausgelassenen Diff-Ziele wurden gefunden.",
    "newAndDeletedFilesReviewed": true
  },
  "findings": [],
  "openQuestions": [],
  "recommendation": "PASS für P1 auf 3a55b85a278f1cb0678a1d9cc6c3d2678e52b246. Die einzige ungetrackte Datei ist erwartungsgemäss .opencode/opencode.jsonc und muss vor der Integritätsprüfung entfernt werden. Restrisiken ausserhalb dieser Phase: Die historischen docs/spec- und Implementierungsserien bleiben absichtlich bis P2 erhalten; Quarto und Thoth sind lokale externe Werkzeuge und der Website-Verweis auf GitHub-main wird erst nach einer Veröffentlichung des Kandidaten auf main allgemein erreichbar. Diese Punkte ändern die P1-Bewertung nicht."
}
GRETL_REVIEW_JSON_END
