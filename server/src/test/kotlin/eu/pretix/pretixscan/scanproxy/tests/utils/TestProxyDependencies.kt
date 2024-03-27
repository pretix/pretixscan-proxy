package eu.pretix.pretixscan.scanproxy.tests.utils

import eu.pretix.libpretixsync.api.HttpClientFactory
import eu.pretix.libpretixsync.api.PretixApi
import eu.pretix.libpretixsync.api.RateLimitInterceptor
import eu.pretix.libpretixsync.config.ConfigStore
import eu.pretix.pretixscan.scanproxy.Models
import eu.pretix.pretixscan.scanproxy.ProxyDependencies
import eu.pretix.pretixscan.scanproxy.ProxyScanConfig
import eu.pretix.pretixscan.scanproxy.db.Migrations
import eu.pretix.pretixscan.scanproxy.tests.test.FakePretixApi
import io.requery.Persistable
import io.requery.cache.EntityCacheBuilder
import io.requery.sql.ConfigurationBuilder
import io.requery.sql.EntityDataStore
import io.requery.sql.KotlinConfiguration
import io.requery.sql.KotlinEntityDataStore
import okhttp3.OkHttpClient
import org.sqlite.SQLiteConfig
import org.sqlite.SQLiteDataSource
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.io.path.absolutePathString

class TestProxyDependencies() : ProxyDependencies() {

    private fun byteArray2Hex(hash: ByteArray): String {
        val formatter = Formatter()
        for (b in hash) {
            formatter.format("%02x", b)
        }
        return formatter.toString()
    }

    private fun createTemporarySqliteSource(): SQLiteDataSource {
        val randomBytes = ByteArray(32) // length is bounded by 7

        Random().nextBytes(randomBytes)
        val md = MessageDigest.getInstance("SHA-1")
        //md.update(name.getMethodName().toByteArray())
        md.update(randomBytes)
        val dbname = byteArray2Hex(md.digest())

        val dataSource = SQLiteDataSource()
        val tmpfile = File.createTempFile(dbname, "sqlite3")
        tmpfile.deleteOnExit()

        dataSource.url = "jdbc:sqlite:file:$tmpfile"
        return dataSource
    }

    override val proxyData: KotlinEntityDataStore<Persistable> by lazy {
        val dataSource = createTemporarySqliteSource()
        val config = SQLiteConfig()
        config.setDateClass("TEXT")
        dataSource.config = config
        dataSource.setEnforceForeignKeys(true)

        val model = Models.DEFAULT
        Migrations.migrate(dataSource, true)
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

    override val syncData: EntityDataStore<Persistable> by lazy {
        val dataSource = createTemporarySqliteSource()
        val config = SQLiteConfig()
        config.setDateClass("TEXT")
        dataSource.config = config
        dataSource.setEnforceForeignKeys(true)

        val model = eu.pretix.libpretixsync.Models.DEFAULT
        eu.pretix.libpretixsync.db.Migrations.migrate(dataSource, true)
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
}