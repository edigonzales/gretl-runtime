# M6-Prüfbericht: Optionaler Job-Fat-JAR-Packager (Stufe C)

**Stand:** 18. Oktober 2026  
**Basis:** Spezifikation 4.1, Kapitel 09/10/12  
**Projektversion:** `0.1.0-M6` (historischer Meilensteinbericht; die veröffentlichte Snapshot-Version ist `0.1.0-SNAPSHOT`)  
**Status:** erster technischer Stufe-C-Packager umgesetzt. Die
M5-Anwenderabnahme bleibt bestanden; die formale M5-Release-/Pilotentscheidung
bleibt offen. M6 ist kein Native-Image- und kein Publisher-Meilenstein.

## 1. Lieferumfang

Neu hinzugekommen sind:

- `gretl-packager` — strikter Paketdeskriptor, Groovy-Vorkompilation,
  JAR-Zusammenbau, Service-/Groovy-/Lizenz-Merges und Kollisionsprüfung,
- `gretl-job-runtime` — Einstiegspunkt `ch.so.agi.gretl.job.JobJarMain`,
  Paketmetadaten, Ressourcenmaterialisierung und `JobSource`-Anbindung,
- `gretl package descriptor.json --output job.jar` in der CLI,
- M5-Abschlussdokumentation mit bestandener TC-U-Abnahme.

Das JAR enthält den kompilierten Job, deklarierte Groovy-Helfer,
Jobressourcen, Runtime, Standardtasks und den qualifizierten Klassenpfad. Es
startet mit `java -jar` allein; Maven, Gradle und eine installierte
GRETL-Distribution sind am Zielrechner nicht nötig.

## 2. Paketvertrag

Beispiel: `gretl-dist/src/main/dist/examples/ili-validator/job-package.json`

```json
{
  "schemaVersion": 1,
  "name": "ili-validator",
  "version": "0.1.0-M6",
  "runtimeVersion": "0.1.0-M6",
  "entryScript": "job.groovy",
  "sources": [],
  "resources": ["data", "models"],
  "dependencies": ["ch.so.agi.gretl:gretl-ilivalidator:0.1.0-M6"]
}
```

Regeln:

- `entryScript` liegt unmittelbar im Deskriptorverzeichnis.
- Quellen und Ressourcen bleiben unter dieser Wurzel; absolute Pfade, `..`,
  Symlinks und unbekannte Deskriptorfelder werden abgelehnt.
- Dependency-Versionen müssen fest sein; `LATEST`, `RELEASE` und Bereiche
  werden abgelehnt. Ein `0.1.0-SNAPSHOT`-Packager akzeptiert auch
  Snapshot-Dependencies; ein solches Paket ist nicht als qualifiziertes
  Release-Artefakt freigegeben.
- Beim Packen wird der Job **nicht** ausgeführt; Toplevelcode läuft erst beim
  Start.
- Ressourcen werden als Lesebestand nach `META-INF/gretl/job-resources/`
  eingebettet und beim Start in ein privates temporäres Jobroot materialisiert.
- `file('...')` bleibt relativ zum materialisierten Jobroot; deklarierte
  `path`-Parameter bleiben relativ zum Start-CWD.

## 3. Laufzeitverhalten

`JobJarMain`:

- beantwortet `--version` aus statischen Paketmetadaten vor dem Jobladen,
- stellt bei Bedarf dieselbe Jobhilfe (`--help`) und denselben Planmodus
  (`--plan`) wie der Scriptmodus bereit,
- verwendet dieselben CLI-Optionen, Parameter-/Secretregeln, Logs, Reports und
  Exitcodes,
- lädt keine weiteren Taskbibliotheken aus dem Netz.

## 4. Nachweise

```sh
./mvnw -o clean verify
./mvnw -o -Pgeotools-probe verify
```

| Prüfung | Ergebnis |
|---|---|
| Standard-`verify` | **286 Tests**, `BUILD SUCCESS` (historischer M6-Stand; nach Entfernen der M0-Testbaugruppe aktuell 270) |
| GeoTools-Profil | **290 Tests**, `BUILD SUCCESS` (aktuell 274) |
| Frisches Maven-Repository | **290 Tests**, `BUILD SUCCESS` (historisch) |
| `gretl-packager`-Tests | 4 |
| Distributions-Failsafe | 7 |
| Dist-Checksum-Test | 1 |

