# P1: gemeinsame Include-Syntax und Anwendungsmodell

Auftrag `include-tooling`, Fassung 1.0. Auftragsbasis
`9773c42ddb12a7343cf5f9ad7b6be81b489abb30`; feste Phasebase ist der unabhängig
abgenommene P0-Kandidat `d2dccba942a749a3f270a7dbfbdb11ffe2814a1a`.
Kandidat erst nach Commit extern im Handoff. Prüfe IT01–IT04, IT06, IT23 im
Komponenten-/Extraktionsumfang von P1, keine P2–P4-Freigabe vorwegnehmen.

Lieferung: neutrales `gretl-script-syntax`, kompatible Runtime-Readerfassade und
Quellnamen, Adapter an den sicheren Toolingparser mit bestehendem Tokenledger,
SourceApplication/IncludeLink/RegistrationEvent und geordnete Expansion.
Der komplette Resolver bleibt P2; ein gültiges erkanntes Include bleibt bis dahin
UNRESOLVED_INCLUDE/partiell und strukturelle Edits sind weiterhin gesperrt.
RegistrationSequence beweist keine Reihenfolge bei opakem Kontrollfluss oder
unaufgelösten Links. ASTrollenbefunde für Fragmentdefaults sind Daten, keine IO.

Prüfe die unveränderten Helper-/Map-/Shadowingverträge gegen den alten Reader,
BOM/CRLF/Emoji-Ranges, Platform-Classloader, Recovery/Budgets, Nichtausführung,
Anwendungsidentität/Ressourcenbasis und Apply-Site-Reihenfolge. Die Paketprobe ist
fällig, weil neue Syntaxklassen transitiv in Runtime/Compiler/Server landen.

Finaler gezielter Mavenbefehl (Mavenrepo im Reviewerhandoff separat):

```sh
./mvnw -B -Dmaven.repo.local=/Users/stefan/.cache/gretl-include-tooling/m2 \
  -pl gretl-dist,gretl-lsp -am \
  -Dtest=IncludeSyntaxTest,ApplyDirectiveReaderTest,FragmentGraphResolverTest,FragmentLoaderTest,GroovyLoaderTest,GroovyProjectLoaderTest,GretlProjectParserTest,GretlDocumentParserTest,ParserAdversarialTest,RecoveryAndLiteralContractTest,ParseBoundsAndCoordinatesTest,ApplyPartialCoverageTest,ApplyEditCompatibilityTest,CliFragmentProcessTest,FragmentJarProcessTest,FragmentCompilerTest \
  -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=ApplyIncludeServerIT \
  -Dfailsafe.failIfNoSpecifiedTests=false clean verify
```

Der Testfilter benennt ausdrücklich die gesamte kleine Toolingparserfamilie,
Runtime-Reader-/Loaderregressionen, echte SCRIPT-/JARprozesse und die bisherige
Include-Edit-/stdio-Sicherheitsgrenze. Keine Fachoperations-/Dienstmatrix.
Prüfe die frischen erwarteten Ziel-XMLs, nicht nur Reactor-Exit0. Lies bei
JAR-/SCRIPTfällen deren fachliche Assertions (Quellbaum gelöscht, reale Ausgabe,
Bytecode ohne spätere Groovykompilierung, alte Metadaten) selbst.

Zusätzlich `python3 -m unittest discover -s tools/include-tooling -p 'test_*.py' -v`
für Adapterhärtung P0-M1 und Negativcanaries. Der Adapterexport benutzt eine
reguläre Datei, weil die CLI grosse JSONausgaben an einer Pipe abschneiden kann.

Entwickle deine eigene neue Gegenprobe und führe sie wirklich aus. Keine
Produkt-/Originalteständerungen; eigene Java-/Pythonproben extern. Prüfe die
neuen Modulgrenzen und die tatsächlichen Artefakte. Neue Session, tatsächlicher
Abschluss, eigene Tests, vollständige Kriterienmenge und Originalbericht/JSON
nach Plan §9.4 sind Pflicht. P0-Belege im angegebenen implementer_evidence-Nachbar
sind historisch und kein neuer P1-PASS.
