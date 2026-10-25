# Phase 1: Stabile Lebensdauer des FTP-Testdienstes

Datum: 25. Oktober 2026. Agent A: `/root`. Kandidat: 52675c526a0edad52d7005c1d7e874fb7d3ca276.

Dieser Bericht ergänzt die unveränderten vorherigen Implementierungs- und Prüfberichte. Ausgang ist Produktkandidat `df0f00d7f74ea285c7c9eb476389d8cb55ee4aa2` mit reinem Berichtscommit `ec55c86`. Phase 2 und Phase 3 sind weiterhin nicht begonnen. Unabhängiges PASS steht bei Übergabe aus.

## Unabhängiger Befund F2

Agent B bestätigte die Korrektur des öffentlichen GPKG-Beispiels mit eigenem tatsächlichem CLI-Hilfe-/JAR-/SQLite-Orakel. Sein neuer Standardlauf war erfolgreich (413/0/0/0). Im vollständigen Profil scheiterte jedoch der FTP-Cleanup nach dem bewusst falschen Login: Connection refused; der Serverlog endet mit `process has died, quitting`. Tatsächlich ausgeführt wurden 443 Tests mit 0 Failures, 1 Error und 0 JUnit-Skips; die vier nachgelagerten GeoTools-Tests wurden wegen des Reactorabbruchs nicht ausgeführt. Der neue Gesamtstatus war FAIL, nicht PASS. Bericht und Servernachweise bleiben unverändert erhalten.

## Korrektur der Fixture

Das gepinnte FTP-Image bleibt unverändert. Sein tatsächlich aus dem Image gelesener Starter startet vsftpd im Hintergrund und überwacht mit pidproxy die letzte von `pgrep vsftpd | tail -n 1` gefundene PID. Diese Auswahl kann bei bereits vorhandenen Verbindungen einen kurzlebigen Worker treffen. Der beobachtete Logschluss stammt aus dieser Überwachung. Die genaue PID des fehlgeschlagenen Containers liess sich nach seiner Entfernung nicht mehr sichern; eine verlorene Docker-Portzuordnung ist durch die Logs nicht belegt. Fünf zusätzliche direkte Standardimage-Proben blieben gesund und reproduzierten den intermittierenden Fehler nicht; sie werden nicht als fehlgeschlagene Produktprüfungen ausgegeben.

Die Fixture lässt den vorhandenen Image-Entrypoint weiterhin den Benutzer initialisieren, gibt ihm danach aber den expliziten vsftpd-Befehl mit `background=NO` und denselben Passive-/Adressoptionen. Der Server läuft dadurch direkt unter dem vorhandenen Initprozess; die Auswahl einer PID aus mehreren Prozessen entfällt. Die Containerreferenz ist final. Der bisherige lease()-Neustartfallback mit unbelegtem Portmapping-Kommentar ist entfernt. Fehler im Cleanup werden weiterhin diagnostiziert und lassen die Prüfung fehlschlagen.

Der vorhandene falsche-Passwort-Test verlangt jetzt zusätzlich die konkrete FtpTaskException samt Anmeldediagnose und zwingend einen Report ohne Secret. Danach müssen ein unabhängiges Listing und ein gültiger Runtime-Login auf demselben Server erfolgreich sein. Ein Start-/Classpathfehler kann den Negativfall nicht mehr erfüllen. Kein Test wird ausgelassen oder abgeschwächt.

## Tatsächlich ausgeführte Prüfungen

- Gezielter Reactor-/FTP-Lauf nach der Korrektur: alle vier FTP-Tests erfolgreich.
- Fünf weitere getrennte gezielte Reactor-/FTP-Läufe mit jeweils frischem Container: jeweils vier FTP-Tests erfolgreich, einschliesslich falschem und anschliessend gültigem Login auf demselben Server. Logs/XML und `fixed-repeats.json` unter `/tmp/gretl-improvement-evidence/ftp-diagnosis/`.
- Eigene direkte Vordergrund-Prozessprobe: fünf FTP-530-Ablehnungen und fünf anschliessende gültige Logins, gleicher weiterhin laufender Container/Initprozess. Die Prozessliste zeigt den Vordergrundmaster direkt unter tini. Evidenz `ftp-diagnosis/foreground/`. Ein erster Probeversuch endete am selbst gewählten 3-Sekunden-Clienttimeout vor Abschluss; er ist unter `foreground-short-timeout/` gesichert und wird nicht als Produktfehler oder bestandene Prüfung gewertet. Der abgeschlossene Versuch verwendet 15 Sekunden für die verzögerte Authentifizierungsablehnung.
- `./mvnw -B clean verify`: 413 Tests, 0 Failures, 0 Errors, 0 Skips; erfolgreich.
- `./mvnw -B -Pgeotools-probe,backing-services clean verify`: 447 Tests, 0 Failures, 0 Errors, 0 Skips; erfolgreich, einschliesslich aller vier GeoTools-Tests.

