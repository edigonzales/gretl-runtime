# Reviewrequest script-includes / 1.3 / P1

Unveränderte Phasebase: `e9098ec093cd0ed89065aee57aed461a5daf3c47` (P0 unabhängig akzeptiert).
Assignmentbase: `0bfc8f0fccf43989d751d4f5490202311d200628`; Toolingbasis: `54bb123070fc7bd81aea58b4e1191beecf568cbf`; `MAIN_WITH_ACCEPTED_TOOLING`.

Vollständiger P1-Umfang: S01, S02, S03, S09, S10, S12, S14, S15 auf Komponentenebene nach Plan §6/P1 und unabhängigem Reviewprompt. Lies die neuen Quellen und Tests selbst. Reader darf weder registrieren noch ausführen, Graph muss vollständige Closure pro Scope prüfen, Snapshot und portabler Hash müssen konsistent sein. Gemeinsame Fragmentquelle in mehreren Scopes erlaubt; Projekteinstieg als Fragment verboten. Keine öffentliche Runtime-/JAR-Wirkung vor P2/P3 behauptet.

Implementierung: ApplyDirectiveReader (Parse/Convert + AST/Tokenprüfung), FragmentSource, FragmentSourceAccess mit Filesystemadapter, FragmentGraphResolver mit immutable Bundle. Kein bestehender Loader geändert. Neue DE-/EN-Fehlerschlüssel; bisherige Hashfunktionen unverändert. Bestehender Toolingreviewer unangetastet.

Führe unabhängig aus:

```sh
./mvnw -B -Dmaven.repo.local=/Users/stefan/.cache/gretl-script-includes/m2 -pl gretl-groovy -am -Dtest='ApplyDirectiveReaderTest,FragmentGraphResolverTest' -Dsurefire.failIfNoSpecifiedTests=false test
```

Implementerlauf: 62 Fälle, null Fehler/Skips; frische XMLs und Log extern unter `/Users/stefan/.cache/gretl-script-includes/evidence/P1/`. Entwickle eine neue eigene fachliche Gegenprobe für Syntax/Pfad/Graph/Hash und führe sie tatsächlich aus. Keine getrackten Änderungen; eigene Dateien nur im übergebenen externen Probe-/Reportziel. Beurteile die gesamte Phase gegen unveränderte Phasebase, keine Rootvollgates/alten Toolingkriterien.

Der externe Handoff ergänzt Kandidaten-SHA, Digest, detached Reviewworktree und Reportziel nach Commit. `requirements_checked` muss exakt die acht oben genannten S-Kriterien nennen. Ergebnis und Originalbericht nicht vorfüllen. Bei Defekt konkrete Reproduktion und minimal nötige Nacharbeit benennen.

## Nacharbeitskandidat

Bewerte weiterhin die gesamte P1 gegen dieselbe Phasebase. Erstreview `b1c8991fcbd9a40a705102a4b0164bb33fe2181f` verlangte wegen P1-F1/P1-T1 Nacharbeit; Originalbericht extern `review/P1-attempt-1/evidence/`. Fünf rote Reproduktionsfälle sind jetzt grün, plus Schutz gegen zu breite Unterdrückung durch methodenlokale Variablen. Der neue fokussierte Lauf umfasst 50 Fälle (34 Reader + 16 Graph), null Fehler/Skips. Die vorherigen 18 Settingsparserfälle bleiben ein unveränderter alter Beleg. Wiederhole die betroffenen Reader-/Resolverfälle; keine unbetroffenen Grossgates.

Eigene `apply`-Aufrufe einer Hilfsklasse mit dort deklarierter Methode sind gewöhnliches Groovy. Ebenso bleibt ein Job ohne Includes mit eigener Scriptmethode/-closure `apply` erhalten. Bei wirklichen Includes ist Scriptüberschattung weiterhin APPLY_CONTEXT. Der letzte zusätzliche Erstreview-Probenfall `def helper() { apply('local') }; apply from:'a.groovy'` hat dagegen keine eigene apply-Methode und zielt auf den Scriptreceiver: S02 schliesst GRETL-apply innerhalb von Methoden ausdrücklich aus; seine Ablehnung wird nicht in eine Erlaubnis umgewandelt. Prüfe diese Unterscheidung selbst gegen S02; der Plan bleibt unverändert.
