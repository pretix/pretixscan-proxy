package eu.pretix.pretixscan.scanproxy.endpoints

import com.fasterxml.jackson.databind.JsonMappingException
import eu.pretix.pretixscan.scanproxy.db.SyncedEventEntity
import eu.pretix.pretixscan.scanproxy.proxyDeps
import io.javalin.http.BadRequestResponse
import io.javalin.http.Context
import io.javalin.http.Handler
import io.javalin.json.JavalinJackson
import io.javalin.json.jsonMapper
import org.slf4j.LoggerFactory

abstract class JsonBodyHandler<T>(private val bodyClass: Class<T>) : Handler {
    private val LOG = LoggerFactory.getLogger(JsonBodyHandler::class.java)

    abstract fun handle(ctx: Context, body: T)

    override fun handle(ctx: Context) {
        var body: T?
        try {
            body = ctx.jsonMapper().fromJsonString(ctx.body(), bodyClass)
        } catch (e: JsonMappingException) {
            LOG.info(e.message)
            throw BadRequestResponse("Invalid JSON body")
        }
        handle(ctx, body)
    }

}


fun registerEventIfNotExists(slug: String) {
    val ev = (
            proxyDeps.proxyData select (SyncedEventEntity::class)
                    where (SyncedEventEntity.SLUG eq slug)
            ).get().firstOrNull()
    if (ev == null) {
        val s = SyncedEventEntity()
        s.slug = slug
        proxyDeps.proxyData.insert(s)
    }
}
