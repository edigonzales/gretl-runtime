# M7-Vorbereitung: Release-/Pilot-Härtung

**Stand:** 21. Oktober 2026
**Status:** Release-Kandidat in Vorbereitung; Linux-x86-64-Nachweis als
GitHub-Actions-Workflow angelegt, aber in dieser Arbeitsumgebung nicht
ausgeführt. Die M5-Release-/Pilotentscheidung bleibt eine Betreiberentscheidung.
Standardprüfung: 384 Tests; GeoTools: 388; Backing-Services: 28; kombiniert:
416 Tests.

## 1. Umfang

- Maven-Koordinatenauflösung im Job-Packager
- GeoTools-Guard (`ban-geotools`) und ZIP-Prüfung der Distribution
- Prüfung der Distribution auf 44 Tasks
- Linux-x86-64-Verifikationsworkflow
- Release-/Pilotunterlagen

## 2. Packager

`JobPackager` löst deklarierte Maven-Koordinaten über den Maven-CLI
(`dependency:build-classpath`) auf, sofern das Artefakt nicht bereits im
Runtime-Klassenpfad liegt. Der Resolver nutzt das lokale Maven-Repository
(`~/.m2/repository`) und Maven Central; mit
`-Dgretl.packager.offline=true` wird ausschließlich lokal aufgelöst. Maven wird
nur beim Paketieren benötigt, nie beim normalen Jobstart.

Noch nicht umgesetzt:

- vollständig minimale Runtime-Auswahl (der bereitgestellte Runtime-Klassenpfad
  bleibt als Basis erhalten)
- Paketierung mit einer Maven-Installation, die den Dependency-Plugin-Cache
  bereitstellt
- Signatur/SBOM-Erzeugung

## 3. Linux-x86-64

Der Workflow `.github/workflows/release-candidate.yml` führt auf
`ubuntu-latest` aus:

1. `./mvnw -Pgeotools-probe,backing-services clean verify`
2. Distributionsbuild
3. `SHA256SUMS`-Prüfung
4. Job-Paketierung und Start aus fremdem CWD

Der Workflow muss vor einer Release-Freigabe einmal manuell ausgeführt und
begutachtet werden.

## 4. Offene Release-Bedingungen

- Linux-x86-64-Lauf im CI
- reproduzierbare ZIP-/TAR.GZ-Artefakte
- Lizenz-/SBOM-Bericht
- formale Pilotentscheidung inkl. Betreiber, Rollback und Betriebsgrenzen
- Publisher, XtfToGpkg und Native Image bleiben spätere Aufträge

## 5. Artefakte

- Distribution: `gretl-dist/target/gretl-runtime-0.1.0-SNAPSHOT-bin.zip`
- Job-Paket: `gretl package examples/ili-validator/job-package.json`
