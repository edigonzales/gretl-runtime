# P4: unabhängiger Reviewrequest

Auftrag task-finalizers, Spec 1.0. Auftragsbasis
`9faf347f739e836976dd566bd31a65fc4ff91ac4`; feste Phasebase
`b507e03f204c8201119e721518f84a38ea3e7780`. Kandidat im externen Handoff.
P3 ist exakt auf dieser Phasebase unabhängig und maschinell abgenommen.

Prüfe F19, F23–F28 und F30 vollständig auf der Toolingebene. Lies normative
Dateien, Phasendiff und Arbeitsstand selbst. Führe das gezielte Java-/stdio-Gate
und die angegebenen TypeScript-/Clienttests selbst aus; prüfe frische positive
XMLzahlen ohne Skips. Das Gate baut die echte Server-JAR; neue stdiofälle
verwenden sie tatsächlich. Runtime-/JARbelege aus P3 sind historische,
SHA-gebundene Referenz; der unveränderte gespeicherte GUIjob läuft erst in P5.

Prüfe Owner → Finalizer, nominale bedingte Vorschau, Ergebnisrechte, echte
Fragmentanker, statische vollständige Includes, dynamische partielle Fälle,
Rename/Delete sowie sichere Add/Remove-/Setter-/Batchmutationen. Sharedsource,
Ownergeneration, Kontext, Sourcevector und asynchrone Hostnachprüfungen erhalten.
Graphschema 2 ist gezielt von Basisprotokoll 1 und Katalogschema 1 getrennt;
alte strikte Clients dürfen die neue Kante nicht interpretieren, aktuelle VSIX
prüft explizit die neue Serverfähigkeit. Kein AFTER-Fallback.

Entwickle selbst eine neue Core-/Wiregegenprobe und eine eigene kleine reale
Benutzerpfadgegenprobe im installierten isolierten VSIX-Fenster. Native Hypothese,
Eingaben, Schritte und Assertions bestimmst du selbst; mechanischer CUA-Gateway
nach native-instructions.md. Bereits vorhandene umfangreichere Nativebelege
unabhängig auf Artefakt-, Buffer- und Quellenidentität prüfen. Kein GUI-Mock,
keine produktiven Jobs, keine Produktfixes. Originalbericht und Verdict nach §9.4.

Zusätzlicher breiter Entwicklungslauf und Kontrolllauf am unveränderten P3-SHA
belegen dieselben zwei CatalogSemanticParityTest-Fixturefehler. Sie sind kein
P4-Gate-PASS und wurden nicht umetikettiert oder durch schwächere Tests ersetzt.
Prüfe die Abgrenzung selbst; der Katalog und dessen bestehende Tests sind
unverändert. Alle notwendigen gezielten P4-Tests müssen trotzdem erfolgreich sein.
