# SCRIPT-first-Testing – Arbeitsstand

Auftragsbasis: `520b59be12f7966be46d0eaaca046c6ee3dc04f7`.
Arbeitsbranch: `codex/script-first-testing`; fremde Änderungen waren beim Start
nicht vorhanden. Kein Push, Merge oder Release.

| Phase | Stand | Geprüfter Produktkandidat |
| --- | --- | --- |
| TST1 | PASS, unabhängig abgenommen | `52ca5bee2e52f7c07106d305e071add1c3c3724a` |
| TST2 | PASS, unabhängig abgenommen | `b1440bfd678d4ea83d70e51778b09997c441aace` |
| TST3 | PASS, unabhängig abgenommen | `17dca71d8344095eefc586ed1c4f610c02675f22` |
| TST4 | PASS, kumulativ unabhängig abgenommen | `3655842006d94a7cada138e91d88aa143190606b` |

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

[TST3-Implementierungsbericht](implementation/TST3.md): echte IMAGE- und
OpenShift-Gegenproben umgesetzt. Der erste Kandidat besteht sämtliche
Java-Gates; sein IMAGE-Audit scheitert an Python-3.9-Nanosekundenparsing.
Die präzise Korrektur samt Grenzprobe ist umgesetzt; neue feste Gates
und unabhängiger TST3-Review folgen. TST4 bleibt noch nicht begonnen.


## TST3-Abnahme

Produktkandidat: `17dca71d8344095eefc586ed1c4f610c02675f22`.
[Unveränderter unabhängiger Bericht](reviews/TST3-r1-17dca71d8344.md),
[strukturiertes Urteil](reviews/TST3-r1-17dca71d8344.verdict.json): PASS.
Bericht SHA-256:
`1f1af0d29fa4f6d19ceb54f19ef5decd771df0679ae3d04fe88e0c2338d7cb27`.

Eigene und unabhängige frische Gates: Standard 696, SCRIPT/full 752,
JAR/full 752, IMAGE/full 757 und IMAGE/local 701 Tests. Überall Exit 0,
keine Failures/Errors/Skips; alle 44 Standardoperationen separat pro
Vollmodus. 174 COMMON-Inputbäume bytegleich. Alle ausgewählten
Negativproben (12/13/13/16/15) und Dokumentationsprüfungen bestanden;
Archive nach clean erneut auditiert und tatsächliche Runtimebytes geprüft.

Reviewer: OpenCode 2.2.5, direkter DeepSeek-Flash-1017-Weg. Prozess und
Orchestrator beendet, Exit 0, beide HEADs unverändert und sauber, alle
zehn Kriterien erfüllt. Eigene neue Unicode-/Quellwechsel-, Rechte-,
SQLite-/DuckDB-, Signal- und Evidenzgegenproben nachgeprüft; 36 Assertions.
Unbekannte UID `1007654321:0`, read-only Root/Quelle, frische Volumes,
CapEff 0 und NoNewPrivs 1 mit nativer Arbeit zusätzlich bestätigt.
Keine echte Clusteradmission/PVC- oder AMD64-native-Imageprüfung behauptet.

Rohbelege: `/Users/stefan/sources/gretl-review-output-TST3-1-17dca71d8344/`
und `/Users/stefan/sources/gretl-script-first-evidence/TST3-candidate-17dca71d8344095eefc586ed1c4f610c02675f22/`.
Die unabhängige Gesamtprüfung beginnt jetzt als TST4: reguläre CI-Matrix,
restliche Fehlgrün-Proben, aktive Anleitungen und kumulative 38 Kriterien.


## TST4-Abnahme und Abschluss

Finaler geprüfter **Produkt-SHA**: `3655842006d94a7cada138e91d88aa143190606b`.
Phasenbasis: `15666544e55af21e16598e94c46294f9bdcae2c9`.
[Unveränderter unabhängiger Gesamtbericht](reviews/TST4-r1-3655842006d9.md),
[strukturiertes Urteil](reviews/TST4-r1-3655842006d9.verdict.json): **PASS**
für alle 38 Kriterien TST1–TST4, keine offenen Muss-Findings.
Bericht SHA-256:
`5a30b53bcd27e7adf91e035667c93765e7955df45df35eac13aff2800edfa385`.

Eigene und unabhängige frische Gates am unveränderten Kandidaten:

