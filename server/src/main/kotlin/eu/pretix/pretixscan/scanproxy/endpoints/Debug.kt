package eu.pretix.pretixscan.scanproxy.endpoints

import eu.pretix.libpretixsync.DummySentryImplementation
import eu.pretix.libpretixsync.api.PretixApi
import eu.pretix.libpretixsync.sync.SyncManager
import eu.pretix.pretixscan.scanproxy.PretixScanConfig
import eu.pretix.pretixscan.scanproxy.ProxyFileStorage
import eu.pretix.pretixscan.scanproxy.Server
import eu.pretix.pretixscan.scanproxy.db.DownstreamDeviceEntity
import eu.pretix.pretixscan.scanproxy.db.SyncedEventEntity
import io.javalin.http.*
import io.requery.kotlin.eq
import java.io.File
import java.util.*


object SyncNow : Handler {
    override fun handle(ctx: Context) {
        val result = (Server.proxyData select (SyncedEventEntity::class)).get()
        for (ev in result) {
            val configStore = PretixScanConfig(Server.dataDir, ev.slug, 0)
            if (!configStore.isConfigured) {
                throw ServiceUnavailableResponse("Not configured")
            }

            val syncManager = SyncManager(
                configStore,
                PretixApi.fromConfig(configStore),
                DummySentryImplementation(),
                Server.syncData,
                ProxyFileStorage(),
                1000,
                30000,
                false
            )
            syncManager!!.sync(true)
        }
    }
}

