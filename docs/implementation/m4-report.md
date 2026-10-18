# M4-Prüfbericht: IliValidator und Stufe B

> **Historischer Bericht.** Dieser Bericht dokumentiert den Stand von M4
> (`0.1.0-M4`, 267 Standard-/271 Profil-Tests). M5 ergänzt Distribution,
> Inventar, Prüfsummen und Beispiele; aktuell ist der
> [M5-Bericht](m5-report.md). Die Anwenderabnahme ist offen.

**Stand:** 18. Oktober 2026  
**Basis:** Spezifikation 4.1, Kapitel 04, 07, 09 und 16  
**Projektversion:** `0.1.0-M4`  
**Status:** reale IliValidator-Integration umgesetzt; öffentliche Anwenderfreigabe
(M5) und Paketierung (M6) nicht begonnen.

## 1. Auftrag und Abgrenzung

M4 sollte die reale INTERLIS-Validierung als gleichberechtigte Taskart
integrieren: lokale Modelle und XTF-Transfers, unterstützte Einstellungen,
strukturierte Diagnosen, einen geschützten Validierungsbericht, saubere
Listener-/Ressourcenfreigabe und die Aufnahme in die Scriptdistribution.

Nicht Bestandteil waren Publisher, XtfToGpkg, ein Job-Fat-JAR, Native Image,
eine eigene Validator-JVM oder eine GeoTools-Fachfähigkeit. A bleibt als
kleinere Modul-/Distributionsstufe nutzbar; M4 erweitert den Katalog um B.

## 2. Lieferumfang

Neu ist das Taskmodul **`gretl-ilivalidator`** mit 4 Java-Quellen und 4 Klassen:

| Klasse | Rolle |
|---|---|
| `IliValidatorOperation` | Operationstyp, Request/Builder, realer Handler |
| `ValidationResult` | `file`, `reportFile`, `warnings` |
| `IliValidatorTaskException` | sichere Fehlerdetails, Reportverweis, Zähler |
| `IliValidatorProvider` | ServiceLoader-Provider für `IliValidator` |

Verwendet wird die reale **`ch.interlis:ilivalidator:1.15.0`** mit
`ch.ehi:ehibasics:1.4.1` sowie den transitiven ili2c-/iox-Beständen. Die
Bibliothek wird aus `https://jars.interlis.ch` aufgelöst; die Version ist im
Root-POM fixiert.

Die Scriptdistribution `gretl-runtime-0.1.0-M4-bin.zip` enthält jetzt
`HttpDownload`, `Extract`, `SqlExecutor` und `IliValidator` sowie lokale
Modell-/XTF-Beispiele. Der Erweiterungsbereich `lib/extensions` bleibt
unverändert.

## 3. Taskvertrag

Eingabefelder:

- `dataFile` (Pflicht, `path`): vorhandene XTF-Transferdatei,
- `modelDirectory` (Pflicht, `path`): vollständiger lokaler Modellbestand,
- `validationConfig` (optional, `path`): ausdrücklich angegebene lokale
  Validierungskonfiguration.

Ausgabefelder:

- `file`: validierte Eingabedatei,
- `reportFile`: abgeschlossener aufrufbezogener Validierungsbericht,
- `warnings`: Anzahl der aus tatsächlichen `ADAPTION`-Engineereignissen
  erfassten Warnungen.

Der Handler prüft Daten-, Modell- und Konfigurationspfad vor der Enginearbeit.
Die Validierung verwendet `Validator.runValidation` mit
`SETTING_ILIDIRS`, optional `SETTING_CONFIGFILE` und deaktiviertem
Standardlogger. Der Bericht liegt im privaten Invocation-Workspace
(`workPath('validation-report.txt')`), wird mit 0600-Rechten geschrieben und
enthält die erfassten Engineereignisse sowie die abgefangene Validatorausgabe.

Bei Erfolg wird eine INFO-Diagnose mit dem geschützten Berichtsartefakt
gemeldet. Bei einem fachlich ungültigen Transfer wird der Bericht zuerst
geschrieben, dann eine ERROR-Diagnose mit Artefakt erzeugt und der Task mit
einer sicheren `IliValidatorTaskException` beendet. Zähler und Pfad stehen im
Fehler; die zentrale Logmeldung enthält nur Anzahl und Phase, keine
Datensatz-/Feldwerte.

Der Engine-`LogListener` wird pro Invocation registriert und im `finally`
identisch entfernt. Die Validatorausgabe wird während des Aufrufs in einen
begrenzten Berichtspuffer umgeleitet und danach wiederhergestellt; dadurch
gelangen keine unstrukturierten Validatortexte auf stdout/stderr.

## 4. Tests und Nachweise

### 4.1 Standard- und Profilprüfung

```sh
./mvnw -o clean verify
./mvnw -o -Pgeotools-probe verify
./mvnw -Pgeotools-probe verify -Dmaven.repo.local=/tmp/m4-fresh-final
```

| Prüfung | Ergebnis |
|---|---|
| Standard-`verify` | **267 Tests**, 0 Fehler, `BUILD SUCCESS` |
| GeoTools-Profil | **271 Tests**, `BUILD SUCCESS` |
| Frisches lokales Maven-Repository | **271 Tests**, `BUILD SUCCESS` |
| Distributionslauf mit IliValidator | Launcher, Katalog, gültiger und ungültiger XTF |

Standard-Testverteilung:

