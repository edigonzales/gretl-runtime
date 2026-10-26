# M5-Prüfbericht: Script-MVP und Anwenderoberfläche

> Historischer Meilensteinbericht: Aussagen und Modulnamen gelten für den hier
> dokumentierten Commit. Aktueller Zuschnitt: [Modulgrenzen](module-boundaries.md).

> **Historischer M5-Bericht.** M6 ist inzwischen implementiert; der aktuelle
> Stufe-C-Bericht steht in [m6-report.md](m6-report.md).

**Stand:** 18. Oktober 2026  
**Basis:** Spezifikation 4.1, Kapitel 09/10/13  
**Projektversion:** `0.1.0-M5`  
**Status:** **M5-Anwenderabnahme bestanden.** Die externe Abnahme wurde mit
P1, P2 und P3 durchgeführt. Alle vier TC-U-Fälle sind bestanden; TC-U02 und
TC-U03 wurden nach den Nachbesserungen erfolgreich nachgetestet. Die formale
Release-/Pilotentscheidung bleibt offen; es wird keine öffentliche
Produktionsfreigabe behauptet.

## 1. Auftrag und Grenze

M5 sollte A+B als verständlich verwendbaren Script-MVP bereitstellen: die
Distribution und ihr Inventar prüfen, die Beispiele aus Kapitel 13 mit der
gebauten Distribution ausführen und mit tatsächlichen Fachanwendern die
Pflegeaufgaben beobachten.

Der technische Teil ist umgesetzt. Drei externe Testpersonen (P1/P2/P3) haben
die vier Aufgaben bearbeitet. Die daraus abgeleiteten Nachbesserungen wurden
von P2 (TC-U02) und P1 (TC-U03) erfolgreich nachgetestet. Das Protokoll steht
in [`m5-user-acceptance.md`](m5-user-acceptance.md).

## 2. Technischer Lieferumfang

### 2.1 Distribution

`gretl-dist` erzeugt `gretl-runtime-0.1.0-M5-bin.zip` und `.tar.gz` mit:

- `bin/gretl` und `bin/gretl.cmd`,
- `lib/runtime` mit CLI, Groovy/Groovy-JSON, Runner, API, Logging, Logback und
  den Taskabhängigkeiten,
- `lib/tasks` mit `gretl-basic-tasks` und `gretl-ilivalidator`,
- `lib/extensions` für eigene Task-JARs,
- `inventory.json` mit Version, Modulen, Tasks, Taskartefakten,
  Dependencyversionen und Quellenhinweis,
- `SHA256SUMS` mit relativen Pfaden und SHA-256-Werten,
- Beispielen für Download/Extract, SQL, IliValidator, Token/Katalog und
  Action/NO_DATA.

Das ZIP wird aus einem gestagten `target/dist` gebaut. Die Prüfsummen werden vor
dem Assembly erzeugt und im Archiv mitgeliefert; ein Test prüft die
`bin/gretl`-Prüfsumme gegen das entpackte Archiv.

### 2.2 Ausgeführte Beispiele

| Beispiel | Ausführung | Ergebnis |
|---|---|---|
| `download-extract` | entpackte Distribution, lokaler HTTP-Server, ZIP | Download + Extraktion erfolgreich |
| `sql` | entpackte Distribution, PostgreSQL 17, `runtime.json` | Script ausgeführt, Zeile `1|Beispiel erfolgreich ausgeführt` |
| `ili-validator` | entpackte Distribution, lokales Modell/XTF | gültig Code 0; ungültig Code 10 + Report |
| `token-catalog` | entpackte Distribution, lokaler Token-/Katalogserver | Katalog gelesen, neueste Datei geladen, extrahiert |
| `action-nodata` | entpackte Distribution | optionaler Task SKIPPED; `finishJob` NO_DATA |
| FileSize-Erweiterung | `lib/extensions` im entpackten ZIP | Provider entdeckt, Dateigrösse gelesen |
| `HttpDownload.overwrite` | Handler- und CLI-Tests | vorhandene reguläre Datei nur nach Erfolg ersetzt |

### 2.3 Inventar und Prüfsummen

`inventory.json` nennt Runtimeversion, Java/Groovy, Module, Tasks,
Taskartefakte, Dependencyversionen und einen ausdrücklich als „working-tree“
markierten Quellenhinweis. `SHA256SUMS` ist Bestandteil des Archivs.

Die Prüfsummen sind keine kryptografische Signatur und ersetzen keine
Release-Signatur.

## 3. Tests und Nachweise

```sh
./mvnw -o clean verify
./mvnw -o -Pgeotools-probe verify
./mvnw -Pgeotools-probe verify -Dmaven.repo.local=/tmp/m5-final-fresh
```

| Prüfung | Ergebnis |
|---|---|
| Standard-`verify` | **280 Tests**, 0 Fehler, `BUILD SUCCESS` |
| GeoTools-Profil | **284 Tests**, `BUILD SUCCESS` |
| Frisches lokales Maven-Repository | **284 Tests**, `BUILD SUCCESS` |
| Distributions-Failsafe | 5 Tests: Launcher, Extension/HTTP, IliValidator, Action/NO_DATA, Token/Katalog |
| Distributions-Checksum-Test | 1 Surefire-Test |
| SQL-Beispiel mit PostgreSQL 17 | Code 0, Tabelle/Zeile geprüft |

Standard-Testverteilung:

| Modul | Tests |
|---|---:|
| `gretl-api` | 17 |
| `gretl-runner` | 115 |
| `gretl-groovy` | 49 |
| `gretl-logging` | 15 |
| `gretl-basic-tasks` | 23 |
| `gretl-cli` | 36 |
| `gretl-ilivalidator` | 3 |
| `gretl-m0-qualification` | 16 |
| `gretl-dist` (Surefire) | 1 |
| `gretl-dist` (Failsafe) | 5 |
| **gesamt** | **280** |

