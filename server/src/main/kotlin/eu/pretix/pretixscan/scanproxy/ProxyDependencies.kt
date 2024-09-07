package eu.pretix.pretixscan.scanproxy

import eu.pretix.libpretixsync.api.DefaultHttpClientFactory
import eu.pretix.libpretixsync.api.HttpClientFactory
import eu.pretix.libpretixsync.api.PretixApi
import eu.pretix.pretixscan.scanproxy.db.Migrations
import eu.pretix.pretixscan.scanproxy.db.createSyncDatabase
import eu.pretix.pretixscan.scanproxy.sqldelight.SyncDatabase
import io.requery.Persistable
import io.requery.cache.EntityCacheBuilder
import io.requery.sql.ConfigurationBuilder
import io.requery.sql.EntityDataStore
import io.requery.sql.KotlinConfiguration
import io.requery.sql.KotlinEntityDataStore
import net.harawata.appdirs.AppDirsFactory
import org.postgresql.ds.PGSimpleDataSource
import org.slf4j.LoggerFactory
import java.sql.DriverManager

lateinit var proxyDeps: ProxyDependencies

fun isProxyDepsInitialized(): Boolean {
    return ::proxyDeps.isInitialized
}

abstract class ProxyDependencies {
    abstract val syncData: EntityDataStore<Persistable>
    abstract val proxyData : KotlinEntityDataStore<Persistable>
    abstract val db: SyncDatabase
    abstract val dataDir: String

    open val connectivityHelper = ConnectivityHelper(System.getProperty("pretixscan.autoOfflineMode", "off"))
    open val httpClientFactory: HttpClientFactory by lazy {
        DefaultHttpClientFactory()
    }
    open val configStore: ProxyScanConfig by lazy {
        PretixScanConfig(proxyDeps.dataDir)
    }
    open val fileStorage: ProxyFileStorage by lazy {
        ProxyFileStorage()
    }
    open val pretixApi: PretixApi by lazy {
        PretixApi.fromConfig(configStore, proxyDeps.httpClientFactory, null)
    }

    open fun init() {}
}

class ServerProxyDependencies: ProxyDependencies() {
    private val appDirs = AppDirsFactory.getInstance()!!
    override val dataDir = appDirs.getUserDataDir("pretixscanproxy", "1", "pretix")

    override val proxyData: KotlinEntityDataStore<Persistable> by lazy {
        val LOG = LoggerFactory.getLogger(Server::class.java)

        // TODO: Support other databases
        val conn = DriverManager.getConnection(System.getProperty("pretixscan.database"))
        var exists = false
        val r = conn.metaData.getTables(null, null, "_scanproxy_version", arrayOf("TABLE"))
        while (r.next()) {
            if (r.getString("TABLE_NAME") == "_scanproxy_version") {
                exists = true
                break
            }
        }
        if (!exists) {
            LOG.info("Creating new database.")
        }

        val dataSource = PGSimpleDataSource()
        dataSource.setURL(System.getProperty("pretixscan.database"))
        val model = Models.DEFAULT
        Migrations.migrate(dataSource, !exists)
        val configuration = KotlinConfiguration(
            dataSource = dataSource,
            model = model,
            cache = EntityCacheBuilder(model)
                .useReferenceCache(false)
                .useSerializableCache(false)
                .build()
        )
        KotlinEntityDataStore(configuration = configuration)
    }

    override val db: SyncDatabase by lazy {
        val LOG = LoggerFactory.getLogger(Server::class.java)
        createSyncDatabase(
            url = System.getProperty("pretixscan.database"),
            LOG = LOG,
        )
    }

    override val syncData: EntityDataStore<Persistable> by lazy {
        val LOG = LoggerFactory.getLogger(Server::class.java)

        // TODO: Support other databases
        val conn = DriverManager.getConnection(System.getProperty("pretixscan.database"))
        var exists = false
        val r = conn.metaData.getTables(null, null, "_version", arrayOf("TABLE"))
        while (r.next()) {
            if (r.getString("TABLE_NAME") == "_version") {
                exists = true
                break
            }
        }
        if (!exists) {
            LOG.info("Creating new database.")
        }

        val dataSource = PGSimpleDataSource()
        dataSource.setURL(System.getProperty("pretixscan.database"))
        val model = eu.pretix.libpretixsync.Models.DEFAULT
        eu.pretix.libpretixsync.db.Migrations.migrate(dataSource, !exists)
        val configuration = ConfigurationBuilder(dataSource, model)
            .setEntityCache(
                EntityCacheBuilder(model)
                    .useReferenceCache(false)
                    .useSerializableCache(false)
                    .build()
            )
            .build()

        EntityDataStore<Persistable>(configuration)
    }
}