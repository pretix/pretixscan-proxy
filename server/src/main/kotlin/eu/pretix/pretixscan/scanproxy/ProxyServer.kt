package eu.pretix.pretixscan.scanproxy

import com.fasterxml.jackson.databind.module.SimpleModule
import eu.pretix.pretixscan.scanproxy.endpoints.SetupDownstream
import eu.pretix.pretixscan.scanproxy.endpoints.SetupDownstreamInit
import eu.pretix.pretixscan.scanproxy.endpoints.SetupUpstream
import eu.pretix.pretixscan.scanproxy.endpoints.SyncNow
import eu.pretix.pretixscan.scanproxy.serialization.JSONArraySerializer
import eu.pretix.pretixscan.scanproxy.serialization.JSONObjectSerializer
import io.javalin.Javalin
import io.javalin.apibuilder.ApiBuilder.path
import io.javalin.apibuilder.ApiBuilder.post
import io.javalin.plugin.json.JavalinJackson
import net.harawata.appdirs.AppDirsFactory
import org.json.JSONArray
import org.json.JSONObject
import org.slf4j.LoggerFactory


object Server {
    val VERSION = "0.0.1"
    private val LOG = LoggerFactory.getLogger(Server::class.java)
    val syncData = makeSyncDataStore()
    val proxyData = makeProxyDataStore()

    val appDirs = AppDirsFactory.getInstance()!!
    val dataDir = appDirs.getUserDataDir("pretixscan", "1", "pretix")

    @JvmStatic
    fun main(args: Array<String>) {
        val app = Javalin.create { config ->
            config.requestLogger { ctx, executionTimeMs ->
                LOG.info("[${ctx.ip()}] ${ctx.method()} ${ctx.path()} returned ${ctx.status()}")
            }
        }.start(7000)

        // Map between org.json and Jackson
        val module = SimpleModule()
        module.addSerializer(JSONObject::class.java, JSONObjectSerializer())
        module.addSerializer(JSONArray::class.java, JSONArraySerializer())
        JavalinJackson.getObjectMapper().registerModule(module)

        app.routes {
            path("api/v1") {
                post("device/initialize", SetupDownstream)
            }
            path("proxyapi/v1") {
                post("configure", SetupUpstream)
                post("init", SetupDownstreamInit)
                post("sync", SyncNow)
            }
        }

    }
}
