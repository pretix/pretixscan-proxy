package eu.pretix.pretixscan.scanproxy.endpoints

import eu.pretix.libpretixsync.utils.HashUtils
import eu.pretix.pretixscan.scanproxy.Server
import eu.pretix.pretixscan.scanproxy.proxyDeps
import eu.pretix.pretixscan.scanproxy.sqldelight.proxy.DownstreamDevice
import io.javalin.http.Context
import io.javalin.http.Handler
import io.javalin.http.ServiceUnavailableResponse
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
        val initToken = "proxy=" + (1..16)
            .map { kotlin.random.Random.nextInt(0, charPool.size) }
            .map(charPool::get)
            .joinToString("")
        proxyDeps.proxyDb.downstreamDeviceQueries.insert(
            uuid = UUID.randomUUID().toString(),
            name = body.name,
            added_datetime = System.currentTimeMillis().toString(),
            api_token = null,
            init_token = initToken,
        )
        ctx.json(
            SetupDownstreamInitResponse(
                token = initToken,
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
        val apiToken = (1..64)
            .map { kotlin.random.Random.nextInt(0, charPool.size) }
            .map(charPool::get)
            .joinToString("")
        proxyDeps.proxyDb.downstreamDeviceQueries.insert(
            uuid = UUID.randomUUID().toString(),
            name = body.name,
            added_datetime = System.currentTimeMillis().toString(),
            api_token = apiToken,
            init_token = null,
        )
        ctx.json(
            SetupDownstreamInitResponse(
                token = apiToken,
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

private fun uniqueSerial(device: DownstreamDevice): String {
    val hash = HashUtils.toSHA1(device.uuid.toByteArray())
    return hash.uppercase().slice(0..12)
}

object SetupDownstream : JsonBodyHandler<SetupDownstreamRequest>(SetupDownstreamRequest::class.java) {
    private val charPool: List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9')
    override fun handle(ctx: Context, body: SetupDownstreamRequest) {
        if (!proxyDeps.configStore.isConfigured) {
            throw ServiceUnavailableResponse("Not configured")
        }

        val device = proxyDeps.proxyDb.downstreamDeviceQueries.selectByInitToken(body.token).executeAsOneOrNull()
        if (device == null) {
            ctx.json(mapOf("token" to listOf("This initialization token is not known."))).status(400)
        } else {
            val apiToken = (1..64)
                .map { kotlin.random.Random.nextInt(0, charPool.size) }
                .map(charPool::get)
                .joinToString("")
            proxyDeps.proxyDb.downstreamDeviceQueries.updateTokens(
                init_token = null,
                api_token = apiToken,
                uuid = device.uuid,
            )

            ctx.json(SetupDownstreamResponse(
                organizer = proxyDeps.configStore.organizerSlug,
                device_id = 1,
                unique_serial = uniqueSerial(device),
                api_token = apiToken,
                name = "Downstream device"
            ))
        }
    }
}


data class RemoveDeviceRequest(val uuid: String)

object SetupDownstreamRemove : JsonBodyHandler<RemoveDeviceRequest>(RemoveDeviceRequest::class.java) {
    override fun handle(ctx: Context, body: RemoveDeviceRequest) {
        // TODO: Is the result needed?
        val result = proxyDeps.proxyDb.transactionWithResult {
            if (proxyDeps.proxyDb.downstreamDeviceQueries.selectByUuid(body.uuid).executeAsOneOrNull() != null) {
                proxyDeps.proxyDb.downstreamDeviceQueries.deleteByUuid(body.uuid)
                1
            } else {
                0
            }
        }
        ctx.json(
            mapOf(
                "result" to result
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


object DeviceInfo : Handler {
    override fun handle(ctx: Context) {
        val configStore = proxyDeps.configStore
        val device = ctx.attribute<DownstreamDevice>("device")!!
        ctx.json(
            mapOf(
                "device" to mapOf(
                    "organizer" to configStore.organizerSlug,
                    "device_id" to 1,
                    "unique_serial" to uniqueSerial(device),
                    "api_token" to device.api_token,
                    "name" to device.name,
                    "gate" to if (configStore.deviceKnownGateID > 0) mapOf(
                        "id" to configStore.deviceKnownGateID,
                        "name" to configStore.deviceKnownGateName
                    ) else null,
                ),
                "server" to mapOf(
                    "version" to mapOf(
                        "pretix" to "?",
                        "pretix_numeric" to configStore.knownPretixVersion,
                        "pretixscan_proxy" to Server.VERSION,
                        "pretixscan_proxy_numeric" to Server.VERSION_CODE,
                    ),
                ),
                "medium_key_sets" to emptyList<Map<String, String>>()  // RSA setup not implemented
            )
        )
    }
}
