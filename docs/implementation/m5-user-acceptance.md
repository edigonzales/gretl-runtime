# M5-Anwenderabnahme: Protokoll

> **Historisches M5-Protokoll.** Es bezieht sich auf die Distribution
> `0.1.0-M5`; M6 ist in [m6-report.md](m6-report.md) dokumentiert.

**Stand:** 18. Oktober 2026  
**Status:** **alle vier TC-U-Fälle bestanden.** Die externe Abnahme wurde mit P1,
P2 und P3 durchgeführt. TC-U02 und TC-U03 wurden nach den Nachbesserungen durch
P2 bzw. P1 erfolgreich nachgetestet. Die M5-Anwenderabnahme ist damit
abgeschlossen; die formale Release-/Pilotentscheidung bleibt offen.

## 1. Zweck und Grenze

Die qualitative Prüfung nach Kapitel 09/10 prüft, ob die Einstiegsvorlagen aus
Kapitel 13 für normale Jobpflege ausreichen. Sie ersetzt keine statistische
Studie und keine Produktionsfreigabe. Drei Personen ausserhalb des
Runtime-Entwicklungsteams haben die Aufgaben mit lokalen Testdaten bearbeitet.

## 2. Vorbereitung

- entpackte Scriptdistribution `gretl-runtime-0.1.0-M5-bin.zip`
- lokaler Test-HTTP-Server für das ZIP-Beispiel
- lokale PostgreSQL-Testdatenbank für das SQL-Beispiel
- die Beispiele unter `examples/` und die Anleitung aus Kapitel 13
- die externen Testpersonen P1, P2 und P3

## 3. Aufgaben und Beobachtung

### TC-U01 — SQL-Rezept und Scriptdatei wechseln

**Aufgabe:** `examples/sql/job.groovy` mit der Testdatenbank starten, danach die
vorgegebene Scriptdatei gegen eine zweite lokale Datei tauschen.

**Beobachtung:** Wird die richtige Datei ohne Änderung an SPI, Imports oder
Runtimecode ausgewählt? Bleibt die konfigurierte Datenbank `edit` erhalten?

**Status:** bestanden — P1 hat den Test erfolgreich ausgeführt.

### TC-U02 — ZIP-Quelle und Ziel anpassen

**Aufgabe:** `examples/download-extract/job.groovy` auf eine andere lokale
ZIP-Quelle und ein anderes Ausgabeverzeichnis anpassen.

**Beobachtung:** Reichen Hilfe und Parameter? Kann die Person die Herkunft der
Download-Datei und den Zweck von `dependsOn` in eigenen Worten erklären?

**Status:** bestanden nach Re-Test — P1 hat den Test erfolgreich ausgeführt.
Dabei wurde die Möglichkeit erwartet, eine Datei am Zielort ausdrücklich zu
überschreiben. Die Option `HttpDownload.overwrite` wurde ergänzt; P2 hat den
Re-Test mit der nachgebesserten Distribution erfolgreich ausgeführt.

### TC-U03 — Feld-/Parametertippfehler beheben

**Aufgabe:** Absichtlich `outputDirecotry` oder einen falschen Parameternamen
verwenden und den Lauf anhand der Meldung korrigieren.

**Beobachtung:** Nennt die Meldung Schritt/Parameter, Quelle und
Korrekturhinweis, ohne dass Java-Stacktrace-Kenntnisse nötig sind?

**Status:** bestanden nach Re-Test — P3 hat den Test erfolgreich ausgeführt.
Nicht verständlich war, warum die Fehlermeldung anders formatiert ist als die
übrigen Logmeldungen. Die Textdarstellung wurde vereinheitlicht; P1 hat den
Re-Test mit der nachgebesserten Distribution erfolgreich ausgeführt.

### TC-U04 — Jobdatei verschieben und Pfade erklären

**Aufgabe:** Die Jobdatei in ein anderes Verzeichnis verschieben, eine externe
Eingabe übergeben und das Standardziel erklären.

**Beobachtung:** Sind Jobressource, externer Eingabepfad, Arbeitsdatei und
Startziel anhand der Anleitung unterscheidbar?

**Status:** bestanden — P3 hat den Test erfolgreich ausgeführt.

## 4. Technische Ersatzprüfungen

Diese Prüfungen belegen die technische Lauffähigkeit, **nicht** die
Fachanwenderfreundlichkeit:

- gültiger und ungültiger XTF über die entpackte Distribution,
- HTTP-/ZIP-Beispiel über die entpackte Distribution,
- FileSize-Erweiterungs-JAR aus `lib/extensions`,
- optionale/no-data Action über die entpackte Distribution,
- SQL-Beispiel mit lokaler PostgreSQL-Testdatenbank,
- Hilfe, `operations`, `check`, `plan` und Fehlermeldungen über die CLI-Tests,
- neuer `overwrite`-Pfad für `HttpDownload`,
- gleiche Textstruktur für frühe Parameter- und späte Taskfehler.

## 5. Nachbesserungen aus der Abnahme

1. **TC-U02:** `HttpDownload.overwrite` ist als ausdrückliche, standardmässig
   deaktivierte Option ergänzt. Ohne die Option bleibt das bisherige Verhalten
   „keine stille Überschreibung“ erhalten. P2 hat den Re-Test bestanden.
2. **TC-U03:** Frühe CLI-/Parameterfehler und Laufzeit-Taskfehler verwenden
   dieselbe Textstruktur mit Zeitstempel, Level, Titel, Code und eingerückten
   Feldern. JSON-/JSONL-Ausgaben bleiben unverändert. P1 hat den Re-Test
   bestanden.

## 6. Abbruch-/Wiederholungsregel

Alle vier Aufgaben sind mit externen Testpersonen bestanden. TC-U01 und TC-U04
wurden von P1 bzw. P3 durchgeführt; TC-U02 und TC-U03 wurden nach den
Nachbesserungen von P2 bzw. P1 erfolgreich wiederholt. Wiederkehrende Blockaden
werden vor einer Oberflächenfreigabe in Vorlage, Hilfe oder API korrigiert.

**Ergebnis:** M5-Anwenderabnahme bestanden; formale Release-/Pilotentscheidung
offen.