Die vollständigen Rootlogs, XML-Berichte, Coverage und Artefakt-/Prozessevidenz sind unter `/tmp/gretl-improvement-evidence/phase1-revision-2/` gesichert; der Standardstand vor dem nächsten clean. Beide tested-commit.txt nennen den Kandidaten. Die Java-/Maven-/Docker-Umgebung entspricht den vorangehenden Berichten.

Produktcode, Operationen, DSL-/Resultatverträge und normative Spezifikation sind seit df0f00d unverändert. Die vorangehenden Fach-, Status-, Korrelations- und JAR-Regressionen bleiben erhalten. Der erste und zweite unabhängige FAIL-Bericht werden unverändert mitversioniert. Ein GRETL-Produktimage wurde nicht gestartet; echte PostgreSQL/PostGIS-, FTP- und S3-Testdienste gehören zum Vollprofil.

Separat gestartete Packager-, Launcher- und Job-JVMs bleiben uninstrumentiert und zählen nicht als gemessene Coverage. Testanzahl und Line-/Branchmessung sind getrennte Aussagen. Kein Prozentgate.

## Gemessene Coverage im Vollprofil

| Produktmodul | Lines covered / total | Branches covered / total |
|---|---:|---:|
| gretl-api | 110 / 114 | 23 / 26 |
| gretl-runner | 1796 / 2078 | 754 / 1061 |
| gretl-groovy | 498 / 733 | 148 / 238 |
| gretl-i18n | 24 / 31 | 13 / 20 |
| gretl-logging | 923 / 1103 | 385 / 590 |
| gretl-packager | 508 / 698 | 204 / 310 |
| gretl-job-runtime | 0 / 150 | 0 / 52 |
| gretl-dist | 20 / 26 | 5 / 8 |
| gretl-cli | 1080 / 1326 | 379 / 561 |
| gretl-basic-tasks | 2058 / 2647 | 732 / 1240 |
| gretl-core-tasks | 1099 / 1382 | 288 / 488 |
| gretl-ilivalidator | 115 / 175 | 20 / 42 |
| gretl-interlis-tasks | 2824 / 3667 | 1092 / 1825 |

## Tatsächlich gestartete Identitäten im Vollprofil

