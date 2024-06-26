package eu.pretix.pretixscan.scanproxy.tests.utils

import eu.pretix.libpretixsync.sqldelight.SyncDatabase
import eu.pretix.pretixscan.scanproxy.Models
import eu.pretix.pretixscan.scanproxy.proxyDeps
import io.requery.BlockingEntityStore
import io.requery.Persistable
import io.requery.sql.KotlinEntityDataStore
import org.junit.Before
import kotlin.reflect.KClass

abstract class BaseDatabaseTest : BaseTest() {
    private fun truncateAllTables(
        proxyData: KotlinEntityDataStore<Persistable>,
        db: SyncDatabase,
    ) {
        db.compatQueries.truncateAllTables()

        for (type in Models.DEFAULT.types) {
            proxyData.delete(type.classType.kotlin as KClass<Persistable>).get().value()
        }
    }

    @Before
    fun resetDatabaseAndSettings() {
        truncateAllTables(proxyDeps.proxyData, proxyDeps.db)
    }
}