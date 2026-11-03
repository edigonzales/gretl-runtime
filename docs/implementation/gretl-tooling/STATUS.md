# GRETL Tooling – aktueller Auftragsstatus

**Arbeitsstand · 3. November 2026.**
P0 und P1 sind unabhängig abgenommen; beide äusseren Reviewprozesse sind beendet und validiert. P2 ist in Implementierung und gezielter Pruefung.

`assignmentBase`: `185fc6700b5cb2897f724d457610a57f059b3421`.
`requirementsDigest`: `cb2ea98d41987604be60d1a7520c3e9e129d437cbb2b9a4a1d6f5a2e085997d3`.
Primärplattform: macOS 27.0.1 arm64, JDK 25.0.2, OpenCode 2.2.5 mit direktem DeepSeek Flash-1017. Tatsächliche Bereitschafts- und Bildprobe in P0 belegt.

| Phase | Ziel | Status | geprüfter Produkt-SHA | unabhängiger Review |
|---|---|---|---|---|
| P0 | Verträge, Katalog, Reviewkette | PASS | `5ff6cffcff1d23d1cec00116a8d0a51948945f33` | [Originalreview](reviews/P0/attempt-1/review.md) |
| P1 | Sicherer Parser und Recovery | PASS | `412909f954a6059a331754db1bf11d897ffb9b88` | [Originalreview](reviews/P1/attempt-2/review.md); [erster FAIL](reviews/P1/attempt-1/review.md) |
| P2 | Projekte, Snapshots, Semantik | IN_PROGRESS | `566bb3446456523e9a1bd47ccbec7cfc54df02f4` (keine Freigabe) | [F1/F2](reviews/P2/attempt-1/review.md) und [F3](reviews/P2/attempt-2/review.md) repariert; [neuer URI-Befund](reviews/P2/attempt-3-transport-retry-1/review.md), [äusserer Lauf ungültig](reviews/P2/attempt-3-transport-retry-1/outer-validation.json) |
| P3 | Queries und Editplanung | PLANNED | — | — |
| P4 | Eigenständiger LSP | PLANNED | — | — |
| P5 | Installierter Texteditor | PLANNED | — | — |
| P6 | Interaktiver Graph | PLANNED | — | — |
| P7 | Schreibender Modeler | PLANNED | — | — |
| P8 | Resilienz und Gesamtabnahme | PLANNED | — | — |

## Aktuell erlaubter nächster Schritt

P2 nach CODEX_STARTPROMPT implementieren. P3 bleibt bis zum gültigen P2-PASS gesperrt.

## Testauswahl nach Benutzerpräzisierung

Während der Umsetzung diffbezogene Tests. Beide Rootpflichtgates einmal frisch pro finalem Phasenkandidaten. Der unabhängige Reviewer validiert diese vollständigen Rohbelege und führt betroffene risikotragende Tests sowie eigene neue Gegenproben aus. Keine zusätzlichen pauschalen Rootvolläufe im Review. GUIprüfungen erst in ihren fälligen Produktphasen.

## Fortschreibung

Nach tatsächlichem Kandidatenreview Produkt-SHA, Originalreviewpfad, Urteil und Belegdigest eintragen. Fehlgeschlagene Versuche erhalten. Keine selbstreferenzielle Kandidaten-SHA in denselben Produktcommit schreiben; reine Status-/Reviewarchivierung erfolgt anschliessend als Dokucommit.
