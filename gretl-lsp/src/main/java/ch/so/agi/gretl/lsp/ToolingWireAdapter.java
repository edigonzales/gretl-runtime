package ch.so.agi.gretl.lsp;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.*;
import java.io.IOException;
import java.util.*;

/** Prevents Gson coercion and unknown custom fields while leaving standard LSP deserialization intact. */
final class ToolingWireAdapter implements TypeAdapterFactory {
    public <T> TypeAdapter<T> create(Gson gson,TypeToken<T> type) {
        var raw=type.getRawType();if(!raw.isRecord()||raw.getEnclosingClass()!=GretlProtocol.class)return null;
        var delegate=gson.getDelegateAdapter(this,type);var components=Arrays.stream(raw.getRecordComponents()).collect(java.util.stream.Collectors.toMap(java.lang.reflect.RecordComponent::getName,java.lang.reflect.RecordComponent::getType));
        return new TypeAdapter<>() {
            public void write(JsonWriter output,T value)throws IOException{delegate.write(output,value);}
            public T read(JsonReader input)throws IOException {
                var tree=JsonParser.parseReader(input);if(!tree.isJsonObject())throw new JsonParseException("PROTOCOL_OBJECT_REQUIRED");
                var object=tree.getAsJsonObject();
                for(var entry:object.entrySet()) {
                    var field=components.get(entry.getKey());if(field==null)throw new JsonParseException("PROTOCOL_UNKNOWN_FIELD");
                    var value=entry.getValue();if(value.isJsonNull()){if(field.isPrimitive())throw new JsonParseException("PROTOCOL_NULL_FIELD");continue;}
                    if(field==String.class&&(!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isString()))throw new JsonParseException("PROTOCOL_STRING_REQUIRED");
                    if((field==boolean.class||field==Boolean.class)&&(!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isBoolean()))throw new JsonParseException("PROTOCOL_BOOLEAN_REQUIRED");
                    if(field==int.class||field==Integer.class) {
                        if(!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isNumber())throw new JsonParseException("PROTOCOL_INTEGER_REQUIRED");
                        try{value.getAsBigDecimal().intValueExact();}catch(ArithmeticException invalid){throw new JsonParseException("PROTOCOL_INTEGER_RANGE");}
                    }
                }
                return delegate.fromJsonTree(tree);
            }
        };
    }
}
