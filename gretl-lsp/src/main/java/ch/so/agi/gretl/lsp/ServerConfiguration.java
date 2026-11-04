package ch.so.agi.gretl.lsp;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Set;

/** Atomic allowlist. Launch paths, provider classes and UI preferences are never language settings. */
public record ServerConfiguration(String language,boolean trusted) {
    public static ServerConfiguration defaults(){return new ServerConfiguration("en",false);}
    public static ServerConfiguration decode(JsonElement settings,ServerConfiguration previous) {
        if(settings==null||settings.isJsonNull())return previous;
        if(!settings.isJsonObject())throw new IllegalArgumentException("CONFIG_OBJECT_REQUIRED");
        JsonObject object=settings.getAsJsonObject();
        if(object.size()>2||!Set.of("language","trusted").containsAll(object.keySet()))throw new IllegalArgumentException("CONFIG_UNKNOWN_FIELD");
        String language=previous.language();boolean trusted=previous.trusted();
        if(object.has("language")) {
            var value=object.get("language");
            if(!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isString()||!Set.of("en","de").contains(value.getAsString()))throw new IllegalArgumentException("CONFIG_LANGUAGE");
            language=value.getAsString();
        }
        if(object.has("trusted")) {
            var value=object.get("trusted");if(!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isBoolean())throw new IllegalArgumentException("CONFIG_TRUST");
            trusted=value.getAsBoolean();
        }
        return new ServerConfiguration(language,trusted);
    }
}
