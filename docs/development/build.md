# Build und Distribution

GRETL verwendet Gradle 9.9.1 mit Groovy DSL. Die Java-Toolchain ist JDK 25.
Die Jobruntime verwendet Groovy 6.0.0. Für die VS-Code-Extension wird Node 22
benötigt.

```sh
./gradlew build
./gradlew installDist distZip distTar
./gradlew :gretl-lsp:shadowJar :gretl-vscode:vsix
```

Die Installation liegt in `gretl-cli/build/install/gretl-runtime` und enthält
`bin/`, `lib/` und `examples/`. ZIP und TAR liegen in
`gretl-cli/build/distributions`. Eigene SCRIPT-Erweiterungen können unter
`lib/extensions` installiert werden.

```sh
./gradlew :gretl-cli:dockerBuild
./gradlew docsCheck
```

`dockerBuild` erzeugt das lokale Image `gretl-runtime:local`.
Für die Referenz erzeugt `scripts/generate-task-docs.sh` die Taskdokumentation.
Die ausführbaren Beispiele und Renderpfade prüft
`python3.12 scripts/check-doc-examples.py --help` mit Quarto und Biblios.

Gradle veröffentlicht Bibliotheken und die beiden Distributionsarchive mit
`maven-publish`. Mit `./gradlew publishToMavenLocal` lässt sich die lokale
Publikation prüfen. CI veröffentlicht auf `main` die qualifizierten Archive
unter `ch.so.agi.gretl:gretl-dist` und vergleicht die per HTTP geladenen
Archivbytes mit ihren SHA-256-Werten.

Weitere Befehle stehen in der [Testanleitung](testing.md).
