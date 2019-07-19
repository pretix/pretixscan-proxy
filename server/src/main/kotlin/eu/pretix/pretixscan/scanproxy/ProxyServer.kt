package eu.pretix.pretixscan.scanproxy

import com.fasterxml.jackson.databind.module.SimpleModule
import eu.pretix.pretixscan.scanproxy.db.Migrations
import eu.pretix.pretixscan.scanproxy.db.Models
import eu.pretix.pretixscan.scanproxy.endpoints.SetupDownstream
import eu.pretix.libpretixsync.db.Migrations as PSMigrations
import eu.pretix.libpretixsync.db.Models as PSModels
import eu.pretix.pretixscan.scanproxy.endpoints.SetupDownstreamInit
import eu.pretix.pretixscan.scanproxy.endpoints.SetupUpstream
import eu.pretix.pretixscan.scanproxy.serialization.JSONArraySerializer
import eu.pretix.pretixscan.scanproxy.serialization.JSONObjectSerializer
import io.javalin.Javalin
import io.javalin.apibuilder.ApiBuilder.path
import io.javalin.apibuilder.ApiBuilder.post
import io.javalin.plugin.json.JavalinJackson
import io.requery.Persistable
import io.requery.cache.EntityCacheBuilder
import io.requery.sql.ConfigurationBuilder
import io.requery.sql.EntityDataStore
import io.requery.sql.KotlinConfiguration
import io.requery.sql.KotlinEntityDataStore
import net.harawata.appdirs.AppDirsFactory
import org.json.JSONArray
import org.json.JSONObject
import org.postgresql.ds.PGSimpleDataSource
import org.slf4j.LoggerFactory
import java.sql.DriverManager


private fun makeSyncDataStore(): EntityDataStore<Persistable> {
    val LOG = LoggerFactory.getLogger(Server::class.java)

    // TODO: Support other databases
    val conn = DriverManager.getConnection(System.getProperty("pretixscan.database"))
    var exists = false
    val r = conn.metaData.getTables(null, null, "_version", arrayOf("TABLE"))
    while (r.next()) {
        if (r.getString("TABLE_NAME") == "_version") {
            exists = true;
            break;
        }
    }
    if (!exists) {
        LOG.info("Creating new database.")
    }

    val dataSource = PGSimpleDataSource()
    dataSource.setURL(System.getProperty("pretixscan.database"))
    val model = PSModels.DEFAULT
    PSMigrations.migrate(dataSource, !exists)
    val configuration = ConfigurationBuilder(dataSource, model)
        // .useDefaultLogging()
        .setEntityCache(
            EntityCacheBuilder(model)
                .useReferenceCache(false)
                .useSerializableCache(false)
                .build()
        )
        .build()

    return EntityDataStore<Persistable>(configuration)
}


private fun makeProxyDataStore(): KotlinEntityDataStore<Persistable> {
    val LOG = LoggerFactory.getLogger(Server::class.java)

    // TODO: Support other databases
    val conn = DriverManager.getConnection(System.getProperty("pretixscan.database"))
    var exists = false
    val r = conn.metaData.getTables(null, null, "_scanproxy_version", arrayOf("TABLE"))
    while (r.next()) {
        if (r.getString("TABLE_NAME") == "_scanproxy_version") {
            exists = true;
            break;
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
    return KotlinEntityDataStore(configuration = configuration)
}

object Server {
    val VERSION = "0.0.1"
    private val LOG = LoggerFactory.getLogger(Server::class.java)
    val syncData = makeSyncDataStore()
    val proxyData = makeProxyDataStore()

    val appDirs = AppDirsFactory.getInstance()!!
    val dataDir = appDirs.getUserDataDir("pretixscan", "1", "pretix")

    @JvmStatic
    fun main(args: Array<String>) {
        val app = Javalin.create { config ->
            config.requestLogger { ctx, executionTimeMs ->
                LOG.info("[${ctx.ip()}] ${ctx.method()} ${ctx.path()} returned ${ctx.status()}")
            }
        }.start(7000)

        // Map between org.json and Jackson
        val module = SimpleModule()
        module.addSerializer(JSONObject::class.java, JSONObjectSerializer())
        module.addSerializer(JSONArray::class.java, JSONArraySerializer())
        JavalinJackson.getObjectMapper().registerModule(module)

        app.routes {
            path("api/v1") {
                post("device/initialize", SetupDownstream)
            }
            path("proxyapi/v1") {
                post("configure", SetupUpstream)
                post("init", SetupDownstreamInit)
            }
        }

    }
}
