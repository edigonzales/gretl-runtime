# P2: transitive Captures, Ownership, Context und Membership

Auftrag `include-tooling`, Fassung 1.0. Auftragsbasis
`9773c42ddb12a7343cf5f9ad7b6be81b489abb30`; feste Phasebase ist der unabhängig
abgenommene P1-Kandidat `fb4664de265d06e3145d2757c1d1aed853d25da1`.
Kandidat erst nach Commit extern. Prüfe IT03–IT10 und IT15/IT21/IT23 im
P2-Grundlagenumfang, sowie IT24-Workflow. P3-Semantik/Queries/Vollgraph und
P4-Footprint/positive strukturelle Edits/GUI sind noch nicht geliefert.

Lies aktuelle Implementierung und WORK_QUEUE. Folge einem realen Sourcefluss
vom Client über neuen Membershiphandshake, DidOpen, SourceView/Closure/Ownerindex
und Contextregistry bis zur Invalidierung. Alle Besitzer stammen aus dem bounded
Workspaceinventar oder ausdrücklicher Registrierung, nicht nur offenen Panels.
Sources werden einmal pro Capture gelesen/geparst; Anwendungen bleiben separat.
Settings-Projekte, Rootgrenzen, rohe Symlink-vor-..-Prüfung und negative Reads
gezielt prüfen. SQLaufnahme stammt aus allen Anwendungen und deren Ownerbasis.

Die ganze kleine Core-/LSP-Familie ist wegen der Capture-/Workspaceänderung
betroffen. Keine weiteren Fachmodule, Dienste, Rootvollgates oder JARwiederholung.
Das genaue Kommando am Implementerkandidaten:

```sh
./mvnw -B -Dmaven.repo.local=/Users/stefan/.cache/gretl-include-tooling/m2 -pl gretl-lsp -am -Dtest=AnalysisSchedulerTest,ApplyEditCompatibilityTest,ApplyPartialCoverageTest,DeclarationSourceEditsTest,DefaultsSourceEditsTest,DependencySourceEditsTest,DiagnosticPublicationStateTest,DocumentStoreTest,EditVerifierTest,EditabilityAnalyzerTest,FieldSourceEditsTest,FileSourceRepositoryTest,GraphFormProjectorTest,GretlDocumentParserTest,HeaderSourceEditsTest,HostBoundaryTest,HostSchedulingTest,IncludeOwnershipTest,IncludeResolutionTest,IncludeSyntaxTest,JobGraphProjectorTest,LanguageFeatureQueriesTest,ParseBoundsAndCoordinatesTest,ParserAdversarialTest,ProjectAnalyzerTest,RecoveryAndLiteralContractTest,ReferenceSourceEditsTest,SemanticEditPlannerTest,SnapshotNavigationTest,SourceContextServiceTest,SourceEditBuilderTest,SourceValueWriterTest,SourceViewTest,SupplementalSourcesTest,TargetPreviewProjectorTest,TokenNavigationTest,WorkspaceManagerTest -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=ApplyIncludeServerIT,IncludeMembershipServerIT,ServerProcessIT -Dfailsafe.failIfNoSpecifiedTests=false clean verify
```

Das Reviewer-Mavenrepo kommt separat im Handoff. Frische erwartete Ziel-XMLs mit
Tests>0 und null Fehlern/Skips verlangen. Save XMLs vor weiterem clean.
Zusätzlich die exakten extra_test_commands im Handoff selbst ausführen:
`npm ci --prefix vscode/gretl-vscode --no-audit --no-fund`,
`npm --prefix vscode/gretl-vscode run check`,
`npm --prefix vscode/gretl-vscode run test:includes`.
Das sind gezielte Clienttests und kein nativer GUIersatz.

Eigene neue Gegenprobe wirklich ausführen: Choose a relevant hypothesis yourself,
keine Implementerprobe als unabhängig übernehmen. Shared-Kontextwahl darf keine
andere gepinnte Anfrage umschalten. Alte Tokens/Incarnations/Ownergenerationen
müssen scheitern. Ein allein geöffnetes Fragment darf kein erratenes Entry werden.
Ein vor Mitgliedschaft dirty geöffnetes Groovydokument darf vor Bestätigung keine
schreibbar aktuelle Diskanalyse liefern. Ein geschlossenes geändertes Fragment
muss neu erfasst werden. Unvollständiger Ownerindex bleibt UNKNOWN.

Testmigration genau lesen: ApplyPartialCoverage nun mit echtem statischem Ziel,
zusätzlicher Missing-Gegentest; kaputte Serverquelle jetzt INVALID mit tatsächlicher
Fragmentdiagnose, nie ausgeführt; frühere capture-assertFalse-Übergangsgrenze gezielt
ersetzt. Semantic-/Editfixtures erlauben nun die explizite Inventaraufnahme während
Capture und verbieten nach wie vor jegliche IO während Analyse/Plan/Postimages.
Keine Erwartungslockerung, kein alter Runtimeauftrag und keine P3/P4-Abnahme.

Originalbericht und JSON nach Plan §9.4, vollständige P2-Kriterienmenge, selbst
belegte Commands, eigene neue Probe, Findings/required_not_executed. Produktbytes
nicht ändern. Neue Session am exakten SHA, Ende und Modellidentität extern prüfen.

## Erneuter Kandidat nach P2-F1

Der erste Review von `36caba40ddd49fa6aa7ed34f646e95945dea181f` urteilte
NACHARBEIT wegen verlorener Settings-Projektkontexte. Lies dessen Originalbericht
und Gegenprobe im referenzierten Evidenzverzeichnis selbst. Korrektur: Settings
werden im Owner-/Kontextmodell ausdrücklich als ENTRY des tatsächlichen Projekts
geführt; keine Syntax-only-Umdeutung und kein Fragment-Pseudoprojekt.

Neue feste Regression verlangt den vollständigen Settingsgraphen mit/ohne neue
Capability sowie SourceContexts-Anker und stabile Ownershipgeneration. Zusätzlich
eigene eng verwandte Korrekturen: explizite New-Job/Use-File-Entryrollen nach
Start/Restart; wirkliche Relevanzfilter für Groovyevents; Abweisung älterer
Membershipgenerationen und zusammengeführte Startupupdates; korrekte Rootpolicy
beim Inventarhost. Jede dieser Änderungen ist Teil dieses neuen Kandidaten.

Gleiche Phasebase, gesamte P2-Phase beurteilen. Die im Handoff genannte neue
Testauswahl ersetzt für die Korrektur den oben dokumentierten initialen Vollsatz.
Gezielt neu laufen Workspace-/Owner-/Context-/File-/SourceView-, Semantik-/Defaults-
und Postimagekontrollen, LSP-Hostfamilie, echte Membership-/Apply-/ServerProcessIT
und die 25 Hosttests/TypeScriptcheck. Nicht betroffene P2-Erstbelege dürfen nach
Delta-/Identitätsprüfung ausdrücklich wiederverwendet werden. Das ist kein neuer
Parser-/Query-/JARlauf am neuen SHA. Die ursprüngliche P2-NACHARBEIT bleibt erhalten.

Kriterienstatus bezieht sich auf den in Plan §7.4 bezeichneten P2-Teilvertrag;
P3-/P4-Endfunktionen bleiben ausstehend und werden nicht als erfüllt behauptet.
Erfinde und führe eine neue eigene Gegenprobe für diesen Korrekturkandidaten aus.