| Modul | Tests |
|---|---:|
| `gretl-api` | 17 |
| `gretl-runner` | 115 |
| `gretl-groovy` | 49 |
| `gretl-logging` | 12 |
| `gretl-basic-tasks` | 20 |
| `gretl-cli` | 32 |
| `gretl-ilivalidator` | 3 |
| `gretl-m0-qualification` | 16 |
| `gretl-dist` | 3 |
| **gesamt** | **267** |

### 4.2 IliValidator-Tests

- gültiges lokales Modell und gültiger XTF: Erfolg, `warnings=0`,
  Bericht existiert, INFO-Diagnose mit restricted Artefakt,
- ungültiger XTF ohne Pflichtattribut: `IliValidatorTaskException`, Fehlerzähler
  > 0, Bericht mit der echten Engine-Meldung „Attribute name requires a value“,
  ERROR-Diagnose mit restricted Artefakt,
- fehlende Daten-/Modellpfade: sichere Fehler vor der Enginearbeit,
- aufeinanderfolgende Aufrufe: `EhiLogger`-Listenerzahl vor/nach der Validierung
  identisch; kein Listener-Leak,
- Provider-/Metadatenprüfung: `IliValidator`, `gretl.ilivalidator`,
  Eingabe-/Ergebnisfelder und Beispiel,
- Distributions-IT: gültiger und ungültiger XTF aus dem entpackten ZIP; der
  ungültige Lauf endet mit Code 10 und `VALIDATION_FAILED` im Report.

### 4.3 Gelieferte Distribution

- `gretl-ilivalidator-0.1.0-M4.jar` wird unter `lib/tasks` mitgeliefert.
- `ilivalidator`, `ehibasics`, iox-/ili2c-Bestände und Antlr liegen unter
  `lib/runtime`.
- `operations` listet `IliValidator`; Publisher und XtfToGpkg bleiben
  unregistriert.
- `examples/ili-validator` enthält Modell, gültigen und ungültigen XTF sowie
  ein lauffähiges Beispiel.

## 5. Abdeckung der Stufe B

| Fall | Status | Nachweis |
|---|---|---|
| TC-I01 gültiges lokales Modell/XTF | **bestanden** | echte Engine, gültiger Transfer |
| TC-I02 ungültiges XTF | **bestanden** | echte Diagnose, allgemeiner Taskfehler |
| TC-I03 fehlendes Modell | **bestanden** | präziser Modellpfadfehler, kein stiller Ersatz |
| TC-L13 EHI-/Iox-Event/Cause | **teilweise** | Engine-Ereignisse/Exception werden erfasst; keine separate EHI-Quelle |
| TC-L14 Listener success/failure | **bestanden** | Listenerzahl vor/nach beiden Läufen identisch |
| TC-L15 Sampling grosser Validierungsmengen | **nicht umgesetzt** | kein Massen-Fixture/Sampling |
| GeoTools-Probe | **bestanden** | gesondertes Profil mit 4 Tests, keine Taskregistrierung |

## 6. Bewusste Grenzen und Restrisiken

- **Kein EHI-/Iox-Bridge-Adapter:** Der Task bindet den echten
  `ilivalidator`-`LogListener`; eine separate EHI-Quelle mit
  Objektklassen-/Zeilenfeldern wurde nicht beschafft. Der Bericht enthält die
  tatsächlichen Engineereignisse, aber keine zusätzliche EHI-Feldstruktur.
- **Warnungszähler:** Er zählt `ADAPTION`-Ereignisse. Ein eigener
  Warnungs-Fixture-Transfer existiert nicht; der gültige Fixture erzeugt 0
  Warnungen. Die Zählung ist gegen die Engineereignisart implementiert, nicht
  gegen Textmuster.
- **Validierungskonfiguration:** Der Pfad wird geprüft und an
  `SETTING_CONFIGFILE` übergeben; eine semantisch geprüfte Konfigurationsdatei
  ist nicht Teil des Fixturebestands.
- **stdout/stderr:** Die Validatorausgabe wird für die Dauer des Aufrufs
  abgefangen und im geschützten Bericht abgelegt. Das ist ein bewusster
  prozessweiter sequenzieller Adaptereinsatz; bei künftig paralleler
  Taskausführung wäre ein eigener Prozess oder eine engere Bridge nötig.
- **Keine separate Validator-JVM:** Der gemeinsame qualifizierte Klassenpfad
  wird verwendet. Ein Worker-/Prozessmodell ist nicht eingeführt.
- **Kein Publisher/XtfToGpkg/Job-JAR:** unverändert ausserhalb des MVP.
- **Öffentliche Anwenderfreigabe:** M5 mit TC-U01–TC-U04 und Fachanwendern
  bleibt offen.

## 7. Fazit

Die reale INTERLIS-Validierung ist als reguläre Taskart integriert und mit
gültigen/ungültigen lokalen Fixtures, Listener-Lebensdauer, Berichtsartefakt,
Distributionslauf und gesondertem GeoTools-Profil belegt. Die Standardprüfung
besteht mit 267 Tests, das GeoTools-Profil mit 271 Tests.

Nächster Schritt ist **M5**: Script-MVP und Anwenderoberfläche freigeben,
vollständige Beispiele mit der Distribution ausführen und die kleine
Anwenderprüfung TC-U01–TC-U04 durchführen. M5 wurde nicht begonnen.
