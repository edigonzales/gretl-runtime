# Bereinigung: Verfahren und Baseline

Verbindlich für diesen Auftrag ist [Version 1.2](spec-v2.md). Die darin noch
enthaltenen Beispielpfade `spec-v1.md` werden beim tatsächlichen Aufruf durch
`--spec docs/maintenance/cleanup/spec-v2.md` ersetzt. Die Spezifikation bleibt
unverändert; es wird keine zweite normative Kopie angelegt.

Die eingefrorene [Baseline](inventory.json) beschreibt den sauberen Commit
`8eec03184883ce9d585d46a768b647f2b7a19169`. Gegenüber der analysierten Produktbasis
`37c6f115980ecabc9a5931afa4bffc9b353f51f2` kamen nur zwei Spezifikationscommits hinzu.
Die ersten Klassifikationen waren Pfadheuristiken und wurden vom unabhängigen
Review auf `e439479917226a4fe25a27cd23f1522d41460cd7` ausdrücklich verworfen.
Sie bleiben als unveränderte ursprüngliche Beobachtung erhalten. Verbindliche
P0-Planungsentscheidungen stehen jetzt im [Inhaltsassessment](content-assessment.json):
je Datei eigene Inhaltsbegründung, zitierte Zeilen oder Binär-/Verbrauchsnachweis,
Baselinehash und vollständige bekannte Nutzer. `--check` prüft die Vollständigkeit,
die eindeutige Zuordnung und Zitate direkt gegen den Ausgangscommit. Die Klassen
sind weiterhin geplante Entscheidungen; keine Löschungen wurden bereits ausgeführt.
Insbesondere die FQCN-Move-Map wird trotz früherer DELETE-Heuristik als
Vertragsorakel erhalten. P2 muss aktive Verbraucher vor jeder Entfernung migrieren.
Insbesondere maschinell benutzte Kataloge und Herkunftsnachweise bleiben bis zur
geprüften Migration ihrer Nutzer erhalten.

## Ausführung

```sh
python3 -m unittest discover -s tests/cleanup_review -v
python3 scripts/verify_cleanup_docs.py --audit
python3 scripts/cleanup_review.py --phase P0 --base <vollstaendige-Basis-SHA> \
  --candidate <vollstaendige-Kandidaten-SHA> --spec docs/maintenance/cleanup/spec-v2.md
```

`--preflight` prüft Python 3.9+, Git, JDK 25, Maven-Wrapper und den erreichbaren
Docker-Daemon sowie Live-Verbindung und Rechte; dies qualifiziert keine Phase. Die CLI sucht OpenCode V2, einschliesslich des mit der Desktop-App
mitgelieferten, mit `--version`/`run --help` geprüften CLI-Einstiegs.
`OPENCODE_BIN` kann einen expliziten absoluten V2-Pfad festlegen. HOME und
V2-Profil bleiben erhalten; Zugangsdaten werden ausschliesslich über OpenCodes
eigene Provider-Anmeldung verwendet. Keine Datenbank wird gelesen oder kopiert.
Python 3.9.6 genügt.

Die temporäre Konfiguration definiert einen eigenen primären Review-Agenten mit
V2-`permissions`: grundsätzlich verboten, Lesen/Suchen erlaubt, ausschliesslich
exakte Git-Lesebefehle erlaubt. Wildcards in Shellfreigaben sind bewusst
vermieden, damit etwa `git diff --output` keinen Schreibweg öffnet. Die
Live-Negativprobe fordert ein Schreibwerkzeug und eine verbotene Shellaktion an.
V2 entfernt gesperrte Schreibwerkzeuge aus dem Werkzeugkatalog; zusätzlich muss
der echte `session.permission.create`-Evaluator `edit` und `shell` verweigern.
Eine Selbstbehauptung des Modells allein reicht nicht.

