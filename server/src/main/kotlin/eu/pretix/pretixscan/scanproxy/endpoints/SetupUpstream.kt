package eu.pretix.pretixscan.scanproxy.endpoints

import eu.pretix.libpretixsync.api.DefaultHttpClientFactory
import eu.pretix.libpretixsync.db.CheckInList
import eu.pretix.libpretixsync.db.QueuedCheckIn
import eu.pretix.libpretixsync.setup.*
import eu.pretix.pretixscan.scanproxy.PretixScanConfig
import eu.pretix.pretixscan.scanproxy.Server
import eu.pretix.pretixscan.scanproxy.Server.VERSION
import eu.pretix.pretixscan.scanproxy.db.DownstreamDeviceEntity
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
        val list_id: Long,
        val count: Int
    )

    private fun queues(): List<QueueState> {
        val res = mutableListOf<QueueState>()
        val lists =
            Server.syncData.select(CheckInList::class.java).get().toList()
        for (l in lists) {
            val cnt = Server.syncData.count(QueuedCheckIn::class.java)
                .where(QueuedCheckIn.CHECKIN_LIST_ID.eq(l.getServer_id())).get().value()
            res.add(
                QueueState(
                    l.getEvent_slug(),
                    l.getName(),
                    l.getServer_id(),
                    cnt ?: 0
                )
            )
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
                "downstreamDevices" to (Server.proxyData select (DownstreamDeviceEntity::class) orderBy (DownstreamDeviceEntity.NAME)).get().map {
                    return@map mapOf(
                        "uuid" to it.uuid,
                        "added_datetime" to if (it.added_datetime.isNullOrBlank()) null else Date(it.added_datetime!!.toLong()).toString(),
                        "init_token" to it.init_token,
                        "name" to it.name,
                        "setup" to it.api_token.isNullOrBlank()
                    )
                }.toList(),
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

data class RemoveEventRequest(val slug: String)

object RemoveEvent : JsonBodyHandler<RemoveEventRequest>(RemoveEventRequest::class.java) {
    override fun handle(ctx: Context, body: RemoveEventRequest) {
        ctx.json(
            mapOf(
                "result" to (Server.proxyData delete (SyncedEventEntity::class) where (SyncedEventEntity.SLUG eq (body.slug))).get()
                    .value()
            )
        )
    }
}

data class AddEventRequest(val slug: String)

object AddEvent : JsonBodyHandler<AddEventRequest>(AddEventRequest::class.java) {
    override fun handle(ctx: Context, body: AddEventRequest) {
        val ev = (Server.proxyData select (SyncedEventEntity::class) where (SyncedEventEntity.SLUG eq body.slug)).get()
            .firstOrNull()
        if (ev == null) {
            val s = SyncedEventEntity()
            s.slug = body.slug
            Server.proxyData.insert(s)
        }
        ctx.json(
            mapOf(
                "result" to 1
            )
        )
    }
}
