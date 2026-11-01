# SCRIPT-first-Testing – Arbeitsstand

Auftragsbasis: `520b59be12f7966be46d0eaaca046c6ee3dc04f7`.
Arbeitsbranch: `codex/script-first-testing`; fremde Änderungen waren beim Start
nicht vorhanden. Kein Push, Merge oder Release.

| Phase | Stand | Geprüfter Produktkandidat |
| --- | --- | --- |
| TST1 | PASS, unabhängig abgenommen | `52ca5bee2e52f7c07106d305e071add1c3c3724a` |
| TST2 | PASS, unabhängig abgenommen | `b1440bfd678d4ea83d70e51778b09997c441aace` |
| TST3 | IN_PROGRESS | noch kein Kandidat |
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

TST2-Phasenbasis: `6bf473df13f1c33677e1a4a4f33fd49776aa6546`.
[Implementierungsbericht TST2](implementation/TST2.md).

## TST2-Abnahme

TST2: [unveränderter OpenCode-Bericht](reviews/TST2-r1-b1440bfd678d.md),
[Urteil](reviews/TST2-r1-b1440bfd678d.verdict.json).
Bericht SHA-256: `589c844fadceb7abef3437e695bdae3b02aefa9779276c70100b4b26e6b32a52`.

Eigene frische Gates: Standard 693, SCRIPT/full und JAR/full je 749 Tests;
überall 0 Failures/Errors/Skips und Exit 0. Beide Vollmodi weisen alle
44 Standardoperationen separat positiv nach. 174 gemeinsame Fachszenarien
besitzen identische Inputbytes. Szenariomengen: LOCAL 135, FULL 213; darin
je zwei ausdrücklich geprüfte Infrastruktur-Negative, also 133 bzw. 211
abgeschlossene CLI-Prozesse. Alle Archive bestehen auch nach weiterem clean.

Unabhängig: Standard und JAR/local je 693, SCRIPT/full und JAR/full je
749 Tests, sämtliche Audits und Negativproben grün. Eigene Projektgegenprobe
mit festen Erwartungsbytes in SCRIPT/JAR, geändertem Input und kaputtem
Descriptor sowie eigener fehlender-Szenario-Probe nachgeprüft. OpenCode
2.2.5, direkter DeepSeek-Flash-1017-Vertrag; Prozess und Orchestrator
beendet (Exit 0), alle zehn Kriterien erfüllt, keine offenen Muss-Findings.
Beide HEADs unverändert und sauber; keine verbleibenden Review-Jobprozesse.

Rohbelege: `/Users/stefan/sources/gretl-review-output-TST2-1-b1440bfd678d/`
und `/Users/stefan/sources/gretl-script-first-evidence/TST2-candidate-b1440bfd678d4ea83d70e51778b09997c441aace/`.
Diese Nachweisablage ändert den geprüften Produktkandidaten nicht.
Nächster Schritt: TST3-Phasenbasis an diesem Nachweiscommit festhalten;
generisches Runtime-Image und tatsächliche externe SCRIPT-Jobs qualifizieren.
