package eu.pretix.pretixscan.scanproxy.endpoints

import com.fasterxml.jackson.databind.JsonMappingException
import io.javalin.http.BadRequestResponse
import io.javalin.http.Context
import io.javalin.http.Handler
import io.javalin.plugin.json.JavalinJson

abstract class JsonBodyHandler<T>(private val bodyClass: Class<T>) : Handler {
    abstract fun handle(ctx: Context, body: T)

    override fun handle(ctx: Context) {
        var body: Any?
        try {
            body = JavalinJson.fromJson(ctx.body(), bodyClass)
        } catch (e: JsonMappingException) {
            throw BadRequestResponse("Invalid JSON body")
        }
        handle(ctx, body)
    }

}

