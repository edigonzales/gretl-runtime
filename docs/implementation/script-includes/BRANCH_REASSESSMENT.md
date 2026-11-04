# Main-Abgleich für `apply from:` nach abgeschlossenem Tooling-Merge

**4. November 2026 · Begleitbericht zu Spezifikation 1.3**

Dieser Bericht erläutert die Fortschreibung von v1.2. Aktiver Vertrag bleibt [EXECUTION_PLAN.md](EXECUTION_PLAN.md); Einstieg und Zweitreview stehen in [CODEX_STARTPROMPT.md](CODEX_STARTPROMPT.md) und [OPENCODE_REVIEW_PROMPT.md](OPENCODE_REVIEW_PROMPT.md). Kein zusätzlicher Toolingauftrag, kein Implementierungs- oder Produktprüfbericht.

## 1. Verifizierte Referenzen

| Rolle | Commit / Nachweis |
|---|---|
| Aktuelles untersuchtes `main` | `0bfc8f0fccf43989d751d4f5490202311d200628` |
| Merge | Pull Request #2, „Add GRETL language server and graphical modeler“ |
| Veröffentlichter Tooling-Abschluss-/Archivstand | `c0b65d75fead9e3e017665e01b3b5a69e62f64f9` |
| Ursprünglich unabhängig abgenommener P8-Produktkandidat | `54bb123070fc7bd81aea58b4e1191beecf568cbf` |
| Vorheriger Recherchepin der Spezifikation 1.2 | Ebenfalls `54bb123070fc7bd81aea58b4e1191beecf568cbf`; damals war dessen P8-Abschluss noch nicht archiviert. |

Die Main-Statusdatei weist P0–P8 als abgenommen aus. Die P8-Validierungsdatei nennt Review-ID `860d5238-a848-4082-b196-587c2a270e6c`, den Produktkandidaten `54bb123070fc7bd81aea58b4e1191beecf568cbf` und `result: PASS`. Der Abschlussbericht dokumentiert die ursprünglichen Code-, GUI- und Prozessnachweise und deren Plattformgrenzen [M1–M4]. Das sind aus dem Repository gelesene Abnahmebelege, keine in dieser Unterhaltung wiederholten Testläufe.

**Zeitliche Einordnung:** Der ursprüngliche P8-Bericht sagt, dass im Rahmen jener Abnahme kein Merge oder Release ausgeführt wurde. Der spätere Main-Commit belegt den inzwischen erfolgten Merge. Diese beiden Aussagen beziehen sich auf unterschiedliche Schritte; der alte Bericht wird nicht rückwirkend verändert. Eine Veröffentlichung wird aus dem Merge nicht abgeleitet.

## 2. Entscheidend: Produktinhalt ist gegenüber v1.2 unverändert

Die Compare-Antwort enthält zwei weitere Commits und eine umfangreiche, begrenzte Liste von Abschluss-/Reviewdateien. Eine solche Liste allein reicht nicht, um das Fehlen anderer Änderungen zu beweisen.

Deshalb wurden zusätzlich die **vollständigen nichtrekursiven Root-Trees** beider Pins gelesen. Beide Antworten melden `truncated=false`. Die Pfadmengen stimmen überein. Mit Ausnahme von `docs/` stimmen Mode, Typ und Git-Object-ID sämtlicher Root-Einträge überein. Identische Tree-IDs umfassen den jeweiligen gesamten Unterbaum [M5, M6].

Ausgewählte übereinstimmende Objekt-IDs:

| Root-Eintrag | Identische Git-Object-ID an beiden Pins |
|---|---|
| `gretl-groovy` | `aa93cc97168c0fcb34d4318558fa2edd644bf5d1` |
| `gretl-runner` | `3d4d3e738653b6a3d9b4cb3d9fb8688173338a81` |
| `gretl-packager` | `68200714cf442b487da747dd91f9f8954bfd4471` |
| `gretl-job-runtime` | `a1f65c9df90a828b0e45ec7171eac0b1567cb66c` |
| `gretl-language-contract` | `48eb5d956108c3998870ddc88afce36d877e5234` |
| `gretl-tooling-core` | `6c2a045fb48bdc109c3cfa3730b2f6d0d664097b` |
| `gretl-lsp` | `2ad106be8856d6e37098bd4a0c76795fd9207bed` |
| `gretl-integration-tests` | `c1983d399e917ca98555d8fe7b9ae091bab332ec` |
| `vscode` | `687e191235cb417e7db6bdc548e481808eb96a52` |
| `tools` | `c99e422a1032bf21995965241c6ad1403e5d66fc` |
| `pom.xml` | `a1925cf46c124a5f8b67b23861aff46fbc2fbec7` |
| `AGENTS.md` | `2a4912abea8ad6acaaf8f2d51996f86ddf98121e` |

Die unterschiedliche `docs`-Tree-ID lautet am früheren Produktpin `233d979d0c848b148477884160d3fd7d85ca7dea`, am Mainpin `cbdd54c5810b0c72f201944a25a77a103b0a74a2`.

**Aussagegrenze:** Das ist eine Git-Inhalts-/Quellenprüfung, kein Build, kein Lauf des Include-Features und kein neuer LSP-/GUI-Test. Es wurde kein lokaler Repositorymerge durchgeführt. Die archivierten P8-Rohbelege wurden für diesen Spezifikationsabgleich nicht vollständig erneut ausgeführt oder auditiert.

## 3. Änderungen von v1.2 zu v1.3

