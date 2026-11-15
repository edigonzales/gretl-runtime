# Tests

```sh
./gradlew test
./gradlew serviceTest
./gradlew jarTest
./gradlew imageTest
./gradlew fullCheck
```

`test` führt lokale Tests aus. `serviceTest` wählt die mit `docker`
markierten JUnit-Tests für echte Dienste. `jarTest` wählt unabhängige
Job-JAR-Prozesse. `imageTest` baut das generische Image und führt die IMAGE-Suite
mit Docker aus. `fullCheck` umfasst alle Gradle-Prüfungen, die GeoTools-Probe,
die drei spezialisierten Testtasks und die ZIP- und TAR-Distribution. Die
separate Dokumentationsprüfung läuft mit `./gradlew docsCheck` und Python 3.12.

Für IMAGE benötigt der Host einen nicht privilegierten Unix-Benutzer, ein
für den Docker-Daemon sichtbares `/tmp` und denselben Daemon für die Docker-CLI
und Testcontainers. JUnit legt einen privaten Workspace an und verwendet
dessen UID/GID für die Bind-Mounts. PostgreSQL und S3 laufen im privaten
Testcontainers-Netz. Host-HTTP verwendet `host.docker.internal` auf macOS
und `services:host-gateway` auf Linux.

Die Tests beenden ihre Jobprozesse und entfernen die Jobcontainer nach dem
Auslesen des Containerstatus. JUnit schliesst die Dienste und das Netz und
löscht den Workspace am Suite-Ende. Ryuk ist der Rückfall bei hartem Abbruch
des Testworkers. Einmalig pro Worker vergebene Labels beschränken sein
Cleanup auf Ressourcen dieses Laufs. Dafür müssen Ryuk und der Docker-Daemon
erreichbar bleiben. Bei hartem Ende des Workers können lokale Prozesse
bis zur Containerentfernung und private temporäre Verzeichnisse darüber
hinaus bestehen bleiben.

SCRIPT trägt die fachliche Abdeckung der Standardoperationen. Details stehen
in den [Jobtests](job-artifact-tests.md). Die Testberichte liegen unter
`build/reports/tests/` im jeweiligen Modul.
