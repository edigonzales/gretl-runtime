# Auswahl von Validator-Funktionen

Die allgemeine Formatbibliothek enthält keine feste Organisationsauswahl und
keine obligatorischen Solothurn-/GeoWerkstatt-/NGK-Funktionsbündel. Ihre eigenen
Maven-Laufzeitabhängigkeiten genügen für CSV, JSON, GPKG und SHP ohne Zusatzfunktion.
`ValidatorClasspathLocalJobIT` baut diesen Classpath aus dem Taskartefakt und
seinem eigenen aufgelösten Produktionsgraphen und kompiliert einen separaten
Java-Aufrufer dagegen. Diese Komponententests sind keine Job-JAR-Nachweise.

Die Standarddistribution liefert die bisherigen vier Bündel mit und ergänzt
`META-INF/services/ch.interlis.iox_j.plugins.IoxPlugin` aus dem klassifizierten
Artefakt `standard-functions`. Die 18 Klassen stehen dort als normale Java
Services. Skriptautoren müssen keine JARs verwalten. Andere Auslieferungen
können einen eigenen solchen Service-Deskriptor liefern oder die vorhandene
IOX-JVM-Property `ch.ehi.iox-ili.pluginClasses` setzen: kommaseparierte
Klassennamen; eine ausdrücklich leere Property wählt keine Zusatzfunktion aus.
Die Auswahl erfolgt vor dem JVM-Start, nicht durch einen Jobparameter.

| Vertrag | CSV / JSON / GPKG / SHP | Schmaler IliValidator |
| --- | --- | --- |
| Default | Java ServiceLoader für IOX IoxPlugin | derselbe Service-Vertrag |
| Ausdrückliche JVM-Auswahl | ersetzt die Default-Services | ersetzt die Default-Services |
| Funktionsschnittstelle | vorhandene IOX InterlisFunction | vorhandene IOX InterlisFunction |
| Defekte ausgewählte Klasse / Service | klare VALIDATION_FUNCTION-Konfigurationsdiagnose | VALIDATION_ENGINE_FAILED mit erhaltener Ursache und geschütztem Bericht |
| Doppelter qualifizierter Funktionsname | VALIDATION_FUNCTION_DUPLICATE mit Namen und Klassen | Engine-Konfigurationsfehler mit Namen und Klassen |
| Tatsächlich ausgewertete fehlende Funktion | VALIDATION_FUNCTION_MISSING | Engine-Konfigurationsfehler, konkretes IOX-Ereignis im Bericht |
| Nur deklarierte, nicht benötigte Funktion | kein pauschaler Modellfehler | ebenso |
| Weitere Optionen | bestehende Formatfelder, lokale/Repository-Konfiguration | unverändert dataFile, modelDirectory, validationConfig |

Geprüft wurde das vorhandene IOX 1.24.4: `PluginLoader` kennt die genannte
Property, überspringt aber Ladefehler; die Funktionsabbildung überschreibt
Namenskollisionen. Sein Ordnerscanner ist kein Java-ServiceLoader. Deshalb
registriert der kleine Adapter über die vorhandenen IOX-Typen strikt, ohne eine
neue Plugin-Schnittstelle oder Isolation einzuführen. Die schmale Bibliothek
bleibt vom Formatmodul unabhängig.

IOX 1.24.4 meldet eine ausgewertete fehlende externe Funktion als Warnung
„Function … is not yet implemented“ und kann sonst Erfolg zurückgeben. Die
Adapter erkennen diese versionsgebundene IOX-Diagnose (auch Set-/Uniqueness-
Constraints), erhalten sie und lassen die Validierung ausdrücklich fehlschlagen.
Andere Warnungen bleiben Warnungen. Diese Regel wird mit einem tatsächlich
benötigten Funktionsaufruf geprüft; reine Klassenanwesenheit genügt nicht.
`FunctionLocalJobIT` prüft positive und negative HTTP-Ergebnisse der mitgelieferten
Funktion sowie fehlende Auswahl, defekte Auswahl und doppelte Namen als JAR-Jobs.

Der ursprüngliche NGK-Fall nutzt den bestehenden schmalen `validationConfig`-
Pfad: `additionalModels` und `allObjectsAccessible` stehen im vorhandenen
IOX-Abschnitt `PARAMETER`. Das ersetzt die frühere Meta-Konfigurationsindirektion
und erweitert die IliValidator-DSL nicht. Lokale versionierte Modelle und
INTERLIS-Objektkennungen im geschützten Bericht erhalten das fachliche Orakel.
