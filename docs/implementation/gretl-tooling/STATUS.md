# GRETL Tooling – aktueller Auftragsstatus

**Arbeitsstand · 4. November 2026.**
P0 bis P7 sind unabhängig abgenommen; die äusseren Reviewprozesse sind beendet und validiert. P8 ist in der kumulativen Gesamtabnahme.

`assignmentBase`: `185fc6700b5cb2897f724d457610a57f059b3421`.
`requirementsDigest`: `cb2ea98d41987604be60d1a7520c3e9e129d437cbb2b9a4a1d6f5a2e085997d3`.
Primärplattform: macOS 27.0.1 arm64, JDK 25.0.2, OpenCode 2.2.5 mit direktem DeepSeek Flash-1017. Tatsächliche Bereitschafts- und Bildprobe in P0 belegt.

| Phase | Ziel | Status | geprüfter Produkt-SHA | unabhängiger Review |
|---|---|---|---|---|
| P0 | Verträge, Katalog, Reviewkette | PASS | `5ff6cffcff1d23d1cec00116a8d0a51948945f33` | [Originalreview](reviews/P0/attempt-1/review.md) |
| P1 | Sicherer Parser und Recovery | PASS | `412909f954a6059a331754db1bf11d897ffb9b88` | [Originalreview](reviews/P1/attempt-2/review.md); [erster FAIL](reviews/P1/attempt-1/review.md) |
| P2 | Projekte, Snapshots, Semantik | PASS | `1a8d9f0a4096295c2e5898bab2593bb3eb34789e` | [Originalreview](reviews/P2/attempt-4-resumed-pass/review.md), [validierter Abschluss](reviews/P2/attempt-4-resumed-pass/validation.json); frühere Fehlversuche erhalten |
| P3 | Queries und Editplanung | PASS | `96688a5b2436c25504dbfbb33ce375a002b3cee0` | [Originalreview](reviews/P3/attempt-2/review.md), [validierter Abschluss](reviews/P3/attempt-2/validation.json); [erster FAIL](reviews/P3/attempt-1/review.md) erhalten |
| P4 | Eigenständiger LSP | PASS | `666e812df8394a1f533d534c9c6d134b132898ab` | [Originalreview](reviews/P4/attempt-2/review.md), [validierter Abschluss](reviews/P4/attempt-2/validation.json); [erster FAIL](reviews/P4/attempt-1/review.md) erhalten |
| P5 | Installierter Texteditor | PASS | `4520ee6960d8b0857fad828f972a44950c5aeb09` | [Originalreview mit Infrastruktur-BLOCKED](reviews/P5/attempt-1/review.md), [ergänzter Originalreview](reviews/P5/attempt-1/resume-1/review.md), [validierter Abschluss](reviews/P5/attempt-1/resume-1/validation.json) |
| P6 | Interaktiver Graph | PASS | `ee3a2e8a40bf965a183bc688157d755e2f2320b2` | [Originalreview](reviews/P6/attempt-1/review.md), [ergänzter Originalreview nach Metadatenkorrektur](reviews/P6/attempt-1/resume-1/review.md), [validierter Abschluss](reviews/P6/attempt-1/resume-1/validation.json) |
| P7 | Schreibender Modeler | PASS | `53f7f3daaf92d002fd75978c25471321fd99a310` | [Originalreview](reviews/P7/attempt-1/review.md), [validierter Abschluss](reviews/P7/attempt-1/validation.json) |
| P8 | Resilienz und Gesamtabnahme | IN PROGRESS | — | — |

## Aktuell erlaubter nächster Schritt

P8 nach CODEX_STARTPROMPT ausführen: frischer zusammengehöriger Build, kumulativ
alle 81 Kriterien und G01–G30, Linux-LSP-Prozess sowie Resilienz-/Ressourcenprüfung.
P7 ist am unveränderten Kandidaten unabhängig abgenommen.

Abgeschlossene Modelerabnahme: [P7-Bericht](implementation/P7.md).

## Testauswahl nach Benutzerpräzisierung

Während der Umsetzung diffbezogene Tests. Beide Rootpflichtgates einmal frisch pro finalem Phasenkandidaten. Der unabhängige Reviewer validiert diese vollständigen Rohbelege und führt betroffene risikotragende Tests sowie eigene neue Gegenproben aus. Keine zusätzlichen pauschalen Rootvolläufe im Review. GUIprüfungen erst in ihren fälligen Produktphasen.

## Fortschreibung

Nach tatsächlichem Kandidatenreview Produkt-SHA, Originalreviewpfad, Urteil und Belegdigest eintragen. Fehlgeschlagene Versuche erhalten. Keine selbstreferenzielle Kandidaten-SHA in denselben Produktcommit schreiben; reine Status-/Reviewarchivierung erfolgt anschliessend als Dokucommit.
