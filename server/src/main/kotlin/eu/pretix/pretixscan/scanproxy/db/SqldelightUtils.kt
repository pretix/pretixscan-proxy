package eu.pretix.pretixscan.scanproxy.db

import app.cash.sqldelight.ColumnAdapter
import app.cash.sqldelight.db.SqlDriver
import eu.pretix.libpretixsync.sqldelight.*
import eu.pretix.pretixscan.scanproxy.sqldelight.proxy.ProxyDatabase
import eu.pretix.pretixscan.scanproxy.sqldelight.sync.SyncDatabase
import java.math.BigDecimal
import java.util.*

fun createSyncDatabase(
    driver: SqlDriver,
    dateAdapter: ColumnAdapter<Date, String>,
    bigDecimalAdapter: ColumnAdapter<BigDecimal, Double>,
): SyncDatabase {
    val db = SyncDatabase(
        driver = driver,
        CheckInAdapter = CheckIn.Adapter(
            datetimeAdapter = dateAdapter,
            local_annulledAdapter = dateAdapter,
        ),
        ClosingAdapter = Closing.Adapter(
            cash_countedAdapter = bigDecimalAdapter,
            datetimeAdapter = dateAdapter,
            payment_sumAdapter = bigDecimalAdapter,
            payment_sum_cashAdapter = bigDecimalAdapter,
        ),
        EventAdapter = Event.Adapter(
            date_fromAdapter = dateAdapter,
            date_toAdapter = dateAdapter,
        ),
        ReceiptLineAdapter = ReceiptLine.Adapter(
            cart_expiresAdapter = dateAdapter,
            createdAdapter = dateAdapter,
            custom_price_inputAdapter = bigDecimalAdapter,
            listed_priceAdapter = bigDecimalAdapter,
            priceAdapter = bigDecimalAdapter,
            price_after_voucherAdapter = bigDecimalAdapter,
            tax_rateAdapter = bigDecimalAdapter,
            tax_valueAdapter = bigDecimalAdapter,
            line_price_grossAdapter = bigDecimalAdapter,
        ),
        ReceiptAdapter = Receipt.Adapter(
            datetime_closedAdapter = dateAdapter,
            datetime_openedAdapter = dateAdapter,
        ),
        ReceiptPaymentAdapter = ReceiptPayment.Adapter(
            amountAdapter = bigDecimalAdapter,
        ),
        SubEventAdapter = SubEvent.Adapter(
            date_fromAdapter = dateAdapter,
            date_toAdapter = dateAdapter,
        ),
        QueuedCheckInAdapter = QueuedCheckIn.Adapter(
            datetimeAdapter = dateAdapter,
            annulledAdapter = dateAdapter,
        ),
        DiscountAdapter = Discount.Adapter(
            available_fromAdapter = dateAdapter,
            available_untilAdapter = dateAdapter,
        ),
        ReusableMediumAdapter = ReusableMedium.Adapter(expiresAdapter = dateAdapter)
    )

    return db
}

fun createProxyDatabase(driver: SqlDriver): ProxyDatabase {
    val db = ProxyDatabase(driver)
    return db
}
