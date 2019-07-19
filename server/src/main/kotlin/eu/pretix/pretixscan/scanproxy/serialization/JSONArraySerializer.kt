package eu.pretix.pretixscan.scanproxy.serialization

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import org.json.JSONArray
import org.json.JSONObject

class JSONArraySerializer(t: Class<JSONArray>? = null) : StdSerializer<JSONArray>(t) {
    override fun serialize(value: JSONArray, gen: JsonGenerator, provider: SerializerProvider) {
        gen.writeStartArray()
        for (key in (0 until value.length())) {
            if (value.isNull(key)) {
                gen.writeNull()
                continue
            }
            val v = value.get(key)
            if (v is JSONObject) {
                JSONObjectSerializer().serialize(v, gen, provider)
            } else if (v is JSONArray) {
                serialize(v, gen, provider)
            } else {
                provider.defaultSerializeValue(v, gen)
            }
        }
        gen.writeEndArray()
    }

}