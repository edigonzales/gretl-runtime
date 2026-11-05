# P5: unabhängiger kumulativer Schlussreview

Auftrag task-finalizers, Spec 1.0. Auftragsbasis
`9faf347f739e836976dd566bd31a65fc4ff91ac4`; feste P5-Phasebase
`e572807e6c7c4b0b2bda6a8c8892114028455ed2`. Kandidat im externen Handoff.
P0–P4 sind getrennt und maschinell validiert abgenommen. Prüfe jetzt ausdrücklich
das gesamte Featuredelta seit Auftragsbasis gegen F01–F30, nicht nur den letzten
Dokumentationsdiff. ACCEPTANCE.md ist eine Zuordnung, kein vorgegebenes Urteil.

Lies normative Dateien, Implementierung, frühere Findings/Fixes und aktuelle
Anleitungen selbst. Führe den angegebenen kumulativen gezielten Mavenbefehl,
Client-/Adapter-/Dokumentationsprüfungen und die echten Beispiel-/Renderbefehle
selbst aus. Kein Rootclean oder Dienstvollgate; der ausdrückliche lokale Vertrag
in §1.1/§8 bleibt massgeblich. Positive frische XMLzahlen, erwartete Fehlerexits,
Task-/Trigger-/Rollenergebnisse, Ergebnisbytes und Artefaktidentität prüfen.

Die gespeicherte P4-GUI-Fixture gehört zu e572807 und ist mit SHA256 gesichert.
Es gibt nach diesem P4-PASS keine Runtime-/LSP-/Hoständerung. Prüfe diese Bindung
und die vorhandenen nativen Belege unabhängig, ohne sie auf einen anderen SHA
umzuetikettieren. verify_examples.py startet ihre unveränderten Groovyquellen
separat als SCRIPT. Die aufgezeichneten Inputbytes liegen in parts/input.txt;
für file('input.txt') werden dieselben Bytes ausdrücklich als Jobrootressource
bereitgestellt. gui-input-provisioning.json und das ursprüngliche Manifest müssen
zeigen, dass keine Groovydatei dafür geändert wurde. P3 deckt echte sourcefreie
JARs und beobachtetes SIGTERM ab; keine neue IMAGEbehauptung bei unverändertem Pfad.

Die Beispielprüfung fährt Erfolg und erwarteten Fehlerexit 10 aus Checkout und
Distribution, das dokumentierte JAR und den echten Anleitungs-Codeblock. Die
Dokumentationsprüfung rendert Quarto und die aktuelle AsciiDoc-Referenz mit Thoth,
generiert 44 unveränderte Feldtabellen und prüft lokale Links. Geänderte Beispiele
müssen tatsächlich laufen; statische Links allein reichen nicht.

Zwei unveränderte CatalogSemanticParityTest-Fixturefehler wurden zusätzlich am
P3-SHA reproduziert und sind ausdrücklich kein PASS. Sie wurden nicht gefixt,
geskippt oder durch schwächere Assertions ersetzt. Beurteile diese Abgrenzung.

Entwickle und führe mindestens eine eigene neue substanzielle Gegenprobe zum
Gesamtfeature aus. Kein Implementierer-Input als fertige neue Reviewerprobe.
Getrackte Bytes unverändert halten; Rohberichte und Proben extern. Originalbericht
und Verdict nach §9.4 einschliesslich vollständiger Kriterien, Modell-/Session- /
SHA-/Digestbindung und ehrlicher Findings. Nur ohne offene BLOCKER/MAJOR oder
fällige NOT_EXECUTED kann das Schlussurteil ABGENOMMEN lauten. Keine Publikation.
