# Phase 1: Korrektur des ersten Prüfkandidaten

Datum: 25. Oktober 2026. Agent A: `/root`. Kandidat: df0f00d7f74ea285c7c9eb476389d8cb55ee4aa2.

Dieser Bericht ergänzt den unveränderten [ersten Implementierungsbericht](phase-1-implementation.md). Ausgang für die Korrektur ist Produktkandidat `79867a03ada856011ffb2d3dc52da44b78bcb5a1` mit dem reinen Berichtscommit `abef07d`. Phase 2 und Phase 3 sind weiterhin nicht begonnen. Ein unabhängiges PASS steht bei Übergabe dieses Berichts aus.

## Befund und Korrektur

Agent B führte das veröffentlichte Ili2gpkgImport-Hilfebeispiel aus und erhielt korrekt UNKNOWN_CONFIGURATION_FIELD für dessen fälschliches `database`-Feld. P1-08 war damit nicht erfüllt, obwohl die fachlichen JAR-Tests grün waren.

Die Hilfe enthält jetzt ein vollständiges GeoPackage-Rezept mit Outputparameter, `databaseFile`, `dataFiles`, `modelDirectories`, `defaultSrsCode` und Defaulttarget. Die generischen DuckDB-Beispiele verwenden ebenfalls das angebotene `databaseFile`. Der neue Test `ValidatorLocalJobIT.publishedGeoPackageExampleImportsTransferData` übernimmt das öffentliche Beispiel unverändert, paketiert es regulär und startet einen eigenen `java -jar`-Prozess. Ein unabhängiger SQLite-Leser bestätigt befüllte Fachdaten. Die Referenz bindet zusätzlich das vorhandene ausführbare GPKG-Testrezept direkt ein.

Das bereits als auskommentiert dokumentierte historische FTP-Szenario hat jetzt auch das passende JSON-Boolean `historicallyDisabled: true`. Die 86 aktiven Originalfälle und ihre Zuordnung bleiben unverändert.

## Prüfungen

- Gezielter Maven-Reactorlauf mit `OperationMetadataTest` und dem neuen Hilfe-JAR-Test: erfolgreich; Log `help-fix-targeted.log` im ersten Phase-1-Evidenzverzeichnis.
- `./mvnw -B clean verify`: 413 Tests, 0 Failures, 0 Errors, 0 Skips; erfolgreich.
- `./mvnw -B -Pgeotools-probe,backing-services clean verify`: 447 Tests, 0 Failures, 0 Errors, 0 Skips; erfolgreich.
- Öffentliche Hilfe der gebauten Distribution: korrigiertes vollständiges Beispiel; Ausgabe `ili2gpkg-help.txt`.
- Zusätzliche separate JAR-Proben am vorherigen Kandidaten bestätigen Konfigurationsvorrang der Korrelation, Environment-Fallback, keine Übernahme in den nächsten Lauf sowie Zurückweisung von Überlänge und Steuerzeichen vor dem Taskstart. Original-XML-Bytes stimmen; JAR-SHA bleibt unverändert. Die Evidenz steht unter `/tmp/gretl-improvement-evidence/phase1/manual-correlation/evidence.json`.

Root-Prüfungen und gesicherte XML-Berichte, Coverage und Jobartefakte stehen unter `/tmp/gretl-improvement-evidence/phase1-revision-1/`. Der Standardstand wurde vor dem Profil-clean gesichert. `tested-commit.txt` verknüpft beide Läufe mit dem Kandidaten. Java-, Maven- und Docker-Umgebung entsprechen dem ersten Bericht.

