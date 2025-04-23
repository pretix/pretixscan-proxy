package eu.pretix.pretixscan.scanproxy.endpoints

import eu.pretix.libpretixsync.api.ApiException
import eu.pretix.libpretixsync.api.NotFoundApiException
import eu.pretix.libpretixsync.api.PretixApi.ApiResponse
import eu.pretix.libpretixsync.api.ResourceNotModified
import eu.pretix.libpretixsync.sync.SyncManager.EventSwitchRequested
import eu.pretix.pretixscan.scanproxy.*
import eu.pretix.pretixscan.scanproxy.sqldelight.proxy.DownstreamDevice
import io.javalin.http.*
import io.javalin.json.jsonMapper
import org.json.JSONException
import org.slf4j.LoggerFactory
import java.util.*

data class EventSelectionResponse(
    val event: String,
    val subevent: Long,
    val checkinlist: Long
)


object EventSelection : Handler {
    override fun handle(ctx: Context) {
        val LOG = LoggerFactory.getLogger(EventSelection::class.java)
        if (!proxyDeps.configStore.isConfigured) {
            throw ServiceUnavailableResponse("Not configured")
        }

        try {
            val resp: ApiResponse = proxyDeps.pretixApi.fetchResource(
                proxyDeps.pretixApi.apiURL("device/eventselection?${ctx.queryString()}")
            )
            if (resp.response.code == 200) {
                val eventSlug = resp.data!!.getJSONObject("event").getString("slug")
                ctx.json(resp.data!!)
                LOG.info("Asking downstream device \"${ctx.attribute<DownstreamDevice>("device")?.name}\" to " +
                        "switch to: ${resp.data!!.toString()}")

                val ev = proxyDeps.proxyDb.syncedEventQueries.selectBySlug(eventSlug).executeAsOneOrNull()
                if (ev == null) {
                    LOG.info("Adding $eventSlug to sync because it was previously not known.")
                    proxyDeps.proxyDb.syncedEventQueries.insert(
                        slug = eventSlug,
                    )
                    try {
                        syncAllEvents(true)
                    } catch (e: LockedException) {
                        // ignore
                    } catch (e: UnconfiguredException) {
                        // ignore
                    } catch (e: SyncFailedException) {
                        // ignore
                    }
                }
            }
        } catch (e: ResourceNotModified) {
            ctx.status(304)
            return
        } catch (e: NotFoundApiException) {
            ctx.status(404)
            return
        } catch (e: ApiException) {
            LOG.error("Could not fetch event selection: $e")
            // Downgrade to "nothing changed" so the downstream device does not abort sync
            ctx.status(304)
            return
        } catch (e: JSONException) {
            LOG.error("Could not fetch event selection: $e")
            // Downgrade to "nothing changed" so the downstream device does not abort sync
            ctx.status(304)
            return
        }
    }
}
