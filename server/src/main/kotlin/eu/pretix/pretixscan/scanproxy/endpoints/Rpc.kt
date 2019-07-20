package eu.pretix.pretixscan.scanproxy.endpoints

import eu.pretix.libpretixsync.check.AsyncCheckProvider
import eu.pretix.libpretixsync.check.CheckException
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
