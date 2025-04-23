package eu.pretix.pretixscan.scanproxy.db

import app.cash.sqldelight.TransacterImpl
import app.cash.sqldelight.driver.jdbc.JdbcPreparedStatement
import app.cash.sqldelight.driver.jdbc.asJdbcDriver
import eu.pretix.libpretixsync.sqldelight.BadgeLayout
import eu.pretix.libpretixsync.sqldelight.BadgeLayoutItem
import eu.pretix.libpretixsync.sqldelight.BlockedTicketSecret
import eu.pretix.libpretixsync.sqldelight.CachedPdfImage
import eu.pretix.libpretixsync.sqldelight.Cashier
import eu.pretix.libpretixsync.sqldelight.CheckIn
import eu.pretix.libpretixsync.sqldelight.CheckInList
import eu.pretix.libpretixsync.sqldelight.Closing
import eu.pretix.libpretixsync.sqldelight.Event
import eu.pretix.libpretixsync.sqldelight.Item
import eu.pretix.libpretixsync.sqldelight.ItemCategory
import eu.pretix.libpretixsync.sqldelight.MediumKeySet
import eu.pretix.libpretixsync.sqldelight.OrderPosition
import eu.pretix.libpretixsync.sqldelight.Orders
import eu.pretix.libpretixsync.sqldelight.PostgresIdAdapter
import eu.pretix.libpretixsync.sqldelight.PostgresJavaUtilDateAdapter
import eu.pretix.libpretixsync.sqldelight.PostgresLongBooleanAdapter
import eu.pretix.libpretixsync.sqldelight.Question
import eu.pretix.libpretixsync.sqldelight.QueuedCall
import eu.pretix.libpretixsync.sqldelight.QueuedCheckIn
import eu.pretix.libpretixsync.sqldelight.QueuedOrder
import eu.pretix.libpretixsync.sqldelight.Quota
import eu.pretix.libpretixsync.sqldelight.Receipt
import eu.pretix.libpretixsync.sqldelight.ReceiptLine
import eu.pretix.libpretixsync.sqldelight.ReceiptPayment
import eu.pretix.libpretixsync.sqldelight.ResourceSyncStatus
import eu.pretix.libpretixsync.sqldelight.ReusableMedium
import eu.pretix.libpretixsync.sqldelight.RevokedTicketSecret
import eu.pretix.libpretixsync.sqldelight.Settings
import eu.pretix.libpretixsync.sqldelight.SubEvent
import eu.pretix.libpretixsync.sqldelight.TaxRule
import eu.pretix.libpretixsync.sqldelight.TicketLayout
import eu.pretix.pretixscan.scanproxy.sqldelight.proxy.ProxyDatabase
import eu.pretix.pretixscan.scanproxy.sqldelight.sync.SyncDatabase
import org.postgresql.ds.PGSimpleDataSource
import org.slf4j.Logger
import java.math.BigDecimal
import java.sql.DriverManager

