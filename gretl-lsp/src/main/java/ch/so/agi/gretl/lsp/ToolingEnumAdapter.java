package ch.so.agi.gretl.lsp;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.*;
import java.io.IOException;

/** Domain enum names are stable wire strings; standard LSP enums retain LSP4J's numeric mapping. */
final class ToolingEnumAdapter implements TypeAdapterFactory {
    public <T> TypeAdapter<T> create(Gson gson,TypeToken<T> type) {
        Class<? super T> raw=type.getRawType();
        if(!raw.isEnum()||!raw.getName().startsWith("ch.so.agi.gretl.tooling."))return null;
        return new TypeAdapter<>() {
            public void write(JsonWriter output,T value)throws IOException {if(value==null)output.nullValue();else output.value(((Enum<?>)value).name());}
            @SuppressWarnings("unchecked") public T read(JsonReader input)throws IOException {
                if(input.peek()==JsonToken.NULL){input.nextNull();return null;}
                if(input.peek()!=JsonToken.STRING)throw new JsonParseException("DOMAIN_ENUM_STRING_REQUIRED");
                String name=input.nextString();for(var value:raw.getEnumConstants())if(((Enum<?>)value).name().equals(name))return (T)value;
                throw new JsonParseException("DOMAIN_ENUM_UNKNOWN");
            }
        };
    }
}
