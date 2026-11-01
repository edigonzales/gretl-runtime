# GRETL Runtime – Auftrag SCRIPT-first-Testing

Dieses Paket enthält den Implementierungsauftrag für echte SCRIPT-Prozesstests, vollständige gemeinsame Fachtests und ein generisches Runtime-Image, das **externe Scriptjobs** ausführt. Die zusätzliche JAR-Paketierungsprüfung bleibt erhalten.

**Version:** 1.0 · **Stand:** 1. November 2026  
**Repository:** `edigonzales/gretl-runtime`  
**Referenzrevision:** `8eec03184883ce9d585d46a768b647f2b7a19169` – nur untersuchter Ausgangsstand, kein Reset-Ziel.

## Dateien

| Datei | Aufgabe |
|---|---|
| [EXECUTION_PLAN.md](EXECUTION_PLAN.md) | Verbindliche Spezifikation: Ist-Befunde, Zielverträge, Klassen/Methoden, Testmatrix, vier Phasen, Gates, CI und Commit-/Reviewverfahren. |
| [CODEX_STARTPROMPT.md](CODEX_STARTPROMPT.md) | Vollständiger Startauftrag für Agent A. Er implementiert alle vier Phasen und orchestriert die Reviews selbständig. |
| [OPENCODE_REVIEW_PROMPT.md](OPENCODE_REVIEW_PROMPT.md) | Unabhängiger Prüfauftrag für Agent B mit OpenCode 2.x und DeepSeek Flash 1017 über die direkte API. |

Die Kriterien und fachlichen Verträge stehen zentral im Plan. Prompts ersetzen ihn nicht. Die Beispiel-Requests und API-Signaturen im Plan sind zu implementierende Verträge beziehungsweise auszufüllende Vorlagen, keine schon ausgeführten Abnahmen.

## Ablage im Repository

Das Archiv ist so aufgebaut, dass es im Root von `gretl-runtime` entpackt werden kann. Es entsteht:

```text
gretl-runtime/
  AGENTS.md
  pom.xml
  docs/
    implementation/
      script-first-testing/
        README.md
        EXECUTION_PLAN.md
        CODEX_STARTPROMPT.md
        OPENCODE_REVIEW_PROMPT.md
```

Bei einzeln heruntergeladenen Dateien diese vier Dateien gemeinsam in `docs/implementation/script-first-testing/` ablegen. Bestehende gleichnamige Dateien vorher vergleichen, nicht unbesehen überschreiben. Die alte Subprojektspezifikation nicht ersetzen; dies ist ein Folgeauftrag.

**Pfadbasis:** Repositorypfade in Plan/Prompts beziehen sich auf den Root von `gretl-runtime`. Die Markdownlinks in dieser README sind dagegen relativ zu dieser Datei.

Die vier Auftragsdateien vor dem Agentenstart lokal committen. Laufende lokale Arbeiten erhalten und einen klaren Ausgangsstand herstellen. Nicht auf den Referenzcommit zurücksetzen.

## Codex starten

Codex mit dem Repository-Root als Arbeitsverzeichnis öffnen und folgenden Auftrag übergeben:

```text
Lies zuerst AGENTS.md und danach vollständig

docs/implementation/script-first-testing/CODEX_STARTPROMPT.md

Führe den darin erteilten Auftrag gemäss EXECUTION_PLAN.md vollständig aus:
TST1, TST2, TST3 und TST4 nacheinander. Starte die unabhängigen
OpenCode-2.x-/DeepSeek-Flash-1017-Reviews selbst. Gehe erst nach
beendetem, technisch gültigem PASS zur nächsten Phase über und
arbeite danach ohne erneuten Benutzer-Checkpoint weiter.

SCRIPT ist der Hauptweg. IMAGE führt externe SCRIPT-Jobs im
generischen Runtime-Container aus, niemals einen eingebauten Job im JAR.
Vorhandene lokale Änderungen bewahren. Kein Push, Merge oder Release.
```

Alternativ den vollständigen Inhalt von `CODEX_STARTPROMPT.md` als Startprompt verwenden.

## Die vier Phasen

| Phase | Ergebnis |
|---|---|
| **TST1** | SCRIPT als echter unabhängiger Prozessmodus und Default; kein Paketierungsschritt für den Job. |
| **TST2** | Vollständige gemeinsame Fach-/Projektprüfungen in SCRIPT und JAR; echte Dienste und modespezifische Nachweise. |
| **TST3** | Tatsächliches generisches Runtime-Image mit externen Quellen, realen Fach-/Mount-/Rechte-/Abbruchtests. |
| **TST4** | Reguläre vollständige CI-Matrix, Fehlgrün-Abwehr und unabhängige kumulative Gesamtabnahme. |

Jede Phase braucht erfolgreiche eigene Pflichtprüfungen und einen **neuen unabhängigen Review am festen Kandidatencommit**. Nacharbeit bleibt in derselben Phase und erhält einen neuen Kandidaten. TST4 enthält die kumulative Prüfung aller 38 Kriterien.

## Was die drei Modi bedeuten

```text
SCRIPT  → installierte Kandidatenruntime + externer Job → Hostprozess
IMAGE   → generische Kandidatenruntime im Container + externer Jobmount
          → darin derselbe SCRIPT-Weg
JAR     → separat paketierter konkreter Job → java -jar
```

Ein Image darf normale Java-/GRETL-Bibliotheks-JARs enthalten. Es darf für diese IMAGE-Tests aber keinen vorbereiteten konkreten Testjob voraussetzen. Derselbe unveränderte Containerstand muss verschiedene und nachträglich geänderte externe Jobs ausführen.

Unit-/Komponententests bleiben bestehen. Direkte `GretlCli.run()`-Aufrufe und RecordingEngine-Tests sind wertvoll, zählen aber nicht als Nachweis eines separaten Launcher- beziehungsweise Containerprozesses.

## Werkzeuge und Prüfgrenzen

Die konkrete lokale OpenCode-2.x-Version und ihre Flags werden vor Verwendung geprüft. Vorhandene GUI-/CLI-Anmeldung nutzen; kein API-Key muss allein für dieses Dokument neu in eine Shellvariable kopiert werden. Direkter Provider/Modellschlüssel ist `deepseek/deepseek-flash`, API-Host `api.deepseek.com`; die aktuelle Zuordnung zu Flash-1017 wird beim echten Review überprüft.

Der autonom laufende Reviewer erhält einen eigenen detached Worktree und einen separaten Ausgabeordner, den `mvn clean` nicht entfernt. Er führt Tests selbst aus und entwickelt neue Gegenproben, verändert den Produktkandidaten aber nicht. PASS, FAIL und BLOCKED sind die einzigen Urteile.

Die in den mitgelieferten Compiler-Beispielen vorkommenden spezifischen Browser-/SDK-/Python-Guardanforderungen wurden bewusst nicht in GRETL übernommen. Insbesondere bleibt der vorhandene GRETL-Python-Audit verwendbar. Dieser Auftrag baut keine neue allgemeine Agentenplattform.

**Status dieses Pakets:** Spezifikation und Arbeitsanweisungen erstellt. GRETL-Implementierung, Maven-Gates, Dockerqualifikation und unabhängige OpenCode-Abnahmen sind Aufgaben der anschliessenden Umsetzung und wurden nicht durch die Erstellung dieser Dateien bereits ausgeführt.