| Verzeichnis unter profiles/job-evidence | Modus | SHA-256 | OS-Exit | Timeout |
|---|---|---|---:|---|
| cancel-false-false-b532f20d-b8c1-4d55-a3de-4bbb756ad9e0 | JAR | `dc01f38abd3acad9e2f9124c1f3db5cc6239dc767f0ebc9a350400adeb6a817a` | 20 | False |
| cancel-false-true-cb9c3d16-1e17-4420-9f8d-6f7e76ec1883 | JAR | `a30eaaf523df7bc0c2c94f92592dbd8a208a8bd4b84f4d40e34f6faabf84bf41` | 143 | False |
| cancel-true-false-9ae79953-4faf-46a9-b4fe-74771c84da01 | SCRIPT | `75f9bf9ef596fe6858aa89c99e0a6450ba3610b8591112bc5f45a482b179d375` | 20 | False |
| cancel-true-true-07034d4b-2bde-4ba9-ab5c-b2f5428a6e63 | SCRIPT | `75f9bf9ef596fe6858aa89c99e0a6450ba3610b8591112bc5f45a482b179d375` | 143 | False |
| csv-unsupported-table-7a582c8d-807f-4b88-a1b0-0b69071e39cf | JAR | `c4a7f1b4aec707fe19aac4cc29c25dd6d7cff5b96a9ec148506d6b36917de63b` | 10 | False |
| database-cancel-6ea70dbe-dd7f-4ddd-9584-c2f88940aaea | JAR | `d0cd8da17e6fe42fcb8861bd639f573e5d68e75a23e628436f20407d4165244e` | 143 | False |
| database-chain-b95cc250-b776-4ee2-9d9e-1d4974bbde92 | JAR | `73cf7a34aefb9f3e6165a23aed1d67cfe1f11e51807b9dcfea22eccd482e6c43` | 0 | False |
| failed-shutdown-false-471e0cf9-2d05-4b48-b5af-11d363a529bd | JAR | `6f38d78a153e9bebd039a27c50cac9890e066605b545202e4b0c601c1b0160c8` | 143 | False |
| failed-shutdown-true-891e7c08-40e3-43af-a8fb-69c6b76843b6 | SCRIPT | `75f9bf9ef596fe6858aa89c99e0a6450ba3610b8591112bc5f45a482b179d375` | 143 | False |
| gpkg-help-example-0a3a335d-0fa3-4a95-ad5b-bb6fa73d5439 | JAR | `7c2f940e2e3751139c82ff8ada1c4ccee5c0f98acbed15605de308411fe7c952` | 0 | False |
| gpkg-missing-table-440d12bb-817e-459f-8d28-0ab244ac74cb | JAR | `afbb552263286996ab676f0256cbce39585d732e3627b5d570496791c80ebbbf` | 10 | False |
| gpkg-srid-b264ef72-bc11-48b1-acfb-b85ed65adbee | JAR | `e9072e5111064b46468a3d7c13fd62110bf986f0659bd31b8646c733c7ee12cd` | 0 | False |
| gpkg-srid-cff7cb32-aa39-4ab5-a2b8-e660a93db802 | JAR | `ba53664bca0b1422b7c9434e293758a7988248d7f6f7f2232b05693246a18a2c` | 0 | False |
| invalid-csv-6e2bbf3a-ad5e-4391-916f-ef89e3ab998b | JAR | `d16911c43e5c926ebe365a91e721893704ed0b2f10c2da9f460dbfae28cab223` | 10 | False |
| invalid-model-repository-15a1f86b-e8b4-4327-b772-ee742c3420e5 | JAR | `e6dbeadcf064cdc880f4f7c7b2078aeb7fe71dad8faacde02d8d887f94f16aa0` | 10 | False |
| original-gzip-4dca98c4-5348-4418-b386-34053ca2133c | JAR | `a303bbc7013e4a3162899a4af4ad4bf2fd5c540708ff406c0ec7e31fcfd26c0f` | 0 | False |
| postgis-geometry-contract-718fc680-523a-4b6c-af53-15d601dfeb7c | JAR | `24a632c0f89814064a4596661107760a7ea532986f824f24788331c31d6aa935` | 10 | False |
| runner-timeout-false-e9226b42-0514-4aae-a6fa-26e98c9c4599 | JAR | `6f397389875a80237efad2f380e3ee0f7903a5e84cbceccefcd9052751d1e1d8` | 143 | True |
| runner-timeout-true-a8804eab-87e4-4ac3-88a7-e5fd6f6212da | SCRIPT | `75f9bf9ef596fe6858aa89c99e0a6450ba3610b8591112bc5f45a482b179d375` | 143 | True |
| valid-csv-path-8b10a29a-cfb2-43b0-9d08-fbba35961f9d | JAR | `57d3c391303eaa6a677e6d240f0326d2236876a323e1a7138ee99af82f2f76a0` | 0 | False |
| valid-csv-string-9a64ec8d-7c91-44b6-929a-b4e5057a554b | JAR | `8046f690480d634c5e1d9728834407ee509246e6265c1da9d9885bc93b17b283` | 0 | False |
| valid-gpkg-path-058de50b-48ca-4365-8d6e-81b2686f7ed3 | JAR | `29abeab7e515113b6ec8dd4b436e963f373a152e89e6f26cc68c6f4ad9c5c3e7` | 0 | False |
| valid-gpkg-string-692ff724-9ab6-4081-962c-497d9badf4a3 | JAR | `63976bdd280a3fa63eabdf3e720e499ccbad14f8f7372ace64ef27af003259d9` | 0 | False |
| valid-json-path-0e6eddf3-5222-4363-a137-dc935223bdf7 | JAR | `d77286492a7b8902bb918fb9952c3059ae9b3bd6a28d00da477b25f5bee75b33` | 0 | False |
| valid-json-string-0dd84e9b-4722-4cef-8b83-aea5fcb01ed8 | JAR | `9a6da93a942cf3ca7a270c333f053a779559ca617e2d19236c36c265260a9ffc` | 0 | False |
| valid-shp-path-f6122d51-60af-4ec3-83fd-ab75594c740f | JAR | `1fb4305744b58c5a4c80453d7799a35f1547fcce045d4f8347cb6a255261cf13` | 0 | False |
| valid-shp-string-fad8495b-5b2b-4b9b-bc57-331a0a667775 | JAR | `b304f2981da327366cf1e9e339dd9f175f94147ff70444916ce611b884f6ca54` | 0 | False |
| wrong-gpkg-table-979bf001-cb1b-41da-8634-f805b41c80dc | JAR | `ea8f663cdb4d50bf096ab09233ad6c3387b3345952e48ca30b88e5ff957259a3` | 10 | False |
