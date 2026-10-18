# gretl-runtime

Implementierung der GRETL Runtime auf Basis der Spezifikation 4.1 in
[`docs/spec/`](docs/spec/).

**Aktueller Stand: M6 – Script-MVP plus optionaler Job-Fat-JAR-Packager.** Die
öffentliche Java-API, der sequenzielle Runner, die Groovy-6-Fassade,
Logging/Runbericht, die Tasks `HttpDownload`, `Extract`, `SqlExecutor` und
`IliValidator` sowie die Scriptdistribution sind umgesetzt. M5-Anwenderabnahme
ist bestanden. M6 ergänzt `gretl package`, vorkompilierte Job-JARs und eine
JAR-Laufzeit mit materialisierten Jobressourcen.

Die M5-Release-/Pilotentscheidung bleibt offen. Der Packager bündelt im ersten
Profil den bereitgestellten Runtime-Klassenpfad; eine Auflösung von
Maven-Koordinaten aus dem Deskriptor ist noch nicht enthalten. Publisher,
XtfToGpkg und Native Image bleiben spätere Aufträge.

- Build-Anleitung: [`docs/implementation/build.md`](docs/implementation/build.md)
- M6-Bericht: [`docs/implementation/m6-report.md`](docs/implementation/m6-report.md)
- M5-Bericht (historisch): [`docs/implementation/m5-report.md`](docs/implementation/m5-report.md)
- M5-Anwenderprotokoll: [`docs/implementation/m5-user-acceptance.md`](docs/implementation/m5-user-acceptance.md)
- M4-Bericht (historisch): [`docs/implementation/m4-report.md`](docs/implementation/m4-report.md)
- M3-Bericht (historisch): [`docs/implementation/m3-report.md`](docs/implementation/m3-report.md)
- M2-Bericht (historisch): [`docs/implementation/m2-report.md`](docs/implementation/m2-report.md)
- M1-Bericht (historisch): [`docs/implementation/m1-report.md`](docs/implementation/m1-report.md)
- M0-Bericht (historisch): [`docs/implementation/m0-report.md`](docs/implementation/m0-report.md)
- Standardprüfung: `./mvnw verify` mit JDK 25 (**286 Tests**)
- GeoTools-Profil: `./mvnw -Pgeotools-probe verify` (**290 Tests**)
- Scriptdistribution: `gretl-dist/target/gretl-runtime-0.1.0-M6-bin.zip`

Paketieren und starten:

```sh
./bin/gretl package examples/ili-validator/job-package.json --output /tmp/ili-job.jar
java -jar /tmp/ili-job.jar --report /tmp/ili-job-report.json
```
