# Bereinigung: Baseline, Prüfungen und Nachweise

Verbindlicher Auftrag ist die unveränderte [Spezifikation 1.2](spec-v2.md).
Die in ihren historischen Templates genannten älteren Dateinamen werden beim
realen Aufruf durch `--spec docs/maintenance/cleanup/spec-v2.md` ersetzt.

## Inventare und Entscheidungen

Das [Inventar](inventory.json) bindet den Ausgangscommit
`8eec03184883ce9d585d46a768b647f2b7a19169`. Seine 191 Ausgangsdokumente,
Quellhashes, Operationen, Schemas und übrigen Baseline-Kernfelder bleiben gegen
den ursprünglichen Inventarcommit unverändert geprüft. `introducedDocumentation`
ist ein ausdrücklich ergänzbarer Index neuer Dokumentation; die ursprünglichen
Einträge werden nicht umklassifiziert. Jede aktuelle getrackte oder gestagte
Dokumentationsdatei braucht eine Klasse. Automatische Nachweispaare haben eine
hashgeprüfte eigene Archivklassifikation im reinen Nachweiscommit.

Die ersten Pfadheuristiken waren unzureichend. Das quellengebundene
[Inhaltsassessment](content-assessment.json) enthält die verbindlichen
Dateientscheidungen, Zitatzeilen, Baselinehashes und bekannten Nutzer.
Die [P2-Aktionen](cleanup-actions.json) dokumentieren tatsächliche Auswahl,
Ersatz, Verbraucher, Bytes und Git-Erhaltung. [Historische Quellen](../../history/README.md)
enthalten wenige Abschluss-SHAs, unveränderte Vertragsorakel, Provenienz und Lizenzen.
Frühere Fehlversuche und der entfernte dirty Vorcommit-Bericht bleiben in Git
und den externen Versuchsdaten erhalten; sie sind keine heutigen Freigaben.

## Eigenständige Gates und Review

```sh
python3 -m unittest discover -s tests/cleanup_review -v
python3 scripts/cleanup_inventory.py --check
python3 scripts/verify_cleanup_docs.py
python3 scripts/cleanup_review.py --phase P2 --base <vollstaendige-Basis-SHA> \
  --candidate <vollstaendige-Kandidaten-SHA> --spec docs/maintenance/cleanup/spec-v2.md
```

A und B führen in getrennten sauberen Kandidaten-Worktrees beide vollständigen
Maven-Gates, Artefaktaudits und phasenbezogenen Zusatzchecks aus. Standardbelege
werden vor dem nächsten `clean` extern gesichert. `--implementer-evidence`
validiert A-Belege und stellt sie getrennt von Bs neu erzeugten Belegen bereit.
B erhält redigierte, gehashte, nur lesbare Kopien unter
`target/cleanup-review-evidence/`; rohe Versuchsdaten bleiben geschützt extern.

Python 3.9.6 und Standardbibliothek genügen. `--preflight` prüft Python 3.9+,
Git, JDK 25, Maven-Wrapper, Docker-Daemon und echte Direktprovider-Verbindung.
Die mit OpenCode Desktop gelieferte V2-CLI wird unabhängig von einer alten V1
im PATH ermittelt; `OPENCODE_BIN` erlaubt einen geprüften expliziten V2-Pfad.
Gespeicherte Anmeldung und HOME bleiben erhalten. Kein ENV-Key wird verlangt,
kein GUI-Profil oder Credentialstore gelesen, kopiert oder umgeschrieben.

