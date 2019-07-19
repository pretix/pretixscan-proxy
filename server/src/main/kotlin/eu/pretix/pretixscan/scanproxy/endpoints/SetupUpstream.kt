package eu.pretix.pretixscan.scanproxy.endpoints

import eu.pretix.libpretixsync.api.DefaultHttpClientFactory
import eu.pretix.libpretixsync.setup.*
import eu.pretix.pretixscan.scanproxy.PretixScanConfig
import eu.pretix.pretixscan.scanproxy.Server
import eu.pretix.pretixscan.scanproxy.Server.VERSION
import io.javalin.http.BadRequestResponse
import io.javalin.http.Context
import io.javalin.http.Handler
import io.javalin.http.InternalServerErrorResponse
import net.harawata.appdirs.AppDirsFactory
import java.io.IOException
import javax.net.ssl.SSLException


data class SetupUpstreamRequest(val url: String, val token: String)

object SetupUpstream : JsonBodyHandler<SetupUpstreamRequest>(SetupUpstreamRequest::class.java) {
    override fun handle(ctx: Context, body: SetupUpstreamRequest) {
        val setupm = SetupManager(
            System.getProperty("os.name"), System.getProperty("os.version"),
            "pretixSCANPROXY", VERSION,
            DefaultHttpClientFactory()
        )

        val configStore = PretixScanConfig(Server.dataDir)

        if (configStore.isConfigured) {
            throw BadRequestResponse("Already configured")
        }

        try {
            val init = setupm.initialize(body.url, body.token)
            configStore.setDeviceConfig(init.url, init.api_token, init.organizer, init.device_id)
            ctx.status(200)
        } catch (e: SSLException) {
            throw BadRequestResponse("SSL error")
        } catch (e: IOException) {
            throw InternalServerErrorResponse("IO error")
        } catch (e: SetupServerErrorException) {
            throw InternalServerErrorResponse("Server Error")
        } catch (e: SetupBadRequestException) {
            throw InternalServerErrorResponse("Bad Request")
        } catch (e: SetupBadResponseException) {
            throw InternalServerErrorResponse("Bad Response")
        }
    }
}
