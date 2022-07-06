package eu.pretix.pretixscan.scanproxy.endpoints

import eu.pretix.pretixscan.scanproxy.PretixScanConfig
import eu.pretix.pretixscan.scanproxy.Server
import eu.pretix.pretixscan.scanproxy.db.DownstreamDeviceEntity
import eu.pretix.pretixscan.scanproxy.db.SyncedEventEntity
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
        val configStore = PretixScanConfig(Server.dataDir, "", 0)
        if (!configStore.isConfigured) {
            throw ServiceUnavailableResponse("Not configured")
        }

        // TODO: Require some kind of proper authentication!!!
        if (ctx.ip() != "127.0.0.1" && ctx.ip() != "0:0:0:0:0:0:0:1") {
            throw ForbiddenResponse("Only local access is allowed")
        }
        val baseurl = System.getProperty("pretixscan.baseurl", "http://URLNOTSET")
        val d = DownstreamDeviceEntity()
        d.uuid = UUID.randomUUID().toString()
        d.name = body.name
        d.added_datetime = System.currentTimeMillis().toString()
        d.init_token = "proxy=" + (1..16)
            .map { i -> kotlin.random.Random.nextInt(0, SetupDownstreamInit.charPool.size) }
            .map(SetupDownstreamInit.charPool::get)
            .joinToString("")
        Server.proxyData.insert(d)
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
        val configStore = PretixScanConfig(Server.dataDir, "", 0)
        if (!configStore.isConfigured) {
            throw ServiceUnavailableResponse("Not configured")
        }

        // TODO: Require some kind of proper authentication!!!
        if (ctx.ip() != "127.0.0.1" && ctx.ip() != "0:0:0:0:0:0:0:1") {
            throw ForbiddenResponse("Only local access is allowed")
        }
        val baseurl = System.getProperty("pretixscan.baseurl", "http://URLNOTSET")
        val d = DownstreamDeviceEntity()
        d.uuid = UUID.randomUUID().toString()
        d.name = body.name
        d.added_datetime = System.currentTimeMillis().toString()
        d.api_token = (1..64)
            .map { i -> kotlin.random.Random.nextInt(0, SetupDownstreamInitReady.charPool.size) }
            .map(SetupDownstreamInitReady.charPool::get)
            .joinToString("")
        Server.proxyData.insert(d)
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
        val configStore = PretixScanConfig(Server.dataDir, "", 0)
        if (!configStore.isConfigured) {
            throw ServiceUnavailableResponse("Not configured")
        }

        val result = (Server.proxyData
                select (DownstreamDeviceEntity::class) where (DownstreamDeviceEntity.INIT_TOKEN.eq(body.token))).get()
        val device = result.firstOrNull()
        if (device == null) {
            ctx.json(mapOf("token" to listOf("This initialization token is not known."))).status(400)
        } else {
            device.init_token = null
            device.api_token = (1..64)
                .map { i -> kotlin.random.Random.nextInt(0, charPool.size) }
                .map(charPool::get)
                .joinToString("")
            Server.proxyData.update(device)
            ctx.json(SetupDownstreamResponse(
                organizer = configStore.organizerSlug,
                device_id = 1,
                unique_serial = (1..12)
                    .map { i -> kotlin.random.Random.nextInt(0, charPool.size) }
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
                "result" to (Server.proxyData delete (DownstreamDeviceEntity::class) where (DownstreamDeviceEntity.UUID eq body.uuid)).get()
                    .value()
            )
        )
    }
}
