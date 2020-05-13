package eu.pretix.pretixscan.scanproxy.endpoints

import eu.pretix.libpretixsync.check.AsyncCheckProvider
import eu.pretix.libpretixsync.check.CheckException
import eu.pretix.libpretixsync.check.TicketCheckProvider
import eu.pretix.libpretixsync.db.*
import eu.pretix.pretixscan.scanproxy.Server
import io.javalin.http.Context
import io.javalin.http.Handler
import io.javalin.http.NotFoundResponse
import io.requery.Persistable

object StatusEndpoint : Handler {
    override fun handle(ctx: Context) {
        val acp = AsyncCheckProvider(
            ctx.pathParam("event"),
            Server.syncData,
            ctx.pathParam("list").toLong()
        )
        try {
            ctx.json(acp.status())
        } catch (e: CheckException) {
            ctx.status(400).json(mapOf("title" to e.message))
        }
    }
}

data class CheckInput(
    val ticketid: String,
    val answers: List<TicketCheckProvider.Answer>?,
    val ignore_unpaid: Boolean,
    val with_badge_data: Boolean,
    val type: String?
)

object CheckEndpoint : JsonBodyHandler<CheckInput>(CheckInput::class.java) {
    override fun handle(ctx: Context, body: CheckInput) {
        val acp = AsyncCheckProvider(
            ctx.pathParam("event"),
            Server.syncData,
            ctx.pathParam("list").toLong()
        )
        try {
            val type = TicketCheckProvider.CheckInType.valueOf((body.type ?: "entry").toUpperCase())
            ctx.json(acp.check(body.ticketid, body.answers, body.ignore_unpaid, body.with_badge_data, type))
        } catch (e: CheckException) {
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
        val acp = AsyncCheckProvider(
            ctx.pathParam("event"),
            Server.syncData,
            ctx.pathParam("list").toLong()
        )
        try {
            ctx.json(acp.search(body.query, body.page))
        } catch (e: CheckException) {
            ctx.status(400).json(mapOf("title" to e.message))
        }
    }
}