Der V2.2.5-JSONL-Strom enthält `text`, `tool_use` und `step_finish` mit Abschluss
`reason=stop`, jedoch keine Modellidentität. Deshalb qualifiziert der Runner
zusätzlich `session export --sanitize`: Sitzungs-ID, Agent, Erfolgsabschluss und
sämtliche Assistentenmodelle müssen direktes `deepseek/deepseek-flash` bestätigen.
Die Provider-Metadaten werden über `api provider.get` geprüft; die temporäre
V2-Konfiguration setzt den direkten API-Endpunkt. Die offizielle
[DeepSeek-Modellzuordnung](https://api-docs.deepseek.com/quick_start/pricing/)
wird bei jedem Lauf erneut abgerufen und gehasht. Dies prüft den offiziellen
Alias, nicht kryptografisch die Modellgewichte.

Jeder Versuch erhält ein neues geschütztes externes Verzeichnis. Die temporäre
OpenCode-Konfiguration wird vor den Maven-Gates entfernt, damit die Distribution
keinen schmutzigen Quellstand meldet, und erst danach für den Review angelegt.
Standardberichte werden vor dem zweiten `clean` kopiert. Beide Gatebelege werden
anschliessend unter dem ignorierten `target/cleanup-review-evidence/` lesbar
bereitgestellt. Der Reviewer erhält keinen Zugriff auf externe Rohlogs.

Nur nach technisch validiertem PASS exportiert `--export-evidence <Versuchspfad>`
die unveränderte Reviewantwort und gebundene Metadaten. Der Implementierungsagent
committet ausschliesslich diese beiden Dateien. Der Runner prüft beim nächsten
Phasenaufruf die Whitelist und Bindung des vorherigen Nachweiscommits.

Offizielle technische Grundlagen:
[CLI](https://opencode.ai/v2/docs/cli/commands),
[Provider-Anmeldung](https://opencode.ai/v2/docs/cli/providers),
[V2-Rechte](https://opencode.ai/v2/docs/permissions/),
[V2-Agenten](https://opencode.ai/v2/docs/agents/),
[HTTP-API](https://opencode.ai/v2/docs/api).

## Lokale Netzwerkqualifikation

Der erste Standardlauf mit Temurin 25.0.2 blieb im unveränderten
`FtpOperationTest.downloadsWildcardPattern` hängen (Client wartete auf FTP-Abschluss,
Mock-Server auf Datenverbindung). Der vollständige FTP-Test bestand anschliessend
mit `JAVA_TOOL_OPTIONS=-Djava.net.preferIPv4Stack=true`. Beide vollständigen Gates
werden in dieser Umgebung erneut ausgeführt; die Einstellung gilt auch für die
unabhängigen Gateprozesse und wird in deren Quell-/Umgebungsmanifest erfasst.
Der abgebrochene Versuch ist kein PASS. Es wurden weder FTP-Assertions noch
Produktcode geändert. Externe Diagnose: `/tmp/gretl-P0-A-precommit-be8rldxt/`,
positive Gegenprobe: `/tmp/gretl-ftp-network-diagnostic-duj1a3fi/`.

## V2-Kontextbindung und Modellzuordnung

V2.2.5 kodiert den optionalen API-Parameter `location` als OpenAPI-`deepObject`:
`--param 'location[directory]=<Review-Worktree>'`. Vor `provider.get` wird
`location.get` mit demselben Parameter aufgerufen und der aufgelöste Pfad geprüft.
So wird die Providerprüfung an den Review-Worktree gebunden statt an die
globale Standard-Location. Ein P0-Versuch auf
`13af7f165b730a31684e6ef58b284e5d7a9d41c2` hatte zwar einen echten inhaltlichen
DeepSeek-PASS, wurde wegen des ungebundenen nachgelagerten Metadatenaufrufs
technisch BLOCKED. Er gilt deshalb nicht als Phasenfreigabe.

Die offizielle Zuordnung wird aus derselben Tabellenspalte von `MODEL` und
`MODEL VERSION` gelesen. Ein blosses gemeinsames Vorkommen von API-ID und altem
Versionsnamen in einer Fussnote genügt nicht. Der Berichtsschema-Validator
unterscheidet Integer und Boolean und verlangt alle obligatorischen Felder.
JSON-Inventar und JSON-Evidenz werden weiterhin klassifiziert, aber nicht als
Markdown interpretiert. Die Lesesperre betrifft `.git` und `.git/*`, wodurch
`.gitignore` und `.github` für den Reviewer lesbar bleiben.

Mit `--implementer-evidence <externes-Verzeichnis>` werden die eigenen,
sauberen Kandidatengates von A dem Reviewer separat bereitgestellt. Der Runner
prüft Kandidaten-SHA, Befehle, Log-Digests, Testzahlen und Artefaktaudit, bevor er
diese sekundären Belege kopiert. Sie ersetzen niemals die unabhängigen B-Gates.

V2 hält zusätzlich Agentkataloge je Location im Speicher. Nach Wiederherstellung
der temporären Konfiguration wird deshalb ausschliesslich die eigene neue
Review-Location über die dokumentierte API `debug.location.evict` verworfen und
mit `location.get` neu geladen. `agent.get` muss danach den primären
`cleanup-review`-Agenten, das feste Modell und die unverändert restriktiven
Berechtigungen bestätigen. Andere Locations und der gemeinsame Service werden
nicht neu gestartet. Alle Pfade werden kanonisiert, damit `/var` und
`/private/var` auf macOS nicht verschiedene Cache-Kontexte erzeugen.

## Historische Vorprüfungsdaten

[P0-implementation.json](P0-implementation.json) ist ausschliesslich der
historische erste Vorcommit-Lauf (45 Python-Tests, Quellstand dirty). Sein
`docsAudit.checked = "AUDIT"` bezeichnet versehentlich den Modus, keine Zielzahl.
Diese Rohdaten werden nicht nachträglich auf aktuelle Zahlen umgeschrieben und
sind kein Abschlussbeleg. Für jeden neuen Kandidaten werden eigene und unabhängige
saubere Kandidatengates neu ausgeführt, deren tatsächlich gemessene Zahlen und
SHA-Bindungen im externen Versuch sowie später im unabhängigen Review stehen.

Die V2-Edit-Verweigerung entfernt Schreibwerkzeuge aus dem Agentkatalog. Daher
kann das Modell keinen nicht angebotenen Schreibtoolaufruf absenden; der echte
V2-Permission-Evaluator weist die explizite Edit-Anfrage zurück. Dieser technisch
belegte Unterschied zum wörtlichen Versuch aus §4.4 wird dem Reviewer als
LOW-Resthinweis offengelegt, nicht als ausgeführter Schreibtoolaufruf behauptet.

Der Smoke-Prompt fordert den verbotenen Shellaufruf ausdrücklich als erste Aktion
an. Zwei Versuche auf `a43085d3d934c2369234cd848c380b9b70b4003c` gaben lediglich
das Antworttoken zurück und wurden korrekt gesperrt. Die isolierte Liveprobe
`/private/tmp/gretl-v2-shell-instruction-dkj568il` belegte mit präziserem Prompt
den tatsächlichen `printf DENY_TEST`-Toolaufruf und seine technische Ablehnung.
Der Validator verlangt nun genau diesen Befehl; ein anderer verweigerter
Shellaufruf oder das Antworttoken allein qualifiziert weiterhin nicht.
