# Include-/Tooling-Handoff

Ziel ist Runtime-Includes mit sicherer partieller Toolingsicht. P0 zeigt am sonst geschlossenen Job: `apply` erzeugt PARTIAL und DYNAMIC_CODE; die Kontrolle ist COMPLETE. Dies ist noch keine C01–C08-Abnahme.

Der bestehende Resolver erfasst keine Fragmentclosure. Änderungen unerfasster Fragmente besitzen deshalb noch keine zugesicherte kontextsensitive Invalidierung. Ein allein geöffnetes Shared-Fragment hat keinen bewiesenen Einbinderkontext. Vollständige Auflösung, Navigation und Mehrdateien-Rename bleiben ausserhalb dieses Auftrags.

Spätere Integration muss offene Buffer statt zweitem Diskstand lesen und Quelle/Einbinderkontext getrennt identifizieren. Ressourcenbasis bleibt Job/Subprojekt, Includebasis die einschliessende Datei. Gemeinsame Fragmente benötigen kontextübergreifende Invalidierung und Editprüfung. Registrierungsposition und konservative Coverage bei beliebigem Groovy-Kontrollfluss bleiben erhalten. Keine Runtimeausführung/-abhängigkeit im LSP.
