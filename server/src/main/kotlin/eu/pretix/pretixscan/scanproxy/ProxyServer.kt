package eu.pretix.pretixscan.scanproxy

import eu.pretix.pretixscan.scanproxy.endpoints.*
import com.fasterxml.jackson.databind.module.SimpleModule
import eu.pretix.pretixscan.scanproxy.serialization.JSONArraySerializer
import eu.pretix.pretixscan.scanproxy.serialization.JSONObjectSerializer
import io.javalin.Javalin
import io.javalin.apibuilder.ApiBuilder.*
import io.javalin.plugin.json.JavalinJackson
import org.json.JSONArray
import org.json.JSONObject



object Server {
    @JvmStatic
    fun main(args: Array<String>) {
        val app = Javalin.create().start(7000)

        // Map between org.json and Jackson
        val module = SimpleModule()
        module.addSerializer(JSONObject::class.java, JSONObjectSerializer())
        module.addSerializer(JSONArray::class.java, JSONArraySerializer())
        JavalinJackson.getObjectMapper().registerModule(module)

        // "Middleware"

    }
}
