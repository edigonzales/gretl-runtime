# Tests

```sh
./gradlew test
./gradlew serviceTest
./gradlew jarTest
python3.12 scripts/verify-runtime.py
```

`test` führt lokale Tests aus. `serviceTest` wählt die mit `docker`
markierten JUnit-Tests für echte Dienste. `jarTest` wählt unabhängige
Job-JAR-Prozesse. `fullCheck` bündelt die Gradle-Prüfungen einschliesslich `imageTest`.
Für die vollständige Qualifikation startet der Python-Wrapper Gradle mit
dem optionalen GeoTools-Projekt und den IMAGE-Eingaben. Er legt ein eigenes
Docker-Netz und einen privaten Ordner für die Bind-Mounts an, übergibt UID,
GID und den plattformspezifischen Hostzugang und räumt die Ressourcen des
Laufs auch bei Abbruch auf.

Ein direkter Gradle-Aufruf von `fullCheck` setzt diese IMAGE-Eingaben bereits
voraus. Ein eigenständiger Gradle-Einstieg müsste ihren gesamten Lebenszyklus
einschliesslich Abbruch und Bereinigung übernehmen. Dafür bleibt der
bestehende Python-Wrapper der Einstieg.

SCRIPT trägt die fachliche Abdeckung der Standardoperationen. Details stehen
in den [Jobtests](job-artifact-tests.md). Die Testberichte liegen unter
`build/reports/tests/` im jeweiligen Modul.
