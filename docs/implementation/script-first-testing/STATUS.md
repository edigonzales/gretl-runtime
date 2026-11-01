# SCRIPT-first-Testing – Arbeitsstand

Auftragsbasis: `520b59be12f7966be46d0eaaca046c6ee3dc04f7`.
Arbeitsbranch: `codex/script-first-testing`; fremde Änderungen waren beim Start
nicht vorhanden. Kein Push, Merge oder Release.

| Phase | Stand | Geprüfter Produktkandidat |
| --- | --- | --- |
| TST1 | PASS, unabhängig abgenommen | `52ca5bee2e52f7c07106d305e071add1c3c3724a` |
| TST2 | IN_PROGRESS | noch kein Kandidat |
| TST3 | noch nicht begonnen | — |
| TST4 | noch nicht begonnen | — |

TST1: [unveränderter OpenCode-Bericht](reviews/TST1-r1-52ca5bee2e52.md),
[Urteil](reviews/TST1-r1-52ca5bee2e52.verdict.json).
Bericht SHA-256:
`c15a7219dc9e4f928f8d974f6862945af361a8a74eab1fffe65a4cb4149a8253`.

Eigene und unabhängige frische Gates am Kandidaten: Standard 686,
JAR/local 686, SCRIPT/full 742 Tests; jeweils Exit 0 und 0
Failures/Errors/Skips. Alle 44 geforderten Standardoperationen positiv
SCRIPT/full. Standard und JAR/local wurden mit absichtlich unerreichbarem
Docker-Socket ausgeführt. Alle Audits, 21 Reviewstarter-Tests, 73
Dokumentationswerkzeugtests, Linkprüfung und Quarto-Render grün.

Reviewer: OpenCode 2.2.5 aus der Desktop-App, `deepseek/deepseek-flash`,
direkter Host `api.deepseek.com`; offizielle 1017-Zuordnung am 01.11.2026
verifiziert. Privater Prozess vollständig beendet (Exit 0), beide HEADs
unverändert und sauber, alle 10 Kriterien erfüllt, eigene Gegenprobe
nachgeprüft, keine relevanten unbeendeten Kinderprozesse.

Rohbelege:
`/Users/stefan/sources/gretl-review-output-TST1-1-52ca5bee2e52/` und
`/Users/stefan/sources/gretl-script-first-evidence/`.
Host macOS/ARM64, Docker Linux/ARM64; PostGIS teilweise AMD64-emuliert.
Keine echte GRETL-IMAGE-Qualifikation behauptet.

Diese reine Nachweisablage ändert den geprüften Produktkandidaten nicht.
Nächste Aktion: TST2-Phasenbasis an diesem Nachweiscommit festhalten,
Methoden/Varianten klassifizieren, unabhängigen Szenariokatalog und
SCRIPT-/JAR-local/full-Matrix samt Fehlgrün-Proben umsetzen.
