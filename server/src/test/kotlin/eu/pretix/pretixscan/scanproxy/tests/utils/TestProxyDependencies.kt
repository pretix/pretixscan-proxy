package eu.pretix.pretixscan.scanproxy.tests.utils

import eu.pretix.libpretixsync.api.HttpClientFactory
import eu.pretix.libpretixsync.api.PretixApi
import eu.pretix.libpretixsync.api.RateLimitInterceptor
import eu.pretix.pretixscan.scanproxy.ProxyDependencies
import eu.pretix.pretixscan.scanproxy.ProxyScanConfig
import eu.pretix.pretixscan.scanproxy.db.createProxyDatabase
import eu.pretix.pretixscan.scanproxy.db.createSyncDatabase
import eu.pretix.pretixscan.scanproxy.sqldelight.proxy.ProxyDatabase
import eu.pretix.pretixscan.scanproxy.sqldelight.sync.SyncDatabase
import eu.pretix.pretixscan.scanproxy.tests.test.FakePretixApi
import io.requery.Persistable
import io.requery.cache.EntityCacheBuilder
import io.requery.sql.ConfigurationBuilder
import io.requery.sql.EntityDataStore
import okhttp3.OkHttpClient
import org.postgresql.ds.PGSimpleDataSource
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.sql.DriverManager
import java.util.concurrent.TimeUnit
import kotlin.io.path.absolutePathString

class TestProxyDependencies : ProxyDependencies() {

    override val syncData: EntityDataStore<Persistable> by lazy {
        val url = System.getProperty("pretixscan.database")

        val conn = DriverManager.getConnection(url)
        var exists = false
        val r = conn.metaData.getTables(null, null, "_version", arrayOf("TABLE"))
        while (r.next()) {
            if (r.getString("TABLE_NAME") == "_version") {
                exists = true
                break
            }
        }

        val dataSource = PGSimpleDataSource()
        dataSource.setURL(url)
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

    override val dataDir: String by lazy {
        Files.createTempDirectory("proxytests").absolutePathString()
    }

    override val httpClientFactory: HttpClientFactory by lazy {
        object : HttpClientFactory {
            override fun buildClient(ignore_ssl: Boolean): OkHttpClient {
                return OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .addInterceptor(RateLimitInterceptor())
                    .build()
            }
        }
    }

    override val configStore: ProxyScanConfig by lazy {
        TestConfigStore()
    }

    override val pretixApi: PretixApi by lazy {
        FakePretixApi()
    }

    override fun init() {
        super.init()
        System.setProperty("pretixscan.adminauth", "foo:bar")
    }

    override val proxyDb: ProxyDatabase
        get() {
            val LOG = LoggerFactory.getLogger(TestProxyDependencies::class.java)
            return createProxyDatabase(
                url = System.getProperty("pretixscan.database"),
                LOG = LOG,
            )
        }

    override val db: SyncDatabase
        get() {
            val LOG = LoggerFactory.getLogger(TestProxyDependencies::class.java)
            return createSyncDatabase(
                url = System.getProperty("pretixscan.database"),
                LOG = LOG,
            )
        }
}
