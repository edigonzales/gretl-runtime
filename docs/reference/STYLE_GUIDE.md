# Typografie und technische Schreibweisen

Die Referenz verwendet das unveränderte Default-Styling von Thoth Biblios
für Schrift, Farben, Überschriften, Tabellen und Code. Die technischen Rollen
stammen aus [gretl-next](https://github.com/edigonzales/gretl-next/tree/main/docs/reference)
und sind auf die Runtime-DSL angepasst. Sie erhalten keine eigene Typografie;
Backticks und starke Feldnamen verwenden die Standarddarstellung.

## Semantische Rollen

Eine Rolle umfasst nur den technischen Token. Backticks liefern Code-Semantik;
die Rolle benennt die fachliche Bedeutung.

| Element | Rolle | AsciiDoc-Beispiel |
| --- | --- | --- |
| Produkt oder Werkzeug | `product` | `[.product]#GRETL#` |
| Standard, Format oder Protokoll | `acronym` | `[.acronym]#SQL#` |
| Registriertes Tasktypsymbol | `task-type` | `[.task-type]#\`SqlExecutor\`#` |
| Name eines konkreten Tasks | `task-name` | `[.task-name]#\`update\`#` |
| DSL-Helper oder vollständiger Aufruf | `dsl-method` | `[.dsl-method]#\`parameter('quelle')\`#` |
| Konfigurationsfeld mit Werttyp | `dsl-signature` | `[.dsl-signature]#*scripts* = list#` |
| Java-Typ | `java-type` | `[.java-type]#\`Path\`#` |
| Parametername | `parameter` | `[.parameter]#\`quelle\`#` |
| Dateiname | `file-name` | `[.file-name]#\`job.groovy\`#` |
| Dateierweiterung | `file-ext` | `[.file-ext]#\`.sql\`#` |
| Pfad oder Verzeichnis | `path` | `[.path]#\`docs/reference/\`#` |
| Dateimuster | `glob` | `[.glob]#\`*.xtf\`#` |
| Kommando | `command` | `[.command]#\`gretl check job.groovy\`#` |
| CLI-Option | `cli-option` | `[.cli-option]#\`--target\`#` |
| Konfigurationsschlüssel | `config-key` | `[.config-key]#\`database\`#` |
| Environmentvariable | `env-var` | `[.env-var]#\`JAVA_HOME\`#` |
| Literal oder Werttypbezeichnung | `literal` | `[.literal]#\`false\`#` |
| Fachlicher Identifikator | `domain-id` | `[.domain-id]#\`DM01AVCH24LV95D\`#` |
| Platzhalter | `placeholder` | `[.placeholder]#\`<runId>\`#` |
| Sonstiges Codefragment | `code-fragment` | `[.code-fragment]#\`failOnError = false\`#` |
| Pflichtstatus | `required` | `[.required]#ja#` |
| Optionaler Status | `optional` | `[.optional]#nein#` |

Ein Tasktyp ist ein registriertes DSL-Symbol, etwa `SqlExecutor`. Ein Taskname
bezeichnet eine konkrete Instanz, etwa `update` in `task('update', SqlExecutor)`.
Einstellungen sind Zuweisungen mit `=`, keine Methodenaufrufe.
`parameter('quelle')` ist dagegen ein Helper-Aufruf. Kapitelüberschriften
bleiben normale Überschriften ohne Monospace-Auszeichnung.

## Schreibregeln

- Die AsciiDoc-Schreibweise `+…+` dient nicht zur technischen Auszeichnung.
  Backticks und Rollen übernehmen diese Aufgabe.
- Kursivschrift ist sprachlicher Hervorhebung vorbehalten.
- Rollen umfassen nur den Token: `[.acronym]#SQL#-Datei`.
- Bei direkt anschliessenden Wortbestandteilen die unconstrained Form verwenden,
  etwa `[.acronym]##ID##s`.
- Satzzeichen bleiben ausserhalb der Rolle, ausser sie gehören zum Literal,
  Pfad, Aufruf oder Codefragment.
- Source-Blöcke tragen eine Sprache und enthalten keine Inline-Rollen.
  Prism übernimmt Highlighting und Copy-Button.
- Erklärungsausschnitte als solche benennen. Vollständige Jobbeispiele enthalten
  erforderliche Parameterdeklarationen und ein explizites Standardziel.
- Geheimnisse gehören in Secretquellen, nie in öffentliche Parameterdefaults.

## Links, Tabellen und Hinweise

Links bleiben blau und werden beim Hover unterstrichen. Sprechende Linktexte
sind sichtbaren rohen URLs vorzuziehen. Generische Wörter wie «Datei», «Task»
oder «Tabelle» bleiben gewöhnlicher Fliesstext.

Die Property-Tabelle verwendet «Feld», «Beschreibung» und «Erforderlich».
Nur der Feldname in der Signatur wird fett gesetzt. Weitere Angaben stehen
in der Beschreibung: Vorgabe, erlaubte Werte und Feldbeispiel. Nur tatsächlich
vorhandene Metadatenvorgaben werden als Vorgabe ausgegeben.

Schriften und Schriftgewichte werden nicht lokal festgelegt. Codeblöcke und
Inline-Code verwenden die Biblios-/Prism-Defaults. Technische Kategorien
erhalten keine eigene Farbe. Hinweise ersetzen keine reguläre Beschreibung.
Lange Feldnamen, Pfade und Beispiele dürfen umbrechen.

## Breite Property-Tabellen

Der GRETL-Renderer setzt die Tabellenrolle `gretl-task-properties`. Nur diese
Tabellen nutzen die volle verfügbare Inhaltsbreite neben der Navigation.
Lauftext, Codeblöcke, Hinweise und gewöhnliche Tabellen behalten maximal `72ch`.
Die Regeln stehen ausschliesslich in `assets/gretl-layout.css`. Das lokale
`templates/layout.ftl` ergänzt zum gebündelten Biblios-Template nur den Link
auf dieses Stylesheet. Biblios und seine Standard-Stylesheets bleiben
unverändert. Lange Tokens dürfen die mobile Seite nicht horizontal verbreitern;
Ein kleiner zusätzlicher Scrollabstand hält Kapitelanker unterhalb der
Standardnavigation sichtbar.

## Generierung und Review

`generated/task-*.adoc` werden ausschliesslich mit
`./scripts/generate-task-docs.sh` erzeugt und nie manuell bearbeitet.
Operation-Metadaten und Sprachkataloge enthalten Klartext beziehungsweise
unterstützten Inline-Code, aber kein AsciiDoc-Rollenmarkup.

Vor Abschluss prüfen:

- Technische Rollen sind passend, Source-Blöcke frei von Inline-Rollen.
- Beispiele und Tabellen entsprechen dem ausgelieferten Taskkatalog.
- Vollständige Beispiele verwenden dieselben Quellen wie die Prüfungen.
- Deutsche und englische Katalogtexte existieren ohne Sprachfallback.
- `./mvnw verify` und erforderliche Dienstprüfungen wurden ausgeführt.
- Der Thoth-Build hat keine ungelösten Includes oder defekten lokalen Links.
- Property-Tabellen sind auf breiten Bildschirmen breiter als der Lauftext.
- Auf Desktop, Tablet und Mobilgerät bleiben Navigation und Inhalt lesbar.