| Gate | Tests | Failures / Errors / Skips | deklarierte Szenarien | positive Pflichtoperationen |
| --- | ---: | --- | ---: | ---: |
| Standard, SCRIPT/local | 696 | 0 / 0 / 0 | 135 | 21 |
| SCRIPT/full | 752 | 0 / 0 / 0 | 213 | 44 |
| JAR/full | 752 | 0 / 0 / 0 | 213 | 44 |
| IMAGE/full | 757 | 0 / 0 / 0 | 220 | 44 |

Je Gate Maven- und Auditexit 0. Standard tatsächlich ohne Modus-/Run-ID-/
Kandidatenproperty mit unerreichbarem Docker-Socket; 17-stellige gemeinsame
Maven-Run-ID in JUnit und Prozessen. Die Vollmatrix besteht aus drei
vollständigen sauberen Mavenläufen einschliesslich GeoTools und echten
Backing-Services. 174 COMMON-Inputbäume bytegleich. Alle Archive nach
späteren clean-Läufen erneut erfolgreich auditiert, JUnit-Zahlen und
Runtimebytes durch Codex unabhängig nachgeprüft.

Negativproben 17/17/21 für SCRIPT/JAR/IMAGE bestanden; vier
Publikationskontrollen, 21 Reviewstartertests, 73 Dokumentationswerkzeugtests,
Linkprüfung, 19 ausgeführte Dokumentationsbefehle, 44 generierte Tabellen
und Quarto-/Thoth-Render bestanden. Erhaltene ZIP-Checksum-/ILI-Paketstartprobe
auch direkt mit dem entpackten Launcher bestanden. Kein echter Deploy.
Zwei Unterbrechungen ausschliesslich im externen eigenen Evidenzhelfer
(CWD-Eventlogs und fehlende Wiederaufnahmevariable) sind samt Korrektur in
`orchestrator-interruption.json` dokumentiert; keine Produktkorrektur,
kein stiller Testskip, alle tatsächlichen Restprüfungen ausgeführt.

Unabhängiges Image:
`sha256:5b407a5399ead724540a1fdbdb168dd9fb5d54012d76c03a4c91a5dd8b3fe844`.
Runtimeinventar SHA-256:
`85439e843b5c34414aaa629fe50b56c62e1cbfeed768aabe7796525d4d9b6f2c`,
126 tatsächliche Runtime-Dateien. Der neue externe Reviewerjob liefert
SCRIPT A/B und im selben unveränderten Image B/C, jeweils exakt
`externer-inhalt-ä\n|review-counterprobe-<Marker>`. Keine Job-JAR-Paketierung;
kaputter unbenutzter Descriptor. Geprüfte Quellhashes passen zu den Reports.
Eigene Modus-/Altruntime-Manipulationen scheitern nach gültiger Baseline.
Die frühere E12-Lücke wurde erneut unabhängig reproduziert (alt Exit 0,
aktueller Guard Exit 1).

OpenShift-relevante Bedingungen am finalen Kandidaten erneut real geprüft:
unbekannte UID `1001234567:0`, read-only Root/Quelle, frische Schreibvolumes,
SQLite/DuckDB, CapEff 0, NoNewPrivs 1; kein passwd-Write oder privilegierter
Workaround. Host macOS/ARM64, Container Linux/ARM64, PostGIS teilweise
AMD64-emuliert. Keine echte OpenShift-Clusteradmission/PVC-, AMD64-native-
oder entfernte CI-Ausführung behauptet.

OpenCode 2.2.5 / direkter DeepSeek Flash 1017, Zuordnung am 02.11.2026
verifiziert. Tatsächlicher privater Reviewprozess und Orchestrator vollständig
beendet, beide Exit 0; alle 38 Kriterien erfüllt, beide HEADs sauber und
unverändert; keine relevanten Kinderprozesse oder eigenen Container/Netze
übrig. Technische und fachliche Rohvalidierung durch Codex abgeschlossen.

Rohbelege:
`/Users/stefan/sources/gretl-review-output-TST4-1-3655842006d9/` und
`/Users/stefan/sources/gretl-script-first-evidence/TST4-candidate-3655842006d94a7cada138e91d88aa143190606b/`.
Diese unveränderte Reviewablage und Statusfortschreibung sind ein reiner
Nachweiscommit; der oben genannte Produkt-SHA bleibt der abgenommene Stand.
**TST1 bis TST4 abgeschlossen.** Kein fünfter Review, kein Push, Merge,
Release, Image-Push oder produktiver Job.
