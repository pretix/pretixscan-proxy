package eu.pretix.pretixscan.scanproxy.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import eu.pretix.libpretixsync.sqldelight.AndroidUtilDateAdapter
import eu.pretix.libpretixsync.sqldelight.BigDecimalAdapter
import eu.pretix.libpretixsync.sqldelight.Migrations.clearResourceSyncStatusCallback
import eu.pretix.libpretixsync.sqldelight.Migrations.minVersionCallback
import eu.pretix.libpretixsync.sqldelight.SyncDatabase
import eu.pretix.pretixscan.scanproxy.sqldelight.proxy.ProxyDatabase
import org.sqlite.SQLiteConfig
import java.io.File
import java.util.*
import java.util.logging.Logger

class JvmLocalCacheFactory {
    val log = Logger.getLogger("JvmLocalCacheFactory")

    private fun jdbcConnectionString(dbFile: File): String {
        return "jdbc:sqlite:" + dbFile.absolutePath
    }

    fun deleteDataSource() {
        val dbFile = getSyncDatabasePath()
        if (dbFile.exists()) {
            log.info("Deleting database at $dbFile")
            dbFile.delete()
        }
        val dbFileP = getProxyDatabasePath()
        if (dbFileP.exists()) {
            log.info("Deleting database at $dbFileP")
            dbFileP.delete()
        }
    }

    private fun createSyncDriver(url: String): SqlDriver {
        val driver: SqlDriver = JdbcSqliteDriver(
            url = url,
            properties = Properties(1).apply {
                put(SQLiteConfig.Pragma.FOREIGN_KEYS.pragmaName, "true")
                put(SQLiteConfig.Pragma.BUSY_TIMEOUT.pragmaName, "10000")
            },
            schema = SyncDatabase.Schema,
            callbacks = arrayOf(
                minVersionCallback,
                clearResourceSyncStatusCallback,
            ),
        )
        return driver
    }

    private fun createProxyDriver(url: String): SqlDriver {
        val driver: SqlDriver = JdbcSqliteDriver(
            url = url,
            properties = Properties(1).apply {
                put(SQLiteConfig.Pragma.FOREIGN_KEYS.pragmaName, "true")
                put(SQLiteConfig.Pragma.BUSY_TIMEOUT.pragmaName, "10000")
            },
            schema = ProxyDatabase.Schema,
            callbacks = arrayOf(
                minVersionCallback,
                clearResourceSyncStatusCallback,
            ),
        )
        return driver
    }

    fun getSyncDataSource(): eu.pretix.pretixscan.scanproxy.sqldelight.sync.SyncDatabase {
        // NB: this implementation assumes the database schema has already been created by requery
        val dbFile = getSyncDatabasePath()
        val url = jdbcConnectionString(dbFile)
        log.info("Using database file with sqldelight at $dbFile")
        val driver = createSyncDriver(url)
        log.info("Enabling wal-mode")
        driver.execute(
            identifier = null,
            sql = "PRAGMA journal_mode = wal;",
            parameters = 0,
        )
        return createSyncDatabase(
            driver = driver,
            dateAdapter = AndroidUtilDateAdapter(),
            bigDecimalAdapter = BigDecimalAdapter(),
        )
    }

    fun getProxyDataSource(): ProxyDatabase {
        // NB: this implementation assumes the database schema has already been created by requery
        val dbFile = getProxyDatabasePath()
        val url = jdbcConnectionString(dbFile)
        log.info("Using database file with sqldelight at $dbFile")
        val driver = createProxyDriver(url)
        log.info("Enabling wal-mode")
        driver.execute(
            identifier = null,
            sql = "PRAGMA journal_mode = wal;",
            parameters = 0,
        )
        return createProxyDatabase(
            driver = driver,
        )
    }

}