Fest ist `deepseek/deepseek-flash` über den direkten DeepSeek-Provider,
API-ID `deepseek-flash`. Die offizielle
[Modellzuordnung](https://api-docs.deepseek.com/quick_start/pricing/) wird erneut
abgerufen und aus derselben MODEL-/MODEL-VERSION-Tabellenspalte geprüft.
Sitzungsexport und locationgebundene Providerdaten belegen das tatsächliche
Modell und den direkten Endpunkt; dies ist keine kryptografische Gewichtsattestierung.
Kein stiller Modellwechsel, `--continue` oder `--auto`.

## Rechte und finaler Bericht

Die V2-Policy verwendet `permissions`, `shell`, `subagent`: deny-all,
Lesen/Suchen erlaubt, nur exakte Git-Lesebefehle freigegeben. Credentials,
Schreiben, fremde Verzeichnisse und Subagenten bleiben gesperrt. Temporäre
Konfigurationen liegen nur in der eigenen Review-Location. V2-Pfade werden
kanonisiert; nach Konfigurationswiederherstellung wird ausschliesslich diese
Location über `debug.location.evict` neu geladen und ihr Agentkatalog geprüft.
Andere Projekte und der gemeinsame Service werden nicht neu gestartet.

Die echte Negativprobe verlangt `printf DENY_TEST` und ein eindeutiges
Antworttoken. Token ohne beobachteten verweigerten Aufruf qualifiziert nicht.
Schreibwerkzeuge werden durch V2 aus dem Katalog entfernt. Der echte
Permission-Evaluator muss zusätzlich `edit` und `shell` verweigern. Die Abweichung
vom wörtlichen Schreibtoolversuch wurde in P0 als begründeter LOW-Hinweis
unabhängig akzeptiert; die tatsächliche Schreibsperre und Worktree-Integrität
sind technisch belegt. Es wird kein ausgeführter Schreibtoolaufruf behauptet.

Der Parser bindet genau einen finalen Bericht an die gestoppte V2-`messageID`.
Neutrale vorherige Fortschrittstexte brauchen eine zuvor abgeschlossene
Tool-Nachricht; Zwischenurteile, ungebundene Texte, doppelte Marker/Abschlüsse
und Vor-/Nachtext in der finalen Antwort sperren. Ohne verlässliche ID-Grenzen
gilt die strengere Gesamtstromprüfung. SHA, Kriterienmatrix, alle geänderten
Pfade, Ziele, Regressionen und Findings werden zusätzlich validiert.

Nur ein fachliches und technisch validiertes PASS exportiert automatisch den
unveränderten Modelltext und passende Metadaten. Der folgende Commit darf nur
dieses Nachweispaar enthalten. FAIL verlangt Korrektur und neue Sitzung;
BLOCKED erlaubt keine Folgephase. Alte Belege werden nie überschrieben oder
nachträglich freigegeben. Aktuelle unabhängige Nachweise stehen unter [reviews/](reviews/).

## Dokumentationsausführung

Ab P1 führt `cleanup_doc_checks.py` die markierten README-/Website-Befehle aus,
prüft Dateibytes und Reports, Startseiten-Action und Subprojekte, erzeugt alle
44 Tabellen unverändert neu und rendert Quarto und Thoth. Thoths JGit braucht
eine eigene lokale Git-Kopie mit einem privaten Branch auf der exakten
Kandidaten-SHA. Nur diese Kopie wird angelegt und entfernt; Repositoryrefs und
vorhandene Biblios-Konfigurationen werden nicht geändert. Gitbefehle, SHA,
Rendererlogs und HTML bleiben extern gesichert und B zugänglich.

Der Linkchecker meldet alle Ziele; historische Quellen werden separat ausgewiesen.
Nur konkrete Zitate aus der bytegleich gehashten Auftragsspezifikation haben
exakte Pfad-/Zeilen-Ausnahmen mit Begründung. Neue oder geänderte aktive Ziele
werden dadurch nicht ausgenommen. Ein entfernter vorhandener Zielpfad muss den
Negativtest scheitern lassen.

Die lokale FTP-Netzwerkqualifikation erforderte
`JAVA_TOOL_OPTIONS=-Djava.net.preferIPv4Stack=true`. Beide vollständigen Gates
verwenden und protokollieren diese Einstellung in dieser Umgebung; Produktcode
und Assertions wurden dafür nicht geändert. Ein erster hängender Lauf wurde
abgebrochen und gilt nicht als PASS.
