package eu.pretix.pretixscan.scanproxy

import eu.pretix.pretixscan.scanproxy.db.Migrations
import io.requery.Persistable
import io.requery.cache.EntityCacheBuilder
import io.requery.sql.ConfigurationBuilder
import io.requery.sql.EntityDataStore
import io.requery.sql.KotlinConfiguration
import io.requery.sql.KotlinEntityDataStore
import org.postgresql.ds.PGSimpleDataSource
import org.slf4j.LoggerFactory
import java.sql.DriverManager
import eu.pretix.libpretixsync.db.Migrations as PSMigrations
import eu.pretix.libpretixsync.Models as PSModels

internal fun makeSyncDataStore(): EntityDataStore<Persistable> {
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
        .setEntityCache(
            EntityCacheBuilder(model)
                .useReferenceCache(false)
                .useSerializableCache(false)
                .build()
        )
        .build()

    return EntityDataStore<Persistable>(configuration)
}


internal fun makeProxyDataStore(): KotlinEntityDataStore<Persistable> {
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
