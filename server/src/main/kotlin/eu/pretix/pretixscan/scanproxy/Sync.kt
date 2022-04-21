package eu.pretix.pretixscan.scanproxy

import eu.pretix.libpretixsync.DummySentryImplementation
import eu.pretix.libpretixsync.api.PretixApi
import eu.pretix.libpretixsync.sync.AllEventsSyncAdapter
import eu.pretix.libpretixsync.sync.AllSubEventsSyncAdapter
import eu.pretix.libpretixsync.sync.SyncManager
import eu.pretix.pretixscan.scanproxy.Server.VERSION
import eu.pretix.pretixscan.scanproxy.Server.VERSION_CODE
import eu.pretix.pretixscan.scanproxy.db.SyncedEventEntity
import org.json.JSONObject
import org.slf4j.LoggerFactory
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock


val syncLock = ReentrantLock()

class UnconfiguredException : Exception()
class LockedException : Exception()
class SyncFailedException(message: String) : Exception(message)


fun syncEventList() {
    val fileStorage = ProxyFileStorage()
    val configStore = PretixScanConfig(Server.dataDir, "", null)
    val api = PretixApi.fromConfig(configStore)
    if (!configStore.isConfigured) {
        throw UnconfiguredException()
    }

    AllEventsSyncAdapter(Server.syncData, fileStorage, configStore.eventSlug, api, configStore.syncCycleId, null)
        .download()
    AllSubEventsSyncAdapter(Server.syncData, fileStorage, configStore.eventSlug, api, configStore.syncCycleId, null)
        .download()
}


fun syncAllEvents(force: Boolean = false) {
    val LOG = LoggerFactory.getLogger("eu.pretix.pretixscan.scanproxy.syncAllEvents")

    val result = (Server.proxyData select (SyncedEventEntity::class)).get().map { it.slug }.toSortedSet()
    for (ev in result) {
        LOG.info("Starting to sync ${ev}…")
        val configStore = PretixScanConfig(Server.dataDir, ev, null)
        if (!configStore.isConfigured) {
            throw UnconfiguredException()
        }
        if (syncLock.isLocked) {
            throw LockedException()
        }

		syncLock.withLock {
			val syncManager = SyncManager(
				configStore,
				PretixApi.fromConfig(configStore),
				DummySentryImplementation(),
				Server.syncData,
				ProxyFileStorage(),
				1000,
				30000,
				SyncManager.Profile.PRETIXSCAN,
				false,
				VERSION_CODE,
				JSONObject(),
				System.getProperty("os.name"),
				System.getProperty("os.version"),
				"pretixSCANPROXY",
				VERSION,
				null
			)
			syncManager!!.keepSlugs.addAll(result)
			syncManager!!.sync(force) {
				LOG.info("[$ev] $it")
			}
			if (configStore.lastFailedSync > 0) {
				LOG.info(configStore.lastFailedSyncMsg)
				throw SyncFailedException(configStore.lastFailedSyncMsg)
			}
		}
    }
}
