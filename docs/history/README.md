# Historische Quellen und unveränderte Orakel

**Historisch, keine Arbeitsaufträge und keine aktuelle Releasefreigabe.**
Dieser kleine Index ersetzt die abgeschlossenen Prompt-/Iterationsserien.
Die Originaltexte sind im Git-Quellstand `37c6f115980ecabc9a5931afa4bffc9b353f51f2`
auffindbar. Ein Dokument-Snapshot und ein darin getesteter Kandidat sind
unterschiedliche Identitäten; alte Testergebnisse gelten nur für ihren Kandidaten.

| Arbeitskomplex | Historischer Kandidat / Aussage | Originalquelle |
| --- | --- | --- |
| Task-/Modul-/Pakethärtung | `27adb5baa3006627e66ce819e345ca70cda477bb`, kumulativer Abschluss vom 25.10.2026 | [Gesamtreview](https://github.com/edigonzales/gretl-runtime/blob/37c6f115980ecabc9a5931afa4bffc9b353f51f2/docs/implementation/improvement-plan/phase-3-review.md) |
| F1–F4: Rechte, Budgets, Bibliothekslogscopes | `9d6e26e1d9bfa5916a8d5c5a321d134ca5237447`, Gesamtqualifikation vom 26.10.2026 | [Qualifikation](https://github.com/edigonzales/gretl-runtime/blob/37c6f115980ecabc9a5931afa4bffc9b353f51f2/docs/implementation/hardening-f1-f4/qualification.md) |
| Subprojekte und Projekt-JAR | `f0e65db8add4328957e56939c11e2809d4247afd`, kumulativer Abschluss vom 29.10.2026 | [Gesamtreview](https://github.com/edigonzales/gretl-runtime/blob/37c6f115980ecabc9a5931afa4bffc9b353f51f2/docs/implementation/project-subprojects/phase-3-review.md) |
| Frühe M0–M7-Lieferstufen | M7 war eine Vorbereitung mit offenen Release-/Pilotbedingungen, keine heutige Freigabe | [M7-Quellbericht](https://github.com/edigonzales/gretl-runtime/blob/37c6f115980ecabc9a5931afa4bffc9b353f51f2/docs/implementation/m7-report.md) |

Der [Linux-CI-Beleg](qualification/linux-ci.json) bindet den damaligen Lauf an
`27adb5baa3006627e66ce819e345ca70cda477bb`. Er qualifiziert keinen späteren Commit.
Heutige Testzahlen kommen aus aktuellen XML-/Artefaktaudits.

## Vertrags- und Herkunftsdaten

- [Operationsbaseline](contracts/operation-baseline.json) und [Java-Typumzüge](contracts/java-type-moves.json)
  bleiben bytegenaue historische Testorakel für `OperationMetadataTest` und den Artefaktaudit.
  Alte FQCNs sind absichtlich erhalten und werden über die Move-Map verglichen.
- [Originalszenarien](provenance/original-scenarios.json) bewahren Originalassertionen,
  Quellcommit und Fixturehashes; [Portzuordnung](provenance/scenario-mapping.json)
  ergänzt die konkreten fachlichen Äquivalente und begründeten Anpassungen.
  Dies sind historische Herkunftsdaten, keine heutige Ausführungsstatistik.
- [Fixturedateien](provenance/fixture-files.json), [ergänzte Paketfixtures](provenance/package-fixtures.json)
  und [lokale Modelle](provenance/local-models.json) bewahren Herkunft, URLs und
  Prüfsummen ihrer damaligen Stände. Neue fachliche Umbenennungen werden separat nachgewiesen.
- [gretl-next-MIT](licenses/gretl-next-MIT.txt) und [sogis/gretl-MIT](licenses/sogis-gretl-MIT.txt)
  enthalten die unveränderten erforderlichen Copyright-/Lizenzhinweise. Zusätzliche
  Hinweise in Fixtures und JARs bleiben bestehen.

## Erhaltene Entscheidungen und Originalanalysen

Die [aktuellen Runtimeverträge](../architecture/runtime-contracts.md) und der
[ausdrücklich nicht implementierte Backlog](../backlog/publisher-and-native.md)
bündeln weiter relevante Inhalte. Die vollständigen historischen Quellen bleiben:

- [02-publisher-bestandsanalyse](https://github.com/edigonzales/gretl-runtime/blob/37c6f115980ecabc9a5931afa4bffc9b353f51f2/docs/spec/02-publisher-bestandsanalyse.md)
- [05-publisher-zielvertrag](https://github.com/edigonzales/gretl-runtime/blob/37c6f115980ecabc9a5931afa4bffc9b353f51f2/docs/spec/05-publisher-zielvertrag.md)
- [08-native-image-strategie](https://github.com/edigonzales/gretl-runtime/blob/37c6f115980ecabc9a5931afa4bffc9b353f51f2/docs/spec/08-native-image-strategie.md)
- [09-tests-und-abnahme](https://github.com/edigonzales/gretl-runtime/blob/37c6f115980ecabc9a5931afa4bffc9b353f51f2/docs/spec/09-tests-und-abnahme.md)
- [11-quellen-entscheidungen-und-roadmap](https://github.com/edigonzales/gretl-runtime/blob/37c6f115980ecabc9a5931afa4bffc9b353f51f2/docs/spec/11-quellen-entscheidungen-und-roadmap.md)

Insbesondere die Publisher-TC-P01–TC-P09 sind dort vollständig überliefert; ihre
Verschiebung ist kein bestandener Nachweis. Die alte Native-Strategie trennt
statische Hilfe, erste Task und Gesamtdauer sowie festen Jobcode von variablen
Laufzeitwerten. Künftige Versionen und Plattformen benötigen neue Qualifikation.