| Bestandteil | Neue Anweisung |
|---|---|
| Arbeitsbasis | Neuer Featurebranch direkt von integriertem Main; aktuellen Ausgangs-SHA pinnen. |
| Vorläufige Toolingzustände | Entfernt. P8 ist abgeschlossen; keine neue Warte-/Freigabeschleife. |
| Spätere Mergeprobe | Für einen neu von Main gestarteten Branch nicht erforderlich. Nur bei einem bereits begonnenen älteren Featurebranch oder tatsächlichem späterem Upstreambedarf integrieren. |
| Identitäten | Main-/Featurebasis, akzeptierten ursprünglichen Tooling-Produkt-SHA und neuen Featurekandidaten getrennt dokumentieren. |
| P0 / P4 | Basisprüfung vereinfacht; echte Include-/Editor-Kompatibilität weiterhin nachweisen. |
| Testumfang | Bereits abgeschlossene Tooling-Gesamtqualifikation nicht wiederholen. Neue Include-Tests und relevante Regressionen bleiben Pflicht. |
| Review | Unverändert autonom je Phase durch OpenCode 2.x / DeepSeek Flash 1017; neue Specversion und Digest benutzen. |
| Recherchebericht | Dieser Main-Abgleich ersetzt die veraltete Darstellung einer noch laufenden P8. |

Die Runtime-Verträge S01–S19, der technische Zuschnitt in Abschnitt 5 und die Runtimephasen P1–P3 bleiben gegenüber v1.2 unverändert. C01–C06 und C08 bleiben ebenfalls bestehen; C07 wird nur hinsichtlich der bereits integrierten Basis präzisiert. Es wird kein zusätzliches Feature durch die Hintertür beauftragt.

## 4. Was der Merge nicht automatisch liefert

Die identischen Produktdateien bedeuten zugleich: Die in v1.2 festgehaltenen Includegrenzen bleiben bestehen. Der Runtime-DSL fehlt noch das hier beauftragte `apply from:`; der statische `ProjectResolver` lädt keinen transitiven Includeabschluss. Der bisherige Opaque-/Partialpfad ist eine Übergangsmöglichkeit, keine bereits ausgeführte Qualifikation des neuen Features.

Daher bleibt der Lieferumfang **`RUNTIME_INCLUDES_WITH_SAFE_PARTIAL_TOOLING`**. Die neue Runtime kann nach Umsetzung alle Fragmente laden und paketieren; der Editor darf zunächst nur den bekannten Anteil anzeigen und nachgewiesen sichere lokale Änderungen zulassen. Vollständige Include-Navigation, kontextsensitives Rename über Shared-Fragmente und der komplette Fragmentgraph sind nicht Teil dieses Auftrags. `finalizedBy` bleibt ebenfalls vertagt.

Die bestehenden speziellen und generischen Editzugänge werden gezielt geprüft. Die bisherige Codelektüre hat einen Prüfbedarf beschrieben, keinen reproduzierten Fehler behauptet. Ein bereits funktionierender Schutz wird mit einem Test gesichert; nur ein nachgewiesener Defekt erhält eine minimale Korrektur. Der echte stdio-/installierte-VSIX-Smoke aus v1.2 bleibt erforderlich, die komplette alte G01–G30-Matrix nicht.

Der an den alten Toolingauftrag gebundene `tools/gretl-tooling/review.mjs` ist ebenfalls unverändert. Seine 81-Kriterien-/Rootgatekopplung darf nicht ungeprüft in den neuen Featureauftrag übernommen werden. Vorhandene geeignete Transport-/Identitätsmechanik nutzen oder einen kleinen Featureadapter verwenden, ohne historische Abnahmeverträge umzubauen.

## 5. Primärquellen und Dokumentprüfung

- **M1 – Main-Merge:** `https://github.com/edigonzales/gretl-runtime/commit/0bfc8f0fccf43989d751d4f5490202311d200628`.
- **M2 – Main-Status:** `https://github.com/edigonzales/gretl-runtime/blob/0bfc8f0fccf43989d751d4f5490202311d200628/docs/implementation/gretl-tooling/STATUS.md`.
- **M3 – P8-Validierung:** `https://github.com/edigonzales/gretl-runtime/blob/0bfc8f0fccf43989d751d4f5490202311d200628/docs/implementation/gretl-tooling/reviews/P8/attempt-1/validation.json`.
- **M4 – P8-Abschlussbericht:** `https://github.com/edigonzales/gretl-runtime/blob/0bfc8f0fccf43989d751d4f5490202311d200628/docs/implementation/gretl-tooling/implementation/P8.md`.
- **M5 – Früherer vollständiger Root-Tree:** `https://api.github.com/repos/edigonzales/gretl-runtime/git/trees/54bb123070fc7bd81aea58b4e1191beecf568cbf`.
- **M6 – Aktueller vollständiger Root-Tree:** `https://api.github.com/repos/edigonzales/gretl-runtime/git/trees/0bfc8f0fccf43989d751d4f5490202311d200628`.
- **M7 – Weitere konkrete Codepfade:** Plan §11, R13–R22; dieselben Produktinhalte wie beim v1.2-Abgleich.
- **M8 – OpenCode-2-CLI / Modellzuordnung:** `https://opencode.ai/v2/docs/cli/commands/`, `https://api-docs.deepseek.com/quick_start/pricing/`. Am 4. November 2026 erneut gelesen; Ausführungsumgebung und beweglichen Alias vor dem echten Review nochmals prüfen.

Die erzeugten Dokumente werden lokal auf interne Versions-/Basisverweise, unveränderte fachliche Abschnitte, relative Dokumentlinks, geschlossene Codeblöcke und ZIP-Prüfsummen geprüft. Diese Paketprüfung ist keine Produktabnahme.
