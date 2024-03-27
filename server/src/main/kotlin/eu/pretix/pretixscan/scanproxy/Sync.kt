package eu.pretix.pretixscan.scanproxy

import eu.pretix.libpretixsync.DummySentryImplementation
import eu.pretix.libpretixsync.sync.AllEventsSyncAdapter
import eu.pretix.libpretixsync.sync.AllSubEventsSyncAdapter
import eu.pretix.libpretixsync.sync.SyncManager
import eu.pretix.pretixscan.scanproxy.Server.VERSION
import eu.pretix.pretixscan.scanproxy.Server.VERSION_CODE
import org.json.JSONObject
import org.slf4j.LoggerFactory
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock


val syncLock = ReentrantLock()

class UnconfiguredException : Exception()
class LockedException : Exception()
class SyncFailedException(message: String) : Exception(message)


fun syncEventList() {
    if (!proxyDeps.configStore.isConfigured) {
        throw UnconfiguredException()
    }

    AllEventsSyncAdapter(proxyDeps.syncData, proxyDeps.fileStorage, proxyDeps.pretixApi, proxyDeps.configStore.syncCycleId, null)
        .download()
    AllSubEventsSyncAdapter(proxyDeps.syncData, proxyDeps.fileStorage, proxyDeps.pretixApi, proxyDeps.configStore.syncCycleId, null)
        .download()
}


fun syncAllEvents(force: Boolean = false) {
    val LOG = LoggerFactory.getLogger("eu.pretix.pretixscan.scanproxy.syncAllEvents")

    LOG.info("Starting to sync…")
    if (!proxyDeps.configStore.isConfigured) {
        throw UnconfiguredException()
    }
    if (syncLock.isLocked) {
        throw LockedException()
    }

    syncLock.withLock {
        val syncManager = SyncManager(
            proxyDeps.configStore,
            proxyDeps.pretixApi,
            DummySentryImplementation(),
            proxyDeps.syncData,
            proxyDeps.fileStorage,
            1000,
            30000,
            SyncManager.Profile.PRETIXSCAN,
            false,
            VERSION_CODE,
            JSONObject(),
            System.getProperty("os.name"),
            System.getProperty("os.version"),
            System.getProperty("os.name"),
            System.getProperty("os.version"),
            "pretixSCANPROXY",
            VERSION,
            null,
            proxyDeps.connectivityHelper
        )
        syncManager.sync(force) {
            LOG.info("$it")
        }
        if (proxyDeps.configStore.lastFailedSync > 0) {
            LOG.info(proxyDeps.configStore.lastFailedSyncMsg)
            throw SyncFailedException(proxyDeps.configStore.lastFailedSyncMsg)
        }
    }
}
