package eu.pretix.pretixscan.scanproxy

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.module.SimpleModule
import eu.pretix.pretixscan.scanproxy.endpoints.*
import eu.pretix.pretixscan.scanproxy.db.DownstreamDeviceEntity
import eu.pretix.libpretixsync.serialization.JSONArraySerializer
import eu.pretix.libpretixsync.serialization.JSONObjectSerializer
import io.javalin.Javalin
import io.javalin.apibuilder.ApiBuilder.*
import io.javalin.http.staticfiles.Location
import io.javalin.json.JavalinJackson
import net.harawata.appdirs.AppDirsFactory
import org.json.JSONArray
import org.json.JSONObject
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.Executors


object Server {
    val VERSION = "2.4.0"
    val VERSION_CODE = 13
    private val LOG = LoggerFactory.getLogger(Server::class.java)

    fun createApp(): Javalin {
        val app = Javalin.create { config ->
            config.requestLogger.http { ctx, _ ->
                var device: DownstreamDeviceEntity? = ctx.attribute("device")
                val device_name = device?.name ?: ""
                LOG.info("[${ctx.ip()}] '${device_name}' ${ctx.method()} ${ctx.path()} -> ${ctx.status()}")
            }
            config.staticFiles.add("/public", Location.CLASSPATH)

            // Map between org.json and Jackson
            val module = SimpleModule()
            module.addSerializer(JSONObject::class.java, JSONObjectSerializer())
            module.addSerializer(JSONArray::class.java, JSONArraySerializer())
            config.jsonMapper(JavalinJackson().updateMapper { mapper ->
                mapper.registerModule(module)
                mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            })
        }

        app.routes {
            path("api/v1") {
                get("version", UpstreamVersion)
                post("device/initialize", SetupDownstream)
                path("device/info") {
                    before(DeviceAuth)
                    get(DeviceInfo)
                }
                path("organizers/{organizer}") {
                    before(DeviceAuth)
                    get("subevents", SubEventsEndpoint)
                    get("reusablemedia/", EmptyResourceEndpoint)
                    path("events") {
                        get(EventsEndpoint)
                        path("{event}") {
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
                            get("settings/", SettingsEndpoint)
                            get("revokedsecrets/", EmptyResourceEndpoint)
                            get("blockedsecrets/", EmptyResourceEndpoint)
                            get("subevents/{id}/", SubEventEndpoint)
                        }
                    }
                }
            }
            before("/", AdminAuth)
            path("proxyapi/v1/") {
                before("configure", AdminAuth)
                post("configure", SetupUpstream)
                before("state", AdminAuth)
                get("state", ConfigState)
                before("removeevent", AdminAuth)
                post("removeevent", RemoveEvent)
                before("addevent", AdminAuth)
                post("addevent", AddEvent)
                before("init", AdminAuth)
                post("init", SetupDownstreamInit)
                before("initready", AdminAuth)
                post("initready", SetupDownstreamInitReady)
                before("remove", AdminAuth)
                post("remove", SetupDownstreamRemove)
                before("sync", AdminAuth)
                post("sync", SyncNow)
                before("synceventlist", AdminAuth)
                post("synceventlist", SyncEventList)

                path("rpc/{event}/{list}/") {
                    before(DeviceAuth)
                    get("status/", StatusEndpoint)
                    post("search/", SearchEndpoint)
                    post("check/", CheckEndpoint)
                }
                path("rpc/") {
                    before(DeviceAuth)
                    post("search/", MultiSearchEndpoint)
                    post("check/", MultiCheckEndpoint)
                }
            }
            path("download") {
                before(DeviceAuth)
                get("{filename}", DownloadEndpoint)
            }
        }
        return app
    }

    @JvmStatic
    fun main(args: Array<String>) {
        if (!isProxyDepsInitialized()) {
            proxyDeps = ServerProxyDependencies()
        }

        val app = createApp()
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
