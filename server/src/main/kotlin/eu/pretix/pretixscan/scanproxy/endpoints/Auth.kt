package eu.pretix.pretixscan.scanproxy.endpoints

import eu.pretix.pretixscan.scanproxy.PretixScanConfig
import eu.pretix.pretixscan.scanproxy.db.DownstreamDeviceEntity
import eu.pretix.pretixscan.scanproxy.db.SyncedEventEntity
import eu.pretix.pretixscan.scanproxy.proxyDeps
import io.javalin.http.*

object DeviceAuth : Handler {
    override fun handle(ctx: Context) {
        if (!proxyDeps.configStore.isConfigured) {
            throw ServiceUnavailableResponse("Not configured")
        }

        if (ctx.pathParamMap().containsKey("organizer") && proxyDeps.configStore.organizerSlug != ctx.pathParam("organizer")) {
            throw NotFoundResponse("Unknown organizer")
        }

        if (ctx.header("Authorization") == null) {
            throw UnauthorizedResponse("Authorization required")
        }
        val auth = ctx.header("Authorization")!!.split(" ")
        if (auth[0] != "Device") {
            throw UnauthorizedResponse("Device auth required")
        }

        val result = (
                proxyDeps.proxyData
                        select (DownstreamDeviceEntity::class)
                        where (DownstreamDeviceEntity.API_TOKEN.eq(auth[1]))
                ).get()
        val device = result.firstOrNull() ?: throw UnauthorizedResponse("Unknown device token")
        ctx.attribute("device", device)
    }
}

object EventRegister : Handler {
    override fun handle(ctx: Context) {
        val ev = (
                proxyDeps.proxyData select (SyncedEventEntity::class)
                        where (SyncedEventEntity.SLUG eq ctx.pathParam("event"))
                ).get().firstOrNull()
        if (ev == null) {
            val s = SyncedEventEntity()
            s.slug = ctx.pathParam("event")
            proxyDeps.proxyData.insert(s)
        }
    }
}

object AdminAuth : Handler {
    override fun handle(ctx: Context) {
        val validauth = System.getProperty("pretixscan.adminauth", "nope")
        if (validauth == "nope") {
            if (ctx.ip() != "127.0.0.1" && ctx.ip() != "[0:0:0:0:0:0:0:1]") {
                throw ForbiddenResponse("Only local access is allowed")
            }
        }
        if (ctx.header("Authorization") != null) {
            if (ctx.basicAuthCredentials()?.username == validauth.split(":")[0] && ctx.basicAuthCredentials()?.password == validauth.split(":")[1]) {
                return
            }
        }
        ctx.header("WWW-Authenticate", "Basic realm=\"pretixSCAN proxy\"")
        throw UnauthorizedResponse("Auth required")
    }
}
