# GRETL Tooling – aktueller Auftragsstatus

**Arbeitsstand · 2. November 2026.**
P0 ist unabhängig abgenommen. Der erste P1-Review ist mit gültigem FAIL beendet; F1–F3 sind lokal korrigiert, neue Kandidatengates und unabhängiger Review stehen aus.

`assignmentBase`: `185fc6700b5cb2897f724d457610a57f059b3421`.
`requirementsDigest`: `cb2ea98d41987604be60d1a7520c3e9e129d437cbb2b9a4a1d6f5a2e085997d3`.
Primärplattform: macOS 27.0.1 arm64, JDK 25.0.2, OpenCode 2.2.5 mit direktem DeepSeek Flash-1017. Tatsächliche Bereitschafts- und Bildprobe in P0 belegt.

| Phase | Ziel | Status | geprüfter Produkt-SHA | unabhängiger Review |
|---|---|---|---|---|
| P0 | Verträge, Katalog, Reviewkette | PASS | `5ff6cffcff1d23d1cec00116a8d0a51948945f33` | [Originalreview](reviews/P0/attempt-1/review.md) |
| P1 | Sicherer Parser und Recovery | IN_PROGRESS · Nacharbeit | `ac861d9deb1f623c33d0b38d31ab2030d24aec82` · FAIL | [Originalreview](reviews/P1/attempt-1/review.md) |
| P2 | Projekte, Snapshots, Semantik | PLANNED | — | — |
| P3 | Queries und Editplanung | PLANNED | — | — |
| P4 | Eigenständiger LSP | PLANNED | — | — |
| P5 | Installierter Texteditor | PLANNED | — | — |
| P6 | Interaktiver Graph | PLANNED | — | — |
| P7 | Schreibender Modeler | PLANNED | — | — |
| P8 | Resilienz und Gesamtabnahme | PLANNED | — | — |

## Aktuell erlaubter nächster Schritt

P1-Findings F1–F3 reproduzieren/beheben, neuer Kandidat mit frischen Rootgates und unabhängigem Review. P2 bleibt bis zum gültigen P1-PASS gesperrt.

## Fortschreibung

Nach tatsächlichem Kandidatenreview Produkt-SHA, Originalreviewpfad, Urteil und Belegdigest eintragen. Fehlgeschlagene Versuche erhalten. Keine selbstreferenzielle Kandidaten-SHA in denselben Produktcommit schreiben; reine Status-/Reviewarchivierung erfolgt anschliessend als Dokucommit.