Separat gestartete Packager-, Launcher- und Job-JVMs sind weiterhin nicht instrumentiert. Ihre fachliche Ausführung zählt nicht zur JaCoCo-Abdeckung. Es gibt kein Prozentgate und keine abgeschwächten oder deaktivierten Pflichtfälle. Echte Testdienstcontainer wurden ausgeführt; das GRETL-Produktimage wurde nicht gestartet.

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
| cancel-false-false-9b4ad176-b315-4261-87f5-bbdc7c7429d6 | JAR | `285cfa565574d820feba03203101bc4c524e5f6d5482b38e9d6fcf5f71d5fa26` | 20 | False |
| cancel-false-true-59668d51-9931-48d2-a1af-78de5300246f | JAR | `192c4e8f6c2c2e7f60bd6785c73027178098e7930cc2e9342451a75a193f2597` | 143 | False |
| cancel-true-false-a40fa8c1-dcde-4933-906a-c372dcf7d3b7 | SCRIPT | `75f9bf9ef596fe6858aa89c99e0a6450ba3610b8591112bc5f45a482b179d375` | 20 | False |
| cancel-true-true-66ff48be-92df-4276-93b6-72d4dd3b4cf2 | SCRIPT | `75f9bf9ef596fe6858aa89c99e0a6450ba3610b8591112bc5f45a482b179d375` | 143 | False |
| csv-unsupported-table-ba60640c-c667-4680-b3ac-bfd25ae0461e | JAR | `2d86acd6327c0adb678e7c3b11f615bbe2207dea3be3adf66e0fb9c62ae486a3` | 10 | False |
| database-cancel-ceb97c76-8742-44a2-8408-f443e6f962c3 | JAR | `47f60ad6c9097f15427eb131b3cd7625ece533cbca69b97eae0bcbbefe6f7c7b` | 143 | False |
| database-chain-f8480123-efbb-480b-8864-90468eb5b841 | JAR | `334d8d3ac19295e10b784fc88970bec3a09b57e6924a39890e2d030ffc1dd942` | 0 | False |
| failed-shutdown-false-e7e55d82-f199-42e0-874a-e72be8cc6570 | JAR | `dedda093890b2a7b11fae2870e6aaa4312267fb0218b8cd368ea988a1dfa6ca1` | 143 | False |
| failed-shutdown-true-5ceaa2d4-6a93-4d4b-bb90-c9ecdcf1162d | SCRIPT | `75f9bf9ef596fe6858aa89c99e0a6450ba3610b8591112bc5f45a482b179d375` | 143 | False |
| gpkg-help-example-85d61331-a746-4bc4-ba8d-618c742b8689 | JAR | `34cfb005e09f59ab9d7879159bb23b69a129377237aeabcaaf77bb11a477d301` | 0 | False |
| gpkg-missing-table-7efd506a-3fa6-4d91-b26b-c542de104255 | JAR | `a06dd138be832a9afe9c7855db7972179c0661716382847111d7d82d15ac2f67` | 10 | False |
| gpkg-srid-3c5b2e1f-81e4-4ce9-be42-7ff6523fc557 | JAR | `19c4f0545916c185f2d6ad15aef8e7fcc50fff448f970a76918fcf1597408187` | 0 | False |
| gpkg-srid-640a3ebb-2b9d-4ec1-b7a1-b32930d41e46 | JAR | `73a92d4104a483d55aa2e9f960f32cc0b9fed6eebc5605053b0ff633486ddfa2` | 0 | False |
| invalid-csv-068e3200-d798-479a-8e39-0b4876efd4ae | JAR | `6d064547d715774d76574839c341d7f4051d5d88185f51260be7dcc8ffb76c79` | 10 | False |
| invalid-model-repository-3c05b5f4-5ece-4072-920b-c648160b478f | JAR | `c58a6f43979d79f273c7583f75bf4c0363d04f441ff817e2d5fe87746d8a059e` | 10 | False |
| original-gzip-983c5c5f-63cb-462b-9723-6c3d3ba48897 | JAR | `150a37b2680733d617447e42e51bf9de67afcb8bc426162c5b7b8b976cddd6be` | 0 | False |
| postgis-geometry-contract-5c71ac7f-0ec9-4c37-9873-b2f19962aaf9 | JAR | `4d5ee4979a8d72e664edcb1f99589b4c2f08648b6cbc09e6183e9c7cc89a194b` | 10 | False |
| runner-timeout-false-cc4c2378-77a8-4ac7-83f4-70a6298d834b | JAR | `bb38fc86b4a77a8e802c4ce2c4be2bac61e78ddf04cace2a7a88463270d5684b` | 143 | True |
| runner-timeout-true-ccce94dd-1026-4254-be87-2a2f0f9eb5fd | SCRIPT | `75f9bf9ef596fe6858aa89c99e0a6450ba3610b8591112bc5f45a482b179d375` | 143 | True |
| valid-csv-path-30f59746-2d28-4473-9f7c-43919495ef10 | JAR | `1a5f969ffc88a525ece591912c87c3f19c426e1784405605701f4b4782d64c43` | 0 | False |
| valid-csv-string-05e40fa2-ded3-4522-a663-c5a1b8264bae | JAR | `4eb9489577a01ef66d36e8c3dcc15e10985153a7a302b08a42956b9a26c35b25` | 0 | False |
| valid-gpkg-path-f39868c4-4238-4adb-ab69-2973a1fd4b03 | JAR | `8144fba266c77bd9d8abb04757e3de21a061152417b31b73c9c355d81f86c9d5` | 0 | False |
| valid-gpkg-string-2a67b521-1183-4381-97cf-920519286ce3 | JAR | `be232f818149ccbf9382d9e636af6534d289505fed89b19308058752400ef85c` | 0 | False |
| valid-json-path-eebd356c-4c8f-4f7d-a25d-e374d6e16317 | JAR | `9300784ab7620407ae3281d5c127fd790090a061d1915a3296d3209b8e61b614` | 0 | False |
| valid-json-string-8dd748e0-4b7c-4a12-bf1a-9d4c95b2424f | JAR | `7142a6d649eaf3fcedff046672d39861aa29e06acdf578c339caabfec249da78` | 0 | False |
| valid-shp-path-60a2c7a1-b70e-41b2-b8c0-ea8b6550817c | JAR | `9ab18ab852bc7567d06d58aff1c472fa689c51508cc8110f97d04ce50dddcc12` | 0 | False |
| valid-shp-string-06c456b6-dbb4-462d-818a-0f4c073e77bc | JAR | `08a64f6bfe46ccbf643a744bba6908c39f150d41408efc279fbf69c52bdd08e6` | 0 | False |
| wrong-gpkg-table-604108f1-c6b9-4916-ae77-801322c29c29 | JAR | `928d2281d937ffd2f4151b4428b69995b0640c0b2ba0b254547b9e18534a7a08` | 10 | False |
