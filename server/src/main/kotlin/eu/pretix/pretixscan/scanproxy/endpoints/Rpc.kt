package eu.pretix.pretixscan.scanproxy.endpoints

import eu.pretix.libpretixsync.api.DefaultHttpClientFactory
import eu.pretix.libpretixsync.check.AsyncCheckProvider
import eu.pretix.libpretixsync.check.CheckException
import eu.pretix.libpretixsync.check.OnlineCheckProvider
import eu.pretix.libpretixsync.check.TicketCheckProvider
import eu.pretix.libpretixsync.db.Answer
import eu.pretix.pretixscan.scanproxy.PretixScanConfig
import eu.pretix.pretixscan.scanproxy.ProxyFileStorage
import eu.pretix.pretixscan.scanproxy.Server
import eu.pretix.pretixscan.scanproxy.db.DownstreamDeviceEntity
import io.javalin.http.Context
import io.javalin.http.Handler
import org.slf4j.LoggerFactory


fun getCheckProvider(): TicketCheckProvider {
    if (Server.connectivityHelper.isOffline) {
        return AsyncCheckProvider(
            PretixScanConfig(Server.dataDir),
            Server.syncData,
        )
    } else {
        return OnlineCheckProvider(
            PretixScanConfig(Server.dataDir),
            DefaultHttpClientFactory(),
            Server.syncData,
            ProxyFileStorage(),
        )
    }
}


object StatusEndpoint : Handler {
    override fun handle(ctx: Context) {
        val acp = getCheckProvider()
        try {
            ctx.json(acp.status(ctx.pathParam("event"), ctx.pathParam("list").toLong())!!)
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
        )
        val startedAt = System.currentTimeMillis()
        try {
            val type = TicketCheckProvider.CheckInType.valueOf((body.type ?: "entry").toUpperCase())
            val result = acp.check(
                mapOf(ctx.pathParam("event") to ctx.pathParam("list").toLong()),
                body.ticketid,
                body.answers,
                body.ignore_unpaid,
                body.with_badge_data,
                type
            )
            val device: DownstreamDeviceEntity = ctx.attribute("device")!!
            LOG.info("Scanned ticket '${body.ticketid}' result '${result.type}' time '${(System.currentTimeMillis() - startedAt) / 1000f}s' device '${device.name}' provider '${acp.javaClass.simpleName}'")
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

data class MultiCheckInput(
    val events_and_checkin_lists: Map<String, Long>,
    val ticketid: String,
    val answers: List<Answer>?,
    val ignore_unpaid: Boolean,
    val with_badge_data: Boolean,
    val type: String?
)

object MultiCheckEndpoint : JsonBodyHandler<MultiCheckInput>(MultiCheckInput::class.java) {
    override fun handle(ctx: Context, body: MultiCheckInput) {
        val LOG = LoggerFactory.getLogger("eu.pretix.pretixscan.scanproxy.endpoints.MultiCheckEndpoint")

        val acp = getCheckProvider(
        )
        val startedAt = System.currentTimeMillis()
        try {
            val type = TicketCheckProvider.CheckInType.valueOf((body.type ?: "entry").toUpperCase())
            val result = acp.check(
                body.events_and_checkin_lists,
                body.ticketid,
                body.answers,
                body.ignore_unpaid,
                body.with_badge_data,
                type
            )
            val device: DownstreamDeviceEntity = ctx.attribute("device")!!
            LOG.info("Scanned ticket '${body.ticketid}' result '${result.type}' time '${(System.currentTimeMillis() - startedAt) / 1000f}s' device '${device.name}' provider '${acp.javaClass.simpleName}'")
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
        val acp = getCheckProvider()
        try {
            ctx.json(acp.search(
                mapOf(ctx.pathParam("event") to ctx.pathParam("list").toLong()),
                body.query, body.page
            ))
        } catch (e: CheckException) {
            Server.connectivityHelper.recordError()
            ctx.status(400).json(mapOf("title" to e.message))
        }
    }
}

data class MultiSearchInput(
    val events_and_checkin_lists: Map<String, Long>,
    val query: String,
    val page: Int
)

object MultiSearchEndpoint : JsonBodyHandler<MultiSearchInput>(MultiSearchInput::class.java) {
    override fun handle(ctx: Context, body: MultiSearchInput) {
        val acp = getCheckProvider()
        try {
            ctx.json(acp.search(
                body.events_and_checkin_lists,
                body.query,
                body.page
            ))
        } catch (e: CheckException) {
            Server.connectivityHelper.recordError()
            ctx.status(400).json(mapOf("title" to e.message))
        }
    }
}
