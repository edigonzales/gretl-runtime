# GRETL-Sprachkern, LSP und Modeler – Agentenpaket

**Version 1.0 · 2. November 2026**

Dieses Paket ist der Implementierungsauftrag für Codex. Es enthält noch keinen implementierten LSP/Modeler und keine vorweggenommenen PASS-Ergebnisse.

## Ablage und Start

Alle Dateien dieses Verzeichnisses gehören nach:

```text
<gretl-runtime>/docs/implementation/gretl-tooling/
```

Das ZIP ist bereits in dieser Verzeichnisstruktur aufgebaut. Im bestehenden `gretl-runtime`-Root entpacken beziehungsweise den enthaltenen `docs`-Baum zusammenführen. Vorhandene Dateien gleichen Namens vorher vergleichen; nicht fremde Änderungen überschreiben. Das Root-`AGENTS.md` wird durch das ZIP nicht ersetzt.

Codex im **Root des bestehenden gretl-runtime-Checkouts** starten und folgenden Auftrag geben:

```text
Lies docs/implementation/gretl-tooling/CODEX_STARTPROMPT.md vollständig
und führe den darin beschriebenen Implementierungsauftrag aus.
Beginne mit P0. Nach jeder Phase ist ein unabhängiger autonom gestarteter
OpenCode-2.x-Review mit DeepSeek Flash 1017 über die direkte DeepSeek API Pflicht.
Setze nach gültigem PASS selbständig mit der nächsten Phase fort, bis P8 abgeschlossen ist.
```

Die detaillierten Regeln stehen in den Dateien; der kurze Einstieg ersetzt sie nicht. Bestehende OpenCode-Anmeldung soll weiterverwendet werden. Kein routinemässiger manueller Reviewstart und kein Zwang, einen bereits funktionierenden API-Key neu als Environmentvariable anzulegen.

## Inhalt

| Datei | Aufgabe |
|---|---|
| [CODEX_STARTPROMPT.md](CODEX_STARTPROMPT.md) | Operativer Implementierungsauftrag für Agent A. |
| [EXECUTION_PLAN.md](EXECUTION_PLAN.md) | Neun Phasen, 81 Musskriterien, Gates und autonomer Reviewprozess. |
| [ARCHITECTURE.md](ARCHITECTURE.md) | Module, Daten-/Protokollverträge und Klassen/Methoden bis zum Edit- und Modelerkern. |
| [TEST_MATRIX.md](TEST_MATRIX.md) | 34 Prüfgruppen, konkrete Regressionen und 30 echte GUIfälle. |
| [OPENCODE_REVIEW_PROMPT.md](OPENCODE_REVIEW_PROMPT.md) | Unabhängige Ziel-, Code-, Test- und GUIprüfung für Agent B. |
| [SOURCE_AUDIT.md](SOURCE_AUDIT.md) | Geprüfte Ausgangsquellen, Altbefunde und bewusste Unterschiede zur INTERLIS-Vorlage. |
| [ACCEPTANCE_CRITERIA.json](ACCEPTANCE_CRITERIA.json) | Generierter maschinenlesbarer Kriterienindex; keine Ergebnisse. |
| [schemas/review-request.schema.json](schemas/review-request.schema.json) | Struktureller Vertrag für den Reviewauftrag. |
| [schemas/review-verdict.schema.json](schemas/review-verdict.schema.json) | Struktureller Vertrag für das unabhängige Urteil. |
| [AGENTS.md](AGENTS.md) | Lokale Arbeitsregeln; ersetzt keine Rootregeln. |
| [STATUS.md](STATUS.md) | Initiale Statusübersicht, alle Phasen noch PLANNED. |

Die Review-Schemas prüfen Struktur, Muster und einfache PASS-Bedingungen. Bei FAIL/BLOCKED dürfen tatsächlich abweichende oder unverifizierte Modellangaben wahrheitsgemäss erfasst werden; nur PASS erzwingt die richtige Identität. `testCount: null` bezeichnet einen Nicht-Test-Check wie Archivprüfung, nicht einen erfolgreichen Nulltest. Die Schemas beweisen weder eine tatsächlich ausgeführte Prüfung noch die komplette AC-/Belegidentität. Der in P0 zu implementierende/adaptierende Gatechecker prüft zusätzlich die exakte Kriterienmenge, die Prozess-/Sessionidentität, Dateihashes, aktuelle Rohbelege und unveränderte Kandidaten.

## Reihenfolge

| Abschnitt | Phasen | Ergebnis |
|---|---|---|
| Kern | P0–P3 | Katalog, gemeinsame Verträge, sicherer Parser, Projekte/Semantik, Queries und Editpläne. |
| LSP und Texteditor | P4–P5 | Eigenständiger Java-LSP und installierte VS-Code-Editorintegration. |
| Graph und Modeler | P6–P8 | Interaktive Ansicht, echte grafische Jobbearbeitung und kumulative Gesamtabnahme. |

React Flow/ELK sind für die neue Oberfläche festgelegt. Die Runtime bleibt Gradle-frei. Der Sprachkern und LSP dürfen keine Abhängigkeiten zu anderen GRETL-Varianten erhalten. Kontrollierte einmalige Quellübernahme aus gretl-next ist mit Provenienz erlaubt.

## Was bewusst anders als in der Vorlage ist

Ein Repository statt zweier Repositories. Zuerst Kern statt früher Produkt-GUI. Ein schreibender GRETL-Modeler statt fachlich readonly UML. Vorhandene Runtime-Pythonharnesses bleiben erlaubt. Die aktuellen vollständigen Root-Maven-Gates bleiben verbindlich. Die Primärplattform muss wirklich getestet werden; zusätzliche Plattformen werden nicht aus Crossbuilds als freigegeben ausgegeben.

## Hashprüfung

`PACKAGE_SHA256SUMS.txt` enthält SHA-256 aller übrigen Auftragsdateien unter diesem Verzeichnis, mit relativen Pfaden. Nach bewusstem Bearbeiten der Spezifikationen gelten diese Auslieferungshashes nicht mehr; der Implementierungsorchestrator erzeugt eigene Requirementsdigests für die tatsächlichen Kandidaten. Diese Paketdatei ist kein Produkt- oder Reviewerbeleg.
