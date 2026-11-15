# Fachliche Jobtests mit SCRIPT, JAR und IMAGE

SCRIPT ist der Hauptweg für fachliche Jobtests. Die Tests starten externe
Groovy-Dateien und Projekte über die installierte CLI und prüfen Ausgaben,
Exitcodes und Berichte. Die Diensttests verwenden echte Backing-Services.

```sh
./gradlew test
./gradlew serviceTest
./gradlew jarTest
python3.12 scripts/verify-runtime.py
```

JAR-Tests paketieren ausgewählte Jobs und starten sie in einem separaten
Verzeichnis mit `java -jar`. Sie prüfen unter anderem Standardtasks,
Validatorfunktionen und die Behandlung zusätzlicher Bibliotheken.

IMAGE-Tests verwenden das mit Gradle gebaute generische Image. Sie prüfen
externe read-only Jobquellen, schreibbare Ausgaben, Betrieb unter einer
nicht privilegierten UID, Cancellation und echte Backing-Services. Die
[Containeranleitung](../../gretl-dist/src/main/docker/README.md) enthält
Startbeispiele und OpenShift-Hinweise.
