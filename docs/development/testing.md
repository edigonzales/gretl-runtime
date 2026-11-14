# Tests

```sh
./gradlew test
./gradlew serviceTest
./gradlew jarTest
python3.12 scripts/verify-runtime.py
```

`test` führt lokale Tests aus. `serviceTest` wählt die mit `docker`
markierten JUnit-Tests für echte Dienste. `jarTest` wählt unabhängige
Job-JAR-Prozesse. Die vollständige Qualifikation startet über Gradle auch
`imageTest` und richtet dafür ein isoliertes Docker-Netz und einen privaten
Arbeitsordner ein.

SCRIPT trägt die fachliche Abdeckung der Standardoperationen. Details stehen
in den [Jobtests](job-artifact-tests.md). Die Testberichte liegen unter
`build/reports/tests/` im jeweiligen Modul.
