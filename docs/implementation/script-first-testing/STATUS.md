# SCRIPT-first-Testing – Arbeitsstand

- Auftragsbasis / TST1-Basis: `520b59be12f7966be46d0eaaca046c6ee3dc04f7`.
- Arbeitsbranch: `codex/script-first-testing`; Ausgangscheckout sauber.
- Aktuelle Phase: TST1, IN_PROGRESS. TST2–TST4 noch nicht begonnen.
- JDK: Temurin 25.0.2; Docker Desktop erreichbar, Host darwin/arm64,
  Daemon linux/arm64.
- OpenCode: `/Applications/OpenCode.app/Contents/Resources/opencode-cli`,
  2.2.5; `run --help` und `--standalone` geprüft. PATH zeigt dagegen auf V1.
- Direkter Modellschlüssel: `deepseek/deepseek-flash`; gespeicherte Anmeldung
  vorhanden. Offizielle Aliaszuordnung zu DeepSeek-V4.1-Flash-1017 am
  01.11.2026 geprüft: <https://api-docs.deepseek.com/quick_start/pricing/>.
- Private Bereitschaftsprüfung beendet, Exit 0; Repository lesen, Shell und
  Schreiben ausserhalb des Checkouts erfolgreich. Kein Phasenreview.
- Rohbelege: `/Users/stefan/sources/gretl-script-first-evidence/`.
- Baselines gesichert: Standard 674 und Vollprofil 730 Tests, jeweils
  Exit 0 und keine Failures/Errors/Skips; voller Basisaudit Exit 0.
- SCRIPT-Implementierung und Booleanmigration vorhanden. Entwicklungsgates:
  Standard mit unerreichbarem Docker-Socket 686 Tests, JAR/local 686 Tests,
  SCRIPT/full 742 Tests; jeweils Maven und passender Audit Exit 0,
  keine Failures/Errors/Skips. SCRIPT/full deckt alle 44 geforderten
  Standardoperationen ab. Berichte vor jedem `clean` gesichert.
- Erster fester Kandidat `3e60341597904094383f5394e544ecb125c184fd`:
  alle eigenen Gates frisch grün und vollständig beendet. Eigene Durchsicht
  fand noch zwei nach der JAR-Vorbereitung ausdrücklich zu erhaltende
  No-Toplevel-/No-Report-Checks. Testvertrag wiederhergestellt; kein Review-PASS.
- Nächste Aktion: neuer Kandidatencommit, alle frischen Kandidatengates erneut
  und anschliessend unabhängiger OpenCode-TST1-Review im detached Worktree.
- Kein Phasen-PASS, keine Veröffentlichung.
