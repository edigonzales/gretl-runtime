# Lokale Scriptfragmente

Mit Java 25 und der gebauten GRETL-Distribution, vom entpackten Distributionsroot:

```sh
./bin/gretl run examples/script-includes/job.groovy --help
./bin/gretl check examples/script-includes/job.groovy
./bin/gretl plan examples/script-includes/job.groovy
./bin/gretl run examples/script-includes/job.groovy --param output=target/script-includes-output.txt
cmp examples/script-includes/data/input.txt target/script-includes-output.txt
```

`prepare` komprimiert die lokale Eingabe. `verify` dekomprimiert das Ergebnis,
vergleicht den gesamten Inhalt bytegenau und schreibt ihn an den Ausgabeort.
`dependsOn` gibt die Ausführungs- und Resultatabhängigkeit an; die Includeordnung
allein erzeugt keine Abhängigkeit. Alle Fragmente teilen eine Definition, einen
Run und einen gemeinsamen Cleanupabschluss.

`apply from:` bezieht sich auf die einbindende Quelldatei: `prepare.groovy`
findet `local/compress.groovy` im selben `tasks/`-Verzeichnis. `file` und
`ctx.file` behalten den Jobroot als Ressourcenbasis. Der `path`-Parameter
`output` bleibt relativ zum Start-CWD. Imports und Groovy-Lokalvariablen gelten
je Script; GRETL-Parameter und Tasknamen teilen den Jobscope.

Das Paket nimmt den Includeabschluss automatisch als Bytecode auf. Datenressourcen
bleiben ausdrücklich im Deskriptor ausgewählt. Zum isolierten Start ohne Quellbaum:

```sh
GRETL="$(pwd)/bin/gretl"
INPUT="$(pwd)/examples/script-includes/data/input.txt"
WORK="$(mktemp -d)"
cp -R examples/script-includes "$WORK/sources"
"$GRETL" package "$WORK/sources/job-package.json" --output "$WORK/job.jar"
rm -r "$WORK/sources"
(cd "$WORK" && java -jar job.jar --help)
(cd "$WORK" && java -jar job.jar --plan)
(cd "$WORK" && java -jar job.jar --param output=verified.txt)
cmp "$INPUT" "$WORK/verified.txt"
```

Die Löschung betrifft nur die gerade angelegte temporäre Kopie. Das fertige JAR
braucht Java 25 und seine Parameter, aber keine Groovyquellen oder GRETL-Installation.
Beim Paketieren laufen keine Scriptbodies. Hilfe, Check und Plan laden im
SCRIPT-Betrieb vertrauenswürdigen Top-Level-Code, aber keine Taskkonfiguration,
Tasks oder Cleanups. Der reine Direktivenleser führt keinen Jobcode aus.

Die aktuelle IDE-Sicht bleibt **partiell**: Der Modeler zeigt bekannte lokale Tasks
und den Includeaufruf als Codebereich. Eine eindeutig lokale skalare Feldänderung
bleibt möglich; Rename, Löschungen, Abhängigkeits- und referenzbildende Änderungen
können wegen unbekannter Fragmentinhalte abgelehnt werden. Vollgraph,
Include-Navigation und Rename über Fragmente sind nicht zugesichert. Ein allein
geöffnetes Shared-Fragment hat keinen bewiesenen Einbinderkontext; dort den
Texteditor verwenden. Die Featureanleitung im zugehörigen Quellcheckout unter
`docs/implementation/script-includes/README.md` beschreibt Syntax, Fehler und Subprojekte.
