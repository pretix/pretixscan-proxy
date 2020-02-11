package eu.pretix.pretixscan.scanproxy.endpoints

import eu.pretix.libpretixsync.api.DefaultHttpClientFactory
import eu.pretix.libpretixsync.db.CheckInList
import eu.pretix.libpretixsync.db.QueuedCheckIn
import eu.pretix.libpretixsync.setup.*
import eu.pretix.pretixscan.scanproxy.PretixScanConfig
import eu.pretix.pretixscan.scanproxy.Server
import eu.pretix.pretixscan.scanproxy.Server.VERSION
import eu.pretix.pretixscan.scanproxy.db.SyncedEventEntity
import eu.pretix.pretixscan.scanproxy.syncEventList
import io.javalin.http.BadRequestResponse
import io.javalin.http.Context
import io.javalin.http.Handler
import io.javalin.http.InternalServerErrorResponse
import java.io.IOException
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
    data class QueueState(
        val event: String,
        val list: String,
        val count: Int
    )

    private fun queues(): List<QueueState> {
        val res = mutableListOf<QueueState>()
        val result = (Server.proxyData select (SyncedEventEntity::class)).get().map { it.slug }.toSortedSet()
        for (ev in result) {
            val lists =
                Server.syncData.select(CheckInList::class.java).where(CheckInList.EVENT_SLUG.eq(ev)).get().toList()
            for (l in lists) {
                val cnt = Server.syncData.count(QueuedCheckIn::class.java)
                    .where(QueuedCheckIn.CHECKIN_LIST_ID.eq(l.getServer_id())).get().value()
                res.add(
                    QueueState(
                        ev,
                        l.getName(),
                        cnt ?: 0
                    )
                )
            }
        }
        return res
    }

    override fun handle(ctx: Context) {
        val configStore = PretixScanConfig(Server.dataDir, "", 0)
        ctx.json(
            mapOf(
                "configured" to configStore.isConfigured,
                "organizer" to configStore.organizerSlug,
                "upstreamUrl" to configStore.apiUrl,
                "syncedEvents" to (Server.proxyData select (SyncedEventEntity::class)).get().map {
                    val localStore = PretixScanConfig(Server.dataDir, it.slug, null)
                    return@map mapOf(
                        "slug" to it.slug,
                        "lastSync" to Date(localStore.lastSync).toString(),
                        "lastFailedSync" to Date(localStore.lastFailedSync).toString(),
                        "lastFailedSyncMsg" to localStore.lastFailedSyncMsg,
                        "lastDownload" to Date(localStore.lastDownload).toString()
                    )
                }.toList(),
                "queues" to queues()
            )
        )
    }
}