Abgedeckte Fälle:

- **TC-J01:** derselbe JAR-Job mit zwei Parametersätzen und zwei Reports,
- **TC-J02:** Toplevel-Marker wird beim Packen nicht ausgeführt, beim Start
  schon,
- **TC-J03:** `java -jar` aus fremdem CWD ohne installierte Runtime/Maven,
- **TC-J04:** IliValidator-Ressourcen (`data`, `models`) nach Verlagerung des
  JARs verfügbar,
- **TC-J05:** eigener `FileSize`-Task aus `lib/extensions` im gepackten JAR,
- **TC-J06:** Service-Metadaten, Custom-Task und JDBC/ILI-Bestandteil,
- **TC-J07:** gleichnamige Klassen mit unterschiedlichem Inhalt werden mit
  beiden Herkunftsartefakten abgelehnt,
- **TC-J08:** Deskriptor-Traversal, Symlinks und unbekannte Felder werden
  früh abgelehnt,
- **TC-J09:** `--version`, `--help`, `--plan`, fehlender Pflichtparameter und
  Fehler-/Exitcode-Verhalten,
- **TC-J10:** Paketmetadaten, Quellen, Ressourcen und Dependency-Inventar,
- **TC-J11:** zweiter Start desselben JARs erzeugt einen neuen Lauf.

Zusätzlich manuell geprüft:

- IliValidator-JAR: gültiger XTF, Code 0, Runbericht,
- SQL-JAR: PostgreSQL 17, `examples/sql/job-package.json`, Code 0 und
  Tabellenzeile `1|Beispiel erfolgreich ausgeführt`,
- SHA256-Prüfung der extrahierten Distribution.

## 5. Bewusste Grenzen des ersten Profils

- Der Packager löst **keine Maven-Koordinaten** aus `dependencies` auf. Er
  bündelt den bereitgestellten qualifizierten Runtime-Klassenpfad (in der
  Distribution `lib/runtime`, `lib/tasks`, `lib/extensions`) und prüft, dass
  die deklarierten Artefaktnamen darin vorkommen. Dadurch enthält das JAR den
  vollständigen Standardkatalog statt einer minimalen Auswahl.
- Der Zusammenbau erfolgt mit einem kleinen getesteten Java-Merger, nicht mit
  einem Maven-Shade-Mojo. Service-, Groovy- und Lizenz-Merges sowie
  Klassen-/Ressourcenkollisionen sind implementiert.
- Die JAR-Laufzeit materialisiert Jobressourcen in ein temporäres Verzeichnis
  und entfernt es nach dem Lauf; ein dauerhaftes Ressourcen-Jobroot wird nicht
  behauptet.
- Kein Native Image, kein Publisher, kein XtfToGpkg.
- Die M5-Release-/Pilotentscheidung bleibt offen.

## 6. Artefakt

- Distribution: `gretl-dist/target/gretl-runtime-0.1.0-M6-bin.zip`
- ZIP-SHA-256: `e02461a032deef5a5691e4dfc63b6db159510d9b28af5ac48fd15dc296cfa843`
- Beispiel-JAR: aus `examples/ili-validator/job-package.json` erzeugt und mit
  `java -jar` aus fremdem CWD erfolgreich gestartet.

## 7. Fazit

Stufe C ist als erster technischer Packager lauffähig: Ein Job kann
vorkompiliert, als eigenständiges JAR ausgeliefert und ohne installierte
Runtime gestartet werden. Die Tests prüfen die zentralen Paketierungs- und
Laufzeitrisiken. Die fehlende Maven-Koordinatenauflösung und der vollständige
Runtimebestand sind vor einer breiten C-Freigabe bewusst nachzubessern.

## Snapshot-Veröffentlichung

Die produktiven Maven-Artefakte und die Scriptdistribution werden unter
`0.1.0-SNAPSHOT` nach `https://jars.interlis.guru/snapshots` veröffentlicht.
Die Anleitung steht in [publish-snapshots.md](publish-snapshots.md).
