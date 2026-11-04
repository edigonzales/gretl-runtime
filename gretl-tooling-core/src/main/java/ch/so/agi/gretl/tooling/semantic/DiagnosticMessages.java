package ch.so.agi.gretl.tooling.semantic;

import java.util.Map;

/** Stable codes are the machine contract. Unknown languages use English. No source values are rendered. */
final class DiagnosticMessages {
    private DiagnosticMessages() { }
    private static final Map<String,String[]> MESSAGES=Map.ofEntries(
        entry("DUPLICATE_TASK_ID","Duplicate task or action name.","Task- oder Actionname ist doppelt."),
        entry("DUPLICATE_PARAMETER","Duplicate parameter name.","Parametername ist doppelt."),
        entry("DUPLICATE_CLEANUP_NAME","Duplicate cleanup name.","Cleanupname ist doppelt."),
        entry("INVALID_NAME","Invalid declaration or reference name.","Ungueltiger Deklarations- oder Referenzname."),
        entry("UNKNOWN_TASK_REFERENCE","Unknown task or action.","Unbekannter Task oder Action."),
        entry("UNKNOWN_PARAMETER","Unknown parameter.","Unbekannter Parameter."),
        entry("PROJECT_ENTRY_MISSING","Required project entry is missing.","Ein erforderlicher Projekteintrag fehlt."),
        entry("AMBIGUOUS_REFERENCE","The reference has multiple definitions.","Die Referenz hat mehrere Definitionen."),
        entry("DYNAMIC_REFERENCE","Dynamic reference cannot be resolved statically.","Dynamische Referenz ist statisch nicht aufloesbar."),
        entry("PARTIAL_ANALYSIS","Some declarations or references cannot be analyzed statically.","Einige Deklarationen oder Referenzen sind statisch nicht analysierbar."),
        entry("CATALOG_UNAVAILABLE","Operation catalog is unavailable or incomplete.","Operationskatalog ist nicht oder nur teilweise verfuegbar."),
        entry("UNKNOWN_OPERATION","Operation is not present in the complete catalog.","Operation fehlt im vollstaendigen Katalog."),
        entry("RESERVED_CONFIGURATION_FIELD","Reserved configuration field cannot be assigned.","Reserviertes Konfigurationsfeld darf nicht zugewiesen werden."),
        entry("INNER_INVOCATION_SCOPE","Inner invocation is unavailable in this scope.","Innere Invocation ist in diesem Kontext nicht verfuegbar."),
        entry("UNKNOWN_INPUT","Unknown operation input.","Unbekanntes Operationseingabefeld."),
        entry("REQUIRED_INPUT_NULL","Literal null may not satisfy this required input.","Literales null erfuellt dieses Pflichtfeld moeglicherweise nicht."),
        entry("REQUIRED_INPUT_CONDITIONAL","Required input is missing if this conditional configuration executes.","Erforderliches Eingabefeld fehlt, falls diese bedingte Konfiguration ausgefuehrt wird."),
        entry("REQUIRED_INPUT","Required operation input is missing.","Erforderliches Operationseingabefeld fehlt."),
        entry("INPUT_CONVERSION","Literal input may require conversion to the described type.","Literale Eingabe muss moeglicherweise in den beschriebenen Typ umgewandelt werden."),
        entry("INPUT_TYPE","Literal input has an incompatible type.","Literale Eingabe hat einen unpassenden Typ."),
        entry("INPUT_ENUM","Literal input is outside the allowed values.","Literale Eingabe liegt ausserhalb der erlaubten Werte."),
        entry("RESULT_DEPENDENCY_REQUIRED","Result access requires a direct or transitive data dependency.","Resultatzugriff erfordert eine direkte oder transitive Datenabhaengigkeit."),
        entry("RESULT_SCOPE","Result access is unavailable in this scope.","Resultatzugriff ist in diesem Kontext nicht verfuegbar."),
        entry("UNKNOWN_OUTPUT","Unknown output of the producer operation.","Unbekanntes Ausgabefeld der erzeugenden Operation."),
        entry("TOPLEVEL_PARAMETER_READ","A top-level parameter declaration requires options and a description.","Eine Parameterdeklaration auf oberster Ebene erfordert Optionen und eine Beschreibung."),
        entry("PARAMETER_DESCRIPTION","Parameter description must be nonblank.","Parameterbeschreibung darf nicht leer sein."),
        entry("PARAMETER_TYPE","Unsupported parameter type.","Nicht unterstuetzter Parametertyp."),
        entry("PARAMETER_DEFAULT","Parameter default is incompatible with its declaration.","Parameterdefault passt nicht zur Deklaration."),
        entry("PARAMETER_REQUIRED_DEFAULT","A required parameter cannot have a default.","Ein erforderlicher Parameter darf keinen Default haben."),
        entry("PARAMETER_READ_TYPE","Requested parameter type is incompatible with its declaration.","Angeforderter Parametertyp passt nicht zur Deklaration."),
        entry("UNKNOWN_OPTION","Unknown declaration option.","Unbekannte Deklarationsoption."),
        entry("DUPLICATE_DEFAULT_TARGET","Duplicate default target.","Defaultziel ist doppelt."),
        entry("DUPLICATE_TASK_LINK","Duplicate dependency or ordering link.","Daten- oder Reihenfolgeabhaengigkeit ist doppelt."),
        entry("DEPENDENCY_CYCLE","Cycle in the data dependency graph.","Zyklus im Datenabhaengigkeitsgraph."),
        entry("PLAN_CYCLE","Cycle in the selected plan.","Zyklus im ausgewaehlten Plan."),
        entry("SEMANTIC_LIMIT","Project exceeds the static analysis budget.","Projekt ueberschreitet das Budget fuer statische Analyse."),
        entry("SQL_PARAMETER_MISSING","SQL template refers to a missing or null parameter.","SQLvorlage referenziert einen fehlenden oder nullwertigen Parameter."),
        entry("SQL_PARAMETERS_DYNAMIC","SQL template parameters cannot be determined statically.","SQLvorlagenparameter sind statisch nicht bestimmbar."),
        entry("SQL_SOURCE_NOT_INSPECTED","SQL source cannot be inspected within the source policy.","SQLquelle kann innerhalb der Quellenpolicy nicht geprueft werden."),
        entry("PATH_DYNAMIC","Path remains symbolic; no filesystem lookup was attempted.","Pfad bleibt symbolisch; kein Dateisystemzugriff wurde versucht."),
        entry("PATH_INVALID","Invalid literal resource path.","Ungueltiger literaler Ressourcenpfad.")
    );
    private static Map.Entry<String,String[]> entry(String code,String en,String de){return Map.entry(code,new String[]{en,de});}
    static String message(String code,String language){var found=MESSAGES.get(code);return found==null?code:found[language.equalsIgnoreCase("de")||language.toLowerCase(java.util.Locale.ROOT).startsWith("de-")?1:0];}
}
