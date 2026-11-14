# Snapshot-Publikation

Die GitHub Action qualifiziert GRETL mit denselben Gradle-Tasks wie lokal.
Sie sichert ZIP und TAR samt SHA-256. Auf `main` übernimmt Gradle diese
Archive in die Maven-Publikation. Nach dem Upload lädt der Smoke-Test beide
Archive per HTTP und vergleicht ihre SHA-256-Werte. Aus dem ZIP startet er
einen externen Groovy-Job.

Das Ziel ist `https://jars.interlis.guru/snapshots`. Die Action bezieht
`MAVEN_USERNAME` und `MAVEN_PASSWORD` aus GitHub Secrets. Die Distribution
behält die Maven-Koordinate `ch.so.agi.gretl:gretl-dist` mit den Classifiern
`bin` für ZIP und `bin-tar-gz` für TAR. Der LSP wird zusätzlich mit
`jar-with-dependencies` bereitgestellt. VSIX und Testberichte sind CI-Artefakte.

Mit `./gradlew publishToMavenLocal` lassen sich POMs und Artefakte lokal
erzeugen und prüfen.
