package eu.pretix.pretixscan.scanproxy.endpoints

import eu.pretix.libpretixsync.api.DefaultHttpClientFactory
import eu.pretix.libpretixsync.check.AsyncCheckProvider
import eu.pretix.libpretixsync.check.CheckException
import eu.pretix.libpretixsync.check.OnlineCheckProvider
import eu.pretix.libpretixsync.check.TicketCheckProvider
import eu.pretix.libpretixsync.db.*
import eu.pretix.pretixscan.scanproxy.PretixScanConfig
import eu.pretix.pretixscan.scanproxy.ProxyFileStorage
import eu.pretix.pretixscan.scanproxy.Server
import io.javalin.http.Context
import io.javalin.http.Handler
import io.javalin.http.NotFoundResponse
import io.requery.Persistable
import org.slf4j.LoggerFactory


fun getCheckProvider(event: String, list: Long): TicketCheckProvider {
    if (Server.connectivityHelper.isOffline) {
        return AsyncCheckProvider(
            PretixScanConfig(Server.dataDir, event, null),
            event,
            Server.syncData,
            list,
        )
    } else {
        return OnlineCheckProvider(
            PretixScanConfig(Server.dataDir, event, null),
            DefaultHttpClientFactory(),
            Server.syncData,
            ProxyFileStorage(),
            list,
        )
    }
}


object StatusEndpoint : Handler {
    override fun handle(ctx: Context) {
        val acp = getCheckProvider(
            ctx.pathParam("event"),
            ctx.pathParam("list").toLong()
        )
        try {
            ctx.json(acp.status()!!)
        } catch (e: CheckException) {
            ctx.status(400).json(mapOf("title" to e.message))
        }
    }
}

data class CheckInput(
    val ticketid: String,
    val answers: List<Answer>?,
    val ignore_unpaid: Boolean,
    val with_badge_data: Boolean,
    val type: String?
)

object CheckEndpoint : JsonBodyHandler<CheckInput>(CheckInput::class.java) {
    override fun handle(ctx: Context, body: CheckInput) {
        val LOG = LoggerFactory.getLogger("eu.pretix.pretixscan.scanproxy.endpoints.CheckEndpoint")

        val acp = getCheckProvider(
            ctx.pathParam("event"),
            ctx.pathParam("list").toLong()
        )
        val startedAt = System.currentTimeMillis()
        try {
            val type = TicketCheckProvider.CheckInType.valueOf((body.type ?: "entry").toUpperCase())
            val result = acp.check(body.ticketid, body.answers, body.ignore_unpaid, body.with_badge_data, type)
            LOG.info("Scanned ticket '${body.ticketid}' result '${result.type}' time '${(System.currentTimeMillis() - startedAt) / 1000f}s' provider '${acp.javaClass.simpleName}'")
            ctx.json(result)
            if (acp is OnlineCheckProvider) {
                if (result.type == TicketCheckProvider.CheckResult.Type.ERROR) {
                    Server.connectivityHelper.recordError()
                } else {
                    Server.connectivityHelper.recordSuccess(System.currentTimeMillis() - startedAt)
                }
            }
        } catch (e: CheckException) {
            Server.connectivityHelper.recordError()
            ctx.status(400).json(mapOf("title" to e.message))
        }
    }
}

data class SearchInput(
    val query: String,
    val page: Int
)

object SearchEndpoint : JsonBodyHandler<SearchInput>(SearchInput::class.java) {
    override fun handle(ctx: Context, body: SearchInput) {
        val acp = getCheckProvider(
            ctx.pathParam("event"),
            ctx.pathParam("list").toLong()
        )
        try {
            ctx.json(acp.search(body.query, body.page))
        } catch (e: CheckException) {
            Server.connectivityHelper.recordError()
            ctx.status(400).json(mapOf("title" to e.message))
        }
    }
}
