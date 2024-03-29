package eu.pretix.pretixscan.scanproxy.endpoints

import com.fasterxml.jackson.databind.JsonMappingException
import io.javalin.http.BadRequestResponse
import io.javalin.http.Context
import io.javalin.http.Handler
import io.javalin.json.JavalinJackson
import org.slf4j.LoggerFactory

abstract class JsonBodyHandler<T>(private val bodyClass: Class<T>) : Handler {
    private val LOG = LoggerFactory.getLogger(JsonBodyHandler::class.java)

    abstract fun handle(ctx: Context, body: T)

    override fun handle(ctx: Context) {
        var body: T?
        try {
            body = JavalinJackson().fromJsonString(ctx.body(), bodyClass)
        } catch (e: JsonMappingException) {
            LOG.info(e.message)
            throw BadRequestResponse("Invalid JSON body")
        }
        handle(ctx, body)
    }

}

