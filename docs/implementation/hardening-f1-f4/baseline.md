# Ausgangsstand und Umfang

Stand: 26. Oktober 2026, Europe/Zurich.

Der Nutzer hat `gretl-runtime-startprompt-f1-f4-neu.md` ausdrücklich als die
Auftragsdatei bestätigt. Deren Verweis auf `spec.md` und die Abschnitte 8.1,
9 und 11 ist im gelieferten Bestand nicht auflösbar. Es wurden keine fehlenden
Detailanforderungen oder historischen Reviewtexte erfunden. Massgeblich sind
die ausdrücklich enthaltenen F1–F4-Anforderungen sowie Spezifikation 4.1.

| Feld | Tatsächlich festgestellt |
| --- | --- |
| Deklarierte Analysebaseline | `288137f116f2d861a1648b5eeaf889d463a5ea6c` |
| Branch bei Übernahme | `main` |
| HEAD bei Übernahme | `e5f3c576289c5d45381201fda26b263a10786a93` |
| Arbeitsverzeichnis bei Übernahme | sauber, keine Nutzeränderungen |
| Arbeitsbranch | `codex/hardening-f1-f4` |
| Änderung gegenüber Analysebaseline vor diesem Auftrag | ausschliesslich die hinzugefügte Auftragsdatei; Produktcode unverändert |
| JDK | Eclipse Temurin 25.0.3+9, macOS aarch64 |
| JDK-Pfad für Befehle | `/Users/stefan/.sdkman/candidates/java/25.0.3-tem` |
| Maven | Wrapper, Apache Maven 3.10.0 |

F1, F2 und F3 wurden am übernommenen Produktcode reproduziert; keines ist
`ALREADY_FIXED`. F4 wird erst nach bestandenem Gate A implementiert.

Die Änderungen berühren Runner, Logging und die gemeinsame Job-Runtime sowie
deren Tests. Task-API, DSL, Taskkatalog, Modul-POMs, fachliche Tests und
selektive Paketierung bleiben erhalten. Es gibt keine neuen produktiven Tasks,
Produktionszugriffe, Veröffentlichungen, Pushes oder IMAGE-Ausführung.

Rohlogs und spätere Build-/Prozessartefakte liegen ausserhalb von Git unter
`/tmp/gretl-hardening-evidence/`. Die eingefrorenen Kandidaten und tatsächlichen
Ergebnisse werden in den phasenspezifischen Nachweisen erfasst.
