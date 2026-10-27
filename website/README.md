# GRETL next – Quarto-Webseite

Dies ist ein vollständig nachbearbeitbares Quarto-Webseitenprojekt für `next.gretl.app`.

## Lokal starten

Quarto installieren, im Terminal in diesen Ordner wechseln und ausführen:

```bash
quarto preview
```

Die statische Webseite wird erzeugt mit:

```bash
quarto render
```

Das Ergebnis liegt anschliessend in `_site/`.

## Wo wird was bearbeitet?

- `index.qmd` – Titelseite
- `features.qmd` – Tab **Features**
- `getting-started.qmd` – Tab **Getting Started**
- `docs.qmd` – Tab **Docs**
- `styles.scss` – Farben, Abstände, Schrift und responsives Layout
- `_quarto.yml` – Navigation und globale Quarto-Konfiguration
- `assets/gretl-mark.svg` – einfaches, editierbares Vektorlogo

Die Webseite bindet **Recursive** lokal als woff2 ein. Überschriften und andere fette Texte verwenden Gewicht **800 (ExtraBold)**. Für Code-Blöcke wird die Monospace-Achse (`MONO`) des Recursive-Variable-Fonts verwendet.

## Schnelle visuelle Anpassungen

Die wichtigsten Farben stehen in `styles.scss` oben unter `:root`. Der Farbverlauf der grossen Überschrift ist in `.gradient-text` definiert. Der CSS-Abschnitt für die Titelseite beginnt beim Kommentar `Landing page`.

Der Code auf der Titelseite ist ein normaler Quarto-Codeblock. Er bleibt auswählbar und kopierbar; es wird kein Bildgenerator benötigt.
