package eu.pretix.pretixscan.scanproxy.db

import eu.pretix.libpretixsync.db.Migrations
import eu.pretix.pretixscan.scanproxy.Models
import io.requery.sql.SchemaModifier
import io.requery.sql.TableCreationMode
import java.sql.Connection
import java.sql.SQLException
import java.sql.Statement
import javax.sql.DataSource

object Migrations {
    private val model = Models.DEFAULT
    var CURRENT_VERSION = 6

    @Throws(SQLException::class)
    private fun createVersionTable(c: Connection, version: Int) {
        val s2 = c.createStatement()
        s2.execute("CREATE TABLE _scanproxy_version (version NUMERIC);")
        s2.close()
        val s3 = c.createStatement()
        s3.execute("INSERT INTO _scanproxy_version (version) VALUES ($version);")
        s3.close()
    }

    @Throws(SQLException::class)
    private fun updateVersionTable(c: Connection, version: Int) {
        val s2 = c.createStatement()
        s2.execute("DELETE FROM _scanproxy_version;")
        s2.close()
        val s3 = c.createStatement()
        s3.execute("INSERT INTO _scanproxy_version (version) VALUES ($version);")
        s3.close()
    }

    @Throws(SQLException::class)
    private fun execIgnore(c: Connection, sql: String, ignoreMatch: String) {
        val s1 = c.createStatement()
        try {
            s1.execute(sql)
        } catch (e: SQLException) {
            if (!e.message!!.contains(ignoreMatch)) {
                throw e
            }
        } finally {
            s1.close()
        }
    }

    @Throws(SQLException::class)
    fun migrate(dataSource: DataSource, dbIsNew: Boolean) {
        val c = dataSource.connection
        var db_version = 0

        if (dbIsNew) {
            create_notexists(dataSource)
            createVersionTable(c, CURRENT_VERSION)
            return
        }

        var s: Statement? = null
        try {
            s = c.createStatement()
            var rs = s.executeQuery("SELECT version FROM _scanproxy_version LIMIT 1")
            while (rs.next()) {
                db_version = rs.getInt("version")
            }
        } catch (e: SQLException) {
            db_version = 1;
            createVersionTable(c, db_version)
        } finally {
            if (s != null) s.close()
        }
        if (db_version < 3) {
            create_drop(dataSource)
            updateVersionTable(c, 3)
        }
        if (db_version < 4) {
            create_notexists(dataSource)
            updateVersionTable(c, 4)
        }
        if (db_version < 5) {
            execIgnore(
                c,
                "ALTER TABLE DownstreamDevice ADD added_datetime TEXT;",
                "duplicate column name"
            )
            updateVersionTable(c, 5)
        }
        if (db_version < 6) {
            execIgnore(
                c,
                "ALTER TABLE DownstreamDevice ADD name TEXT;",
                "duplicate column name"
            )
            updateVersionTable(c, 6)
        }

    }

    private fun create_drop(dataSource: DataSource) {
        SchemaModifier(dataSource, model).createTables(TableCreationMode.DROP_CREATE)
    }

    private fun create_notexists(dataSource: DataSource) {
        SchemaModifier(dataSource, model).createTables(TableCreationMode.CREATE_NOT_EXISTS)
    }
}
