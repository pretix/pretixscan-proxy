package eu.pretix.pretixscan.scanproxy

import eu.pretix.libpretixsync.DummySentryImplementation
import eu.pretix.libpretixsync.api.PretixApi
import eu.pretix.libpretixsync.sync.SyncManager
import eu.pretix.pretixscan.scanproxy.db.SyncedEventEntity
import org.slf4j.LoggerFactory


class UnconfiguredException : Exception()
class SyncFailedException(message: String) : Exception(message)

fun syncAllEvents(force: Boolean = false) {
    val LOG = LoggerFactory.getLogger("eu.pretix.pretixscan.scanproxy.syncAllEvents")

    val result = (Server.proxyData select (SyncedEventEntity::class)).get()
    for (ev in result) {
        LOG.info("Starting to sync ${ev.slug}…")
        val configStore = PretixScanConfig(Server.dataDir, ev.slug, null)
        if (!configStore.isConfigured) {
            throw UnconfiguredException()
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
        syncManager!!.sync(force) {
            LOG.info(it)
        }
        if (configStore.lastFailedSync > 0) {
            LOG.info(configStore.lastFailedSyncMsg)
            throw SyncFailedException(configStore.lastFailedSyncMsg)
        }
    }
}