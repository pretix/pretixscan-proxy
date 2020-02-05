package eu.pretix.pretixscan.scanproxy

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.module.SimpleModule
import eu.pretix.pretixscan.scanproxy.endpoints.*
import eu.pretix.libpretixsync.serialization.JSONArraySerializer
import eu.pretix.libpretixsync.serialization.JSONObjectSerializer
import io.javalin.Javalin
import io.javalin.apibuilder.ApiBuilder.*
import io.javalin.plugin.json.JavalinJackson
import net.harawata.appdirs.AppDirsFactory
import org.json.JSONArray
import org.json.JSONObject
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService




object Server {
    val VERSION = "0.0.2"
    val VERSION_CODE = 2
    private val LOG = LoggerFactory.getLogger(Server::class.java)
    val syncData = makeSyncDataStore()
    val proxyData = makeProxyDataStore()

    val appDirs = AppDirsFactory.getInstance()!!
    val dataDir = appDirs.getUserDataDir("pretixscanproxy", "1", "pretix")

    @JvmStatic
    fun main(args: Array<String>) {
        val app = Javalin.create { config ->
            config.requestLogger { ctx, executionTimeMs ->
                LOG.info("[${ctx.ip()}] ${ctx.method()} ${ctx.path()} -> ${ctx.status()}")
            }
            config.prefer405over404 = true
            config.addStaticFiles("/public")
        }

        // Map between org.json and Jackson
        val module = SimpleModule()
        module.addSerializer(JSONObject::class.java, JSONObjectSerializer())
        module.addSerializer(JSONArray::class.java, JSONArraySerializer())
        JavalinJackson.getObjectMapper().registerModule(module)
        JavalinJackson.getObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

        app.routes {
            path("api/v1") {
                post("device/initialize", SetupDownstream)
                path("organizers/:organizer") {
                    before(DeviceAuth)
                    get("subevents", SubEventsEndpoint)
                    path("events") {
                        get(EventsEndpoint)
                        path (":event") {
                            before(EventRegister)
                            get(EventEndpoint)
                            //get("categories/", CategoryEndpoint)
                            get("categories/", EmptyResourceEndpoint)
                            get("items/", ItemEndpoint)
                            //get("questions/", QuestionEndpoint)
                            get("questions/", EmptyResourceEndpoint)
                            get("badgelayouts/", BadgeLayoutEndpoint)
                            get("checkinlists/", CheckInListEndpoint)
                            get("orders/", EmptyResourceEndpoint)
                            get("badgeitems/", BadgeItemEndpoint)
                            get("subevents/:id/", SubEventEndpoint)
                        }
                    }
                }
            }
            path("proxyapi/v1") {
                post("configure", SetupUpstream)
                get("state", ConfigState)
                post("init", SetupDownstreamInit)
                post("sync", SyncNow)
                post("synceventlist", SyncEventList)

                path("rpc/:event/:list/") {
                    before(DeviceAuth)
                    get("status/", StatusEndpoint)
                    post("search/", SearchEndpoint)
                    post("check/", CheckEndpoint)
                }
            }
            path("download") {
                before(DeviceAuth)
                get(":filename", DownloadEndpoint)
            }
        }

        val webthread = Thread {
            app.start(7000)
        }

        val executor = Executors.newSingleThreadScheduledExecutor()
        val periodicTask = Runnable {
            try {
                syncAllEvents()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        executor.scheduleWithFixedDelay(periodicTask, 0, 10, TimeUnit.SECONDS)

        webthread.start()
    }
}