## 4. Anwenderabnahme TC-U01–TC-U04

| Fall | Status | Beobachtung |
|---|---|---|
| TC-U01 SQL-Rezept/Scriptdatei wechseln | **bestanden** | P1 wechselte die SQL-Datei erfolgreich; Alias `edit` blieb erhalten |
| TC-U02 ZIP-Quelle/Ziel anpassen | **bestanden nach Re-Test** | P1 erwartete eine ausdrückliche Overwrite-Option; P2 testete die nachgebesserte Distribution erfolgreich |
| TC-U03 Feld-/Parametertippfehler beheben | **bestanden nach Re-Test** | P3 fand die Fehlermeldung anders formatiert; P1 testete die vereinheitlichte Textdarstellung erfolgreich |
| TC-U04 Job verschieben/Pfade erklären | **bestanden** | P3 konnte Jobressource, Eingabe, Arbeitsdatei und Startziel unterscheiden |

Die technischen Ersatzprüfungen belegen die Lauffähigkeit. Die vier
Anwenderaufgaben wurden zusätzlich von externen Personen bearbeitet und nach
den Nachbesserungen erfolgreich wiederholt. Damit ist die M5-Anwenderabnahme
bestanden; eine statistische oder produktive Freigabe ist damit nicht
verbunden.

## 5. Nachbesserungen

### 5.1 `HttpDownload.overwrite`

- Neues optionales Feld `overwrite` mit Default `false`.
- Ohne die Option bleibt das Ziel geschützt; der Fehler kommt vor dem
  HTTP-Aufruf.
- Mit `overwrite = true` wird nur eine vorhandene **reguläre** Zieldatei
  ersetzt. Verzeichnisse und Symlinks bleiben unzulässig.
- Der Download läuft weiterhin in eine temporäre Datei; die alte Zieldatei
  bleibt bei HTTP-/IO-/Limitfehlern unverändert.
- Veröffentlichung mit atomarem Move und `REPLACE_EXISTING`, mit kontrolliertem
  Fallback.

### 5.2 Einheitliche Textfehler

- Frühe CLI-/Parameterfehler und Laufzeit-Taskfehler verwenden dieselbe
  Grundstruktur: Zeitstempel, Level, Titel, technischer Code und eingerückte
  Felder (`Meldung`, `Feld`, `Task`, `Quelle`, `Ursache`).
- `FEHLER:` als separater Sonderfall entfällt im Textmodus.
- JSON-Konsole und JSONL-Ereignisdatei bleiben schema-stabil.
- Fehlerklassen erscheinen nur noch als kurzer Typ, nicht als vollständiger
  Java-Stacktrace.

## 6. Bekannte Grenzen

Aus M3/M4 bleiben offen:

- Heartbeat, Progress-Koaleszenz und taskgebundene MDC-Anreicherung,
- EHI-/Iox-Feldadapter, Warnungs-Fixture und Massen-Sampling beim Validator,
- keine separate Validator-JVM,
- SQL-Commit-Unbekanntheit nach Verbindungsverlust nicht fault-injiziert,
- HTTP-Hartabbruch/Symlink-Härtung nicht vollständig fault-injiziert,
- kurze, sehr allgemeine Secretwerte können bei globaler Ersetzung weitere
  Texte treffen; strukturelle Attributschlüssel werden geschützt, eine
  vollständige DLP-Zusage besteht nicht,
- `sourceRevision` im Inventar ist im Working-Tree-Build kein Commit,
- die Prüfsummen sind nicht signiert.

Aus M5 bleibt offen:

- formale Release-/Pilotentscheidung,
- produktiver Pilot mit echten Quellen und Betreiberbedingungen.

## 7. Fazit und nächster Schritt

Der Script-MVP ist lauffähig: Distribution,
Inventar, Prüfsummen, Tasks und Beispiele wurden mit der gebauten Distribution
geprüft. Die Standardprüfung besteht mit 280 Tests, das GeoTools-Profil mit 284
Tests.

Die M5-Anwenderabnahme ist bestanden. Die formale Release-/Pilotentscheidung
ist **nicht** durch den Coding-Agenten ersetzt; sie bleibt beim Auftraggeber.
Der nächste Meilenstein ist der optionale M6-Job-Fat-JAR-Packager
(Lieferstufe C); M6 wurde nicht begonnen.

### 7.1 Pilotkandidat und Restbedingungen

Als risikoarmer technischer Pilotkandidat wird `examples/download-extract`
dokumentiert. Er deckt Download, ZIP-Extraktion, Parameter, Jobressource und
Ausgabeziel ab. Das ist **kein produktiver Pilot**.

Vor einem produktiven Piloten bleiben zu klären:

- echte Quelle/URL, TLS und Authentifizierung,
- Zielverzeichnis, Aufbewahrung, Berechtigungen und verfügbarer Speicher,
- verantwortliche Betreiber, Zeitplan und Eskalation,
- Datenschutz-/Produktionsfreigabe,
- Monitoring und Behandlung von Fehlern/Teilwirkungen.

### 7.2 Artefakt

`gretl-runtime-0.1.0-M5-bin.zip` wird als M5-Artefakt festgehalten. Der
SHA-256-Wert wird nach dem finalen Distributionslauf in diesem Bericht
eingetragen.

**ZIP-SHA-256:** `a36b1fcaaabb49b74601c8b0181ec4989d6fc6ab3f69fffb5f9e11e6464a0d72`
