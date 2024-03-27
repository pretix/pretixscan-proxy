package eu.pretix.pretixscan.scanproxy.endpoints

import eu.pretix.pretixscan.scanproxy.PretixScanConfig
import eu.pretix.pretixscan.scanproxy.Server
import eu.pretix.pretixscan.scanproxy.db.DownstreamDeviceEntity
import eu.pretix.pretixscan.scanproxy.db.SyncedEventEntity
import eu.pretix.pretixscan.scanproxy.proxyDeps
import io.javalin.http.*
import io.requery.kotlin.eq
import java.util.*

data class SetupDownstreamInitResponse(
    val token: String,
    val url: String,
    val handshake_version: Int
)


data class SetupDownstreamInitRequest(val name: String)


object SetupDownstreamInit : JsonBodyHandler<SetupDownstreamInitRequest>(SetupDownstreamInitRequest::class.java) {
    private val charPool: List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9')
    override fun handle(ctx: Context, body: SetupDownstreamInitRequest) {
        if (!proxyDeps.configStore.isConfigured) {
            throw ServiceUnavailableResponse("Not configured")
        }

        val baseurl = System.getProperty("pretixscan.baseurl", "http://URLNOTSET")
        val d = DownstreamDeviceEntity()
        d.uuid = UUID.randomUUID().toString()
        d.name = body.name
        d.added_datetime = System.currentTimeMillis().toString()
        d.init_token = "proxy=" + (1..16)
            .map { kotlin.random.Random.nextInt(0, charPool.size) }
            .map(charPool::get)
            .joinToString("")
        proxyDeps.proxyData.insert(d)
        ctx.json(
            SetupDownstreamInitResponse(
                token = d.init_token!!,
                url = baseurl,
                handshake_version = 1
            )
        )
    }
}


object SetupDownstreamInitReady : JsonBodyHandler<SetupDownstreamInitRequest>(SetupDownstreamInitRequest::class.java) {
    private val charPool: List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9')
    override fun handle(ctx: Context, body: SetupDownstreamInitRequest) {
        if (!proxyDeps.configStore.isConfigured) {
            throw ServiceUnavailableResponse("Not configured")
        }

        val baseurl = System.getProperty("pretixscan.baseurl", "http://URLNOTSET")
        val d = DownstreamDeviceEntity()
        d.uuid = UUID.randomUUID().toString()
        d.name = body.name
        d.added_datetime = System.currentTimeMillis().toString()
        d.api_token = (1..64)
            .map { kotlin.random.Random.nextInt(0, charPool.size) }
            .map(charPool::get)
            .joinToString("")
        proxyDeps.proxyData.insert(d)
        ctx.json(
            SetupDownstreamInitResponse(
                token = d.api_token!!,
                url = baseurl,
                handshake_version = 1
            )
        )
    }
}

data class SetupDownstreamRequest(
    val token: String,
    val hardware_brand: String,
    val hardware_model: String,
    val software_brand: String,
    val software_version: String
)

data class SetupDownstreamResponse(
    val organizer: String,
    val device_id: Long,
    val unique_serial: String,
    val api_token: String,
    val name: String
)

object SetupDownstream : JsonBodyHandler<SetupDownstreamRequest>(SetupDownstreamRequest::class.java) {
    private val charPool: List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9')
    override fun handle(ctx: Context, body: SetupDownstreamRequest) {
        if (!proxyDeps.configStore.isConfigured) {
            throw ServiceUnavailableResponse("Not configured")
        }

        val result = (proxyDeps.proxyData
                select (DownstreamDeviceEntity::class) where (DownstreamDeviceEntity.INIT_TOKEN.eq(body.token))).get()
        val device = result.firstOrNull()
        if (device == null) {
            ctx.json(mapOf("token" to listOf("This initialization token is not known."))).status(400)
        } else {
            device.init_token = null
            device.api_token = (1..64)
                .map { kotlin.random.Random.nextInt(0, charPool.size) }
                .map(charPool::get)
                .joinToString("")
            proxyDeps.proxyData.update(device)
            ctx.json(SetupDownstreamResponse(
                organizer = proxyDeps.configStore.organizerSlug,
                device_id = 1,
                unique_serial = (1..12)
                    .map { kotlin.random.Random.nextInt(0, charPool.size) }
                    .map(charPool::get)
                    .joinToString(""),
                api_token = device.api_token!!,
                name = "Downstream device"
            ))
        }
    }
}


data class RemoveDeviceRequest(val uuid: String)

object SetupDownstreamRemove : JsonBodyHandler<RemoveDeviceRequest>(RemoveDeviceRequest::class.java) {
    override fun handle(ctx: Context, body: RemoveDeviceRequest) {
        ctx.json(
            mapOf(
                "result" to (proxyDeps.proxyData delete (DownstreamDeviceEntity::class) where (DownstreamDeviceEntity.UUID eq body.uuid)).get()
                    .value()
            )
        )
    }
}

object UpstreamVersion : Handler {
    override fun handle(ctx: Context) {
        val configStore = proxyDeps.configStore
        ctx.json(
            mapOf(
                "pretix" to "?",
                "pretix_numeric" to configStore.knownPretixVersion,
                "pretixscan_proxy" to Server.VERSION,
                "pretixscan_proxy_numeric" to Server.VERSION_CODE,
            )
        )
    }
}
