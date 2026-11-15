# Repository-Anweisungen

Prüfe Gitstatus, Ausgangscommit, betroffenen Code und Tests. Bewahre fremde
Änderungen und arbeite auf einem eigenen Branch. Push, Merge, Release und
produktive Jobs benötigen einen ausdrücklichen Auftrag.

Gradle Groovy DSL baut mit JDK 25. GRETL-Jobs laufen mit Groovy 6.
Nutze `./gradlew test`, `installDist`, `jarTest` und `serviceTest`.
Die vollständige Qualifikation einschliesslich IMAGE läuft mit
`python3.12 scripts/verify-runtime.py`. Prüfe Links mit `./gradlew docsCheck`
und betroffene Beispiele und Renderpfade nach der [Buildanleitung](docs/development/build.md).

Die [Modulübersicht](docs/architecture/module-boundaries.md) beschreibt die
Grenzen. Java-SPI und Runner bleiben unabhängig von Taskbibliotheken. Der
Packager isoliert den Compiler von Host-Abhängigkeiten. Der LSP analysiert
Quellen ohne Ausführung. Bewahre Pfadgrenzen, read-only Quellmounts und die
isolierte Bereinigung eigener Docker-Ressourcen.

Suche zuerst nach vorhandenem Code. Begründe jede neue Klasse, Schnittstelle
und Modulgrenze mit einem konkreten Verbraucher oder einer echten Grenze.
Bevorzuge direkte Implementierungen und Standardfunktionen von Gradle, Git,
JUnit und bestehenden Bibliotheken. Vermeide triviale Wrapper, hypothetische
Erweiterungspunkte, parallele Buildpfade und obsolete Implementierungen.

Füge Hashes, Fingerprints oder Belege nur hinzu, wenn sie einen konkreten
Fehler verhindern, den vorhandene Mechanismen offenlassen. Fachliche Tests
prüfen das Produkt direkt. Prüfe grössere Entwürfe mit
[simplify-change](.agents/skills/simplify-change/SKILL.md) und beauftrage vor
PR-Abschluss einen unabhängigen [Review](.agents/skills/review-change/SKILL.md).
