# Herkunft der Phase-2-Fixtures

Die [Dateiliste](phase-2-fixture-provenance.json) erfasst alle kanonischen
Fixture-Dateien mit SHA-256 und Herkunft. Das ursprüngliche
[386-Dateien-Inventar](original-scenarios.json) bleibt unverändert erhalten.
129 Dateien des aktuellen Bestands sind bytegleich zu Original-GRETL-Dateien;
weitere Dateien stammen aus dem angegebenen gretl-next-Commit oder ausdrücklich
versionierten offiziellen Modell-Snapshots. Kanonische DSL-Jobs und die
entsprechenden isolierten SQL-Äquivalente sind als solche gekennzeichnet.

Quellen:

- `sogis/gretl@a9222dc0ffd08f8f76570e998101db041822df71`,
  `gretl/src/integrationTest/`, zusätzlich README desselben Commits als lokale
  HTTP-Downloadantwort. [Original-MIT-Lizenz](licenses/sogis-gretl-MIT.txt).
- `edigonzales/gretl-next@08bd11e97a54f32f8702d34ff16fb39bec6ea046`,
  `gretl-core/src/test/resources/` (insbesondere die Administrativ-GPKG-Datei
  und lokal ergänztes SimpleTable-Modell), sowie die bereits vorhandene
  Classpath-XSL-Datei aus `src/main/resources/xslt/`.
  [Original-MIT-Lizenz](licenses/gretl-next-MIT.txt); die IO-Bibliothek führt
  deren Lizenzhinweis auch im JAR mit.
- Offizielle CHBase-/INTERLIS-Modelle mit URLs, Versionen und Hashes in
  [phase-2-local-models.json](phase-2-local-models.json). Bei inzwischen nicht
  mehr unter dem ursprünglichen URL erreichbaren Modellversionen ist der
  übernommene, eingecheckte Snapshot des offiziellen Repositorycaches
  ausdrücklich vermerkt. Tests greifen ausschliesslich auf die versionierten
  Dateien zu und benötigen diesen privaten Cache nicht.

Die ursprünglichen ilidata-Repositories für MetaConfig und MOpublic liegen
bereits unter den Original-Jobfixtures und werden mitsamt Dateien unverändert
übernommen. Es wird keine aktuelle produktive Datendownload-URL benötigt.
Originale Gradle-Builds bleiben Referenzmaterial; der neue Jobbestand verwendet
nur die aktuelle Runtime-DSL und den regulären aktuellen Packager.
