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

## Zweiter Nacharbeitskandidat

Auch `667306c66d949de48b1dcbf219a722099a022605` wurde abgelehnt (Originale extern `review/P1-attempt-2/evidence/`). Die pauschale Unterdrückung aller Probleme bei einer eigenen Scriptmethode ist entfernt. Nur gewöhnliche Nicht-Map-Aufrufe einer bekannten eigenen Scriptmethode werden als eigene Groovy-Aufrufe erkannt. Eindeutige Map-first-Aufrufe behalten APPLY_SYNTAX/APPLY_CONTEXT, auch mit eigenem apply-Namen. Vier neue Red/Green-Fälle ergänzen die fünf ursprünglichen Kompatibilitätsfälle und den Kontextschutz; die Gesamtauswahl enthält jetzt 54 Fälle. Prüfe die ganze unveränderte P1 und entwickle eine weitere neue Gegenprobe. Wiederkehrende Findings sollen fachlich behoben werden, nicht durch unveränderte Neuprompts verschwinden.

## Dritter Nacharbeitskandidat

Erstreview und beide Nachreviews bleiben SHA-gebunden verworfen. Der Review von `d6f6aef18e06a51bafcd1005ae9868138e85c105` fand geerbte Helper-Methoden, Closure-Felder und die fehlende Map-Kompatibilitätsprüfung einer String-Überladung. Drei neue Red/Green-Fälle reproduzieren dies; zusätzlich vier direkte Regressionen für geerbtes Map-apply, Defaultargument, Varargs und Importalias. Die Korrektur bleibt im lokalen Parse/Convert-AST; kein Auswerten oder Nachladen von Helperklassen. Aktuell 61 fokussierte Fälle (45 Reader, 16 Graph) grün. Bericht und Originalproben extern `review/P1-attempt-3/evidence/`, neuer Nachweis `evidence/P1-fix3/`. Prüfe nach wie vor die ganze P1 gegen `e9098ec093cd0ed89065aee57aed461a5daf3c47`, mit eigener neuer ausgeführter Gegenprobe und exaktem Kandidaten.

## Vierter Nacharbeitskandidat

Der Review von `08f54cb0c0810f5274f8510d6b420cb351957333` verlangt Signaturprüfung für Closure-Felder und die Beschränkung der globalen Scriptüberschattung auf Scriptdeklarationen. Die vier Reproduktionen sind jetzt Tests; zwei positive Closure-Fälle sichern Map-Kompatibilität und implizites `it`. Methoden und Closure-Literale nutzen dieselbe kleine Parameterprüfung, ohne Klassen zu laden oder Code auszuführen. Blocklokale Variablen werden nicht als globaler Scriptname registriert. Aktuell 67 fokussierte Fälle (51 Reader + 16 Graph) grün. Originale extern `review/P1-attempt-4/evidence/`, Korrekturbelege `evidence/P1-fix4/`. Die gesamte P1 gegen unveränderte Phasebase bleibt Prüfgegenstand; neue eigene Gegenprobe erforderlich.

## Fünfter Nacharbeitskandidat

Auch `efe3bbcba2651969015648d26c924c4d7a10fa64` wurde verworfen: lokales Defaultinterface und cast-verpacktes Closure-Literal. Beide Fehler sind durch rote Proben reproduziert und korrigiert. Die lokale AST-Hierarchie umfasst jetzt Interfaces; transparente Casts werden vor der gemeinsamen Signaturprüfung ausgepackt. Zusätzliche Regressionen sichern Closure-Aliase und opake externe Helper-Typen ohne deren Nachladen. Aktuell 73 fokussierte Fälle (57 Reader + 16 Graph) grün. Originale `review/P1-attempt-5/evidence/`, Korrekturbelege `evidence/P1-fix5/`. Vollständige Phase weiter gegen dieselbe Phasebase prüfen, eigene neue Gegenprobe entwickeln; keine Abschwächung der S02-Kontextablehnungen.

## Sechster Nacharbeitskandidat

Der Review von `b2c7c00a08b55559d5811bb7792f5cc5c237057e` verlangt Erasure/obere Schranken bei generischen Helper-Parametern. Sechs Reproduktionen sind jetzt Tests: generische Methode/Klasse/Varargs/Map-Schranke/Closure und der Helper ohne Includes. Die gemeinsame Parameterprüfung löst lokale Typvariablen anhand der AST-Deklarationen auf; String-Schranken und eine lokale Klasse namens Map bleiben Negativkontrollen. Aktuell 81 fokussierte Fälle (65 Reader + 16 Graph) grün. Originale `review/P1-attempt-6/evidence/`, neue Belege `evidence/P1-fix6/`. Gesamte P1 gegen unveränderte Phasebase prüfen, mit neuer eigener ausgeführter Gegenprobe. Externe/unknown Helper-Typen weiterhin opak, keine neue Klassenauflösung.
