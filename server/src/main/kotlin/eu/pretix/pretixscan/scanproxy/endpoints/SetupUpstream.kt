package eu.pretix.pretixscan.scanproxy.endpoints

import eu.pretix.libpretixsync.api.DefaultHttpClientFactory
import eu.pretix.libpretixsync.setup.*
import eu.pretix.pretixscan.scanproxy.PretixScanConfig
import eu.pretix.pretixscan.scanproxy.Server
import eu.pretix.pretixscan.scanproxy.Server.VERSION
import eu.pretix.pretixscan.scanproxy.db.SyncedEvent
import eu.pretix.pretixscan.scanproxy.db.SyncedEventEntity
import eu.pretix.pretixscan.scanproxy.syncEventList
import io.javalin.http.BadRequestResponse
import io.javalin.http.Context
import io.javalin.http.Handler
import io.javalin.http.InternalServerErrorResponse
import net.harawata.appdirs.AppDirsFactory
import java.io.IOException
import java.sql.Timestamp
import java.util.*
import javax.net.ssl.SSLException


data class SetupUpstreamRequest(val url: String, val token: String)

object SetupUpstream : JsonBodyHandler<SetupUpstreamRequest>(SetupUpstreamRequest::class.java) {
    override fun handle(ctx: Context, body: SetupUpstreamRequest) {
        val setupm = SetupManager(
            System.getProperty("os.name"), System.getProperty("os.version"),
            "pretixSCANPROXY", VERSION,
            DefaultHttpClientFactory()
        )

        val configStore = PretixScanConfig(Server.dataDir, "", 0)

        if (configStore.isConfigured) {
            throw BadRequestResponse("Already configured")
        }

        try {
            val init = setupm.initialize(body.url, body.token)
            configStore.setDeviceConfig(init.url, init.api_token, init.organizer, init.device_id)
            syncEventList()
            ctx.status(200)
        } catch (e: SSLException) {
            throw BadRequestResponse("SSL error")
        } catch (e: IOException) {
            throw InternalServerErrorResponse("IO error")
        } catch (e: SetupServerErrorException) {
            throw InternalServerErrorResponse("Server Error")
        } catch (e: SetupBadRequestException) {
            throw InternalServerErrorResponse("Bad Request")
        } catch (e: SetupBadResponseException) {
            throw InternalServerErrorResponse("Bad Response")
        }
    }
}


object ConfigState : Handler {
    override fun handle(ctx: Context) {
        val configStore = PretixScanConfig(Server.dataDir, "", 0)
        ctx.json(mapOf(
            "configured" to configStore.isConfigured,
            "organizer" to configStore.organizerSlug,
            "upstreamUrl" to configStore.apiUrl,
            "lastSync" to Date(configStore.lastSync).toString(),
            "lastFailedSync" to Date(configStore.lastFailedSync).toString(),
            "lastFailedSyncMsg" to configStore.lastFailedSyncMsg,
            "lastDownload" to Date(configStore.lastDownload).toString(),
            "syncedEvents" to (Server.proxyData select(SyncedEventEntity::class)).get().map {
                it.slug
            }.joinToString(",")
        ))
    }
}
