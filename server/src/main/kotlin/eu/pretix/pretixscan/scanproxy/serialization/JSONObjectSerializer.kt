package eu.pretix.pretixscan.scanproxy.serialization

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import org.json.JSONArray
import org.json.JSONObject

class JSONObjectSerializer(t: Class<JSONObject>? = null) : StdSerializer<JSONObject>(t) {
    override fun serialize(value: JSONObject, gen: JsonGenerator, provider: SerializerProvider) {
        gen.writeStartObject()
        for (key in value.sortedKeys()) {
            val k = key as String
            if (value.isNull(key)) {
                gen.writeNullField(key)
                continue
            }
            val v = value.get(k)
            if (v is JSONObject) {
                gen.writeFieldName(k)
                serialize(v, gen, provider)
            } else if (v is JSONArray) {
                gen.writeFieldName(k)
                JSONArraySerializer().serialize(v, gen, provider)
            } else {
                provider.defaultSerializeField(k, v, gen)
            }
        }
        gen.writeEndObject()
    }

}