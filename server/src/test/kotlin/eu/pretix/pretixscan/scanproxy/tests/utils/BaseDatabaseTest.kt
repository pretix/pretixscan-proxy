package eu.pretix.pretixscan.scanproxy.tests.utils

import eu.pretix.libpretixsync.sqldelight.SyncDatabase
import eu.pretix.pretixscan.scanproxy.proxyDeps
import eu.pretix.pretixscan.scanproxy.sqldelight.proxy.ProxyDatabase
import org.junit.Before

abstract class BaseDatabaseTest : BaseTest() {
    private fun truncateAllTables(db: SyncDatabase, proxyDb: ProxyDatabase) {
        db.compatQueries.truncateAllTables()

        proxyDb.downstreamDeviceQueries.truncate()
        proxyDb.syncedEventQueries.truncate()
    }

    @Before
    fun resetDatabaseAndSettings() {
        truncateAllTables(proxyDeps.db, proxyDeps.proxyDb)
    }
}