fun createSyncDatabase(url: String, LOG: Logger): SyncDatabase {
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
    val driver = dataSource.asJdbcDriver()

    if (!exists) {
        LOG.info("Creating new database.")

        val t = object : TransacterImpl(driver) {}
        t.transaction {
            SyncDatabase.Schema.create(driver)
        }
    }

    val dateAdapter = PostgresJavaUtilDateAdapter()
    val idAdapter = PostgresIdAdapter()
    val longBooleanAdapter = PostgresLongBooleanAdapter()

    val db = SyncDatabase(
        driver = driver,
        BadgeLayoutAdapter = BadgeLayout.Adapter(
            idAdapter = idAdapter,
        ),
        BadgeLayoutItemAdapter = BadgeLayoutItem.Adapter(
            idAdapter = idAdapter,
        ),
        BlockedTicketSecretAdapter = BlockedTicketSecret.Adapter(
            idAdapter = idAdapter,
        ),
        CachedPdfImageAdapter = CachedPdfImage.Adapter(
            idAdapter = idAdapter,
        ),
        CashierAdapter = Cashier.Adapter(
            idAdapter = idAdapter,
        ),
        CheckInAdapter = CheckIn.Adapter(
            idAdapter = idAdapter,
            datetimeAdapter = dateAdapter,
        ),
        CheckInListAdapter = CheckInList.Adapter(
            idAdapter = idAdapter,
        ),
        ClosingAdapter = Closing.Adapter(
            idAdapter = idAdapter,
            datetimeAdapter = dateAdapter,
        ),
        EventAdapter = Event.Adapter(
            idAdapter = idAdapter,
            date_fromAdapter = dateAdapter,
            date_toAdapter = dateAdapter,
        ),
        ItemAdapter = Item.Adapter(
            idAdapter = idAdapter,
        ),
        ItemCategoryAdapter = ItemCategory.Adapter(
            idAdapter = idAdapter,
        ),
        MediumKeySetAdapter = MediumKeySet.Adapter(
            idAdapter = idAdapter,
        ),
        OrderPositionAdapter = OrderPosition.Adapter(
            idAdapter = idAdapter,
        ),
        ordersAdapter = Orders.Adapter(
            idAdapter = idAdapter,
        ),
        QuestionAdapter = Question.Adapter(
            idAdapter = idAdapter,
        ),
        QueuedCallAdapter = QueuedCall.Adapter(
            idAdapter = idAdapter,
        ),
        QueuedCheckInAdapter = QueuedCheckIn.Adapter(
            idAdapter = idAdapter,
            datetimeAdapter = dateAdapter,
        ),
        QueuedOrderAdapter = QueuedOrder.Adapter(
            idAdapter = idAdapter,
        ),
        QuotaAdapter = Quota.Adapter(
            idAdapter = idAdapter,
            availableAdapter = longBooleanAdapter,
        ),
        ReceiptLineAdapter = ReceiptLine.Adapter(
            idAdapter = idAdapter,
            cart_expiresAdapter = dateAdapter,
            createdAdapter = dateAdapter,
        ),
        ReceiptAdapter = Receipt.Adapter(
            idAdapter = idAdapter,
            datetime_closedAdapter = dateAdapter,
            datetime_openedAdapter = dateAdapter,
        ),
        ReceiptPaymentAdapter = ReceiptPayment.Adapter(
            idAdapter = idAdapter,
        ),
        ResourceSyncStatusAdapter = ResourceSyncStatus.Adapter(
            idAdapter = idAdapter,
        ),
        ReusableMediumAdapter = ReusableMedium.Adapter(
            idAdapter = idAdapter,
        ),
        RevokedTicketSecretAdapter = RevokedTicketSecret.Adapter(
            idAdapter = idAdapter,
        ),
        SettingsAdapter = Settings.Adapter(
            idAdapter = idAdapter,
        ),
        SubEventAdapter = SubEvent.Adapter(
            idAdapter = idAdapter,
            date_fromAdapter = dateAdapter,
            date_toAdapter = dateAdapter,
        ),
        TaxRuleAdapter = TaxRule.Adapter(
            idAdapter = idAdapter,
        ),
        TicketLayoutAdapter = TicketLayout.Adapter(
            idAdapter = idAdapter,
        ),
    )

    val version = if (!exists) {
        db._versionQueries.insertVersion(BigDecimal.valueOf(SyncDatabase.Schema.version))
        SyncDatabase.Schema.version
    } else {
        db._versionQueries.selectVersion().executeAsOne().version!!.toLong()
    }

    if (version != SyncDatabase.Schema.version) {
        LOG.info("Migrating sync database from version $version to ${SyncDatabase.Schema.version}.")
        SyncDatabase.Schema.migrate(
            driver = driver,
            oldVersion = version,
            SyncDatabase.Schema.version,
        )
        db._versionQueries.transaction {
            db._versionQueries.deleteVersion()
            db._versionQueries.insertVersion(BigDecimal.valueOf(SyncDatabase.Schema.version))
        }
    }

    return db
}

fun createProxyDatabase(url: String, LOG: Logger): ProxyDatabase {
    val conn = DriverManager.getConnection(url)
    var exists = false
    val r = conn.metaData.getTables(null, null, "_scanproxy_version", arrayOf("TABLE"))
    while (r.next()) {
        if (r.getString("TABLE_NAME") == "_scanproxy_version") {
            exists = true
            break
        }
    }

    val dataSource = PGSimpleDataSource()
    dataSource.setURL(url)
    val driver = dataSource.asJdbcDriver()

    if (!exists) {
        LOG.info("Creating new proxy database.")
        val t = object : TransacterImpl(driver) {}

        t.transaction {
            ProxyDatabase.Schema.create(driver)
        }
    }

    val db = ProxyDatabase(driver)

    val version = if (!exists) {
        db._scanproxy_versionQueries.insertVersion(BigDecimal.valueOf(ProxyDatabase.Schema.version))
        ProxyDatabase.Schema.version
    } else {
        db._scanproxy_versionQueries.selectVersion().executeAsOne().version!!.toLong()
    }

    if (version != ProxyDatabase.Schema.version) {
        LOG.info("Migrating proxy database from version $version to ${ProxyDatabase.Schema.version}.")
        ProxyDatabase.Schema.migrate(
            driver = driver,
            oldVersion = version,
            ProxyDatabase.Schema.version,
        )
        db._scanproxy_versionQueries.transaction {
            db._scanproxy_versionQueries.deleteVersion()
            db._scanproxy_versionQueries.insertVersion(BigDecimal.valueOf(ProxyDatabase.Schema.version))
        }
    }

    return db
}
