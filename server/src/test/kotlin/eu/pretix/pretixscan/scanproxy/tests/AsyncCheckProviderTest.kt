package eu.pretix.pretixscan.scanproxy.tests

import eu.pretix.libpretixsync.check.AsyncCheckProvider
import eu.pretix.libpretixsync.check.CheckException
import eu.pretix.libpretixsync.check.TicketCheckProvider
import eu.pretix.libpretixsync.db.*
import eu.pretix.libpretixsync.sync.*
import eu.pretix.pretixscan.scanproxy.tests.db.BaseDatabaseTest
import eu.pretix.pretixscan.scanproxy.tests.test.FakeConfigStore
import eu.pretix.pretixscan.scanproxy.tests.test.FakeFileStorage
import eu.pretix.pretixscan.scanproxy.tests.test.FakePretixApi
import eu.pretix.pretixscan.scanproxy.tests.test.jsonResource
import org.joda.time.format.ISODateTimeFormat
import org.json.JSONException
import org.json.JSONObject
import org.junit.Before
import org.junit.Test

import java.util.ArrayList

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull

class AsyncCheckProviderTest : BaseDatabaseTest() {
    private var configStore: FakeConfigStore? = null
    private var fakeApi: FakePretixApi? = null
    private var item: Item? = null
    private var p: AsyncCheckProvider? = null

    @Before
    fun setUpFakes() {
        configStore = FakeConfigStore()
        fakeApi = FakePretixApi()
        p = AsyncCheckProvider(configStore!!, dataStore)

        EventSyncAdapter(dataStore, "demo", "demo", fakeApi, "", null).standaloneRefreshFromJSON(jsonResource("events/event1.json"))
        EventSyncAdapter(dataStore, "demo", "demo", fakeApi, "", null).standaloneRefreshFromJSON(jsonResource("events/event2.json"))
        ItemSyncAdapter(dataStore, FakeFileStorage(), "demo", fakeApi, "", null).standaloneRefreshFromJSON(jsonResource("items/item1.json"))
        ItemSyncAdapter(dataStore, FakeFileStorage(), "demo", fakeApi, "", null).standaloneRefreshFromJSON(jsonResource("items/item2.json"))
        ItemSyncAdapter(dataStore, FakeFileStorage(), "demo2", fakeApi, "", null).standaloneRefreshFromJSON(jsonResource("items/event2-item3.json"))
        CheckInListSyncAdapter(dataStore, FakeFileStorage(), "demo", fakeApi, "", null, 0).standaloneRefreshFromJSON(jsonResource("checkinlists/list1.json"))
        CheckInListSyncAdapter(dataStore, FakeFileStorage(), "demo", fakeApi, "", null, 0).standaloneRefreshFromJSON(jsonResource("checkinlists/list2.json"))
        CheckInListSyncAdapter(dataStore, FakeFileStorage(), "demo", fakeApi, "", null, 0).standaloneRefreshFromJSON(jsonResource("checkinlists/list3.json"))
        CheckInListSyncAdapter(dataStore, FakeFileStorage(), "demo", fakeApi, "", null, 0).standaloneRefreshFromJSON(jsonResource("checkinlists/list4.json"))
        CheckInListSyncAdapter(dataStore, FakeFileStorage(), "demo", fakeApi, "", null, 0).standaloneRefreshFromJSON(jsonResource("checkinlists/list5.json"))
        CheckInListSyncAdapter(dataStore, FakeFileStorage(), "demo", fakeApi, "", null, 0).standaloneRefreshFromJSON(jsonResource("checkinlists/list6.json"))
        CheckInListSyncAdapter(dataStore, FakeFileStorage(), "demo2", fakeApi, "", null, 0).standaloneRefreshFromJSON(jsonResource("checkinlists/event2-list7.json"))
        SubEventSyncAdapter(dataStore, "demo", "14", fakeApi, "", null).standaloneRefreshFromJSON(jsonResource("subevents/subevent1.json"))

        val osa = OrderSyncAdapter(dataStore, FakeFileStorage(), "demo", 0, true, false, fakeApi, "", null)
        osa.standaloneRefreshFromJSON(jsonResource("orders/order1.json"))
        osa.standaloneRefreshFromJSON(jsonResource("orders/order2.json"))
        osa.standaloneRefreshFromJSON(jsonResource("orders/order3.json"))
        osa.standaloneRefreshFromJSON(jsonResource("orders/order4.json"))
        osa.standaloneRefreshFromJSON(jsonResource("orders/order5.json"))
        osa.standaloneRefreshFromJSON(jsonResource("orders/order6.json"))
        osa.standaloneRefreshFromJSON(jsonResource("orders/order7.json"))
        osa.standaloneRefreshFromJSON(jsonResource("orders/order8.json"))
        osa.standaloneRefreshFromJSON(jsonResource("orders/order9.json"))
        val osa2 = OrderSyncAdapter(dataStore, FakeFileStorage(), "demo2", 0, true, false, fakeApi, "", null)
        osa2.standaloneRefreshFromJSON(jsonResource("orders/event2-order1.json"))
    }

    @Test
    fun testSimpleSuccess() {
        val r = p!!.check(mapOf("demo" to 1L), "kfndgffgyw4tdgcacx6bb3bgemq69cxj")
        assertEquals(TicketCheckProvider.CheckResult.Type.VALID, r.type)
        assertEquals("Regular ticket", r.ticket)
        assertEquals(null, r.variation)
        assertEquals("Casey Flores", r.attendee_name)
        assertEquals(true, r.isRequireAttention)

        val qciList = dataStore.select(QueuedCheckIn::class.java).get().toList()
        assertEquals(1, qciList.size.toLong())
        assertEquals("kfndgffgyw4tdgcacx6bb3bgemq69cxj", qciList[0].getSecret())
    }

    @Test
    fun testSimpleInvalid() {
        val r = p!!.check(mapOf("demo" to 1L), "abc")
        assertEquals(TicketCheckProvider.CheckResult.Type.INVALID, r.type)
    }

    @Test
    fun testSimpleCanceled() {
        val r = p!!.check(mapOf("demo" to 1L), "uqonmlRPMOpP9O0NUC0W4yB63R3lZgCt")
        assertEquals(TicketCheckProvider.CheckResult.Type.CANCELED, r.type)
    }

    @Test
    fun testSimpleUnpaid() {
        val r = p!!.check(mapOf("demo" to 1L), "h4t6w9ykuea4n5zaapy648y2dcfg8weq")
        assertEquals(TicketCheckProvider.CheckResult.Type.UNPAID, r.type)
        assertEquals("Regular ticket", r.ticket)
        assertEquals(null, r.variation)
        assertEquals("Emily Scott", r.attendee_name)
        assertEquals(true, r.isRequireAttention)
    }

    @Test
    fun testSimpleUnpaidAllowed() {
        var r = p!!.check(mapOf("demo" to 1L), "h4t6w9ykuea4n5zaapy648y2dcfg8weq")
        assertEquals(TicketCheckProvider.CheckResult.Type.UNPAID, r.type)
        assertEquals("Regular ticket", r.ticket)
        assertEquals(null, r.variation)
        assertEquals("Emily Scott", r.attendee_name)
        assertEquals(true, r.isRequireAttention)

        r = p!!.check(mapOf("demo" to 1L), "h4t6w9ykuea4n5zaapy648y2dcfg8weq", "barcode", null, true, false, TicketCheckProvider.CheckInType.ENTRY)
        assertEquals(TicketCheckProvider.CheckResult.Type.VALID, r.type)
        assertEquals("Regular ticket", r.ticket)
        assertEquals(null, r.variation)
        assertEquals("Emily Scott", r.attendee_name)
        assertEquals(true, r.isRequireAttention)
    }

    @Test
    fun testRequireApproval() {
        var r = p!!.check(mapOf("demo" to 1L), "jugeme335ggtc88tnt9pj853fu4wrf2s")
        assertEquals(TicketCheckProvider.CheckResult.Type.UNAPPROVED, r.type)
        assertEquals(false, r.isCheckinAllowed)
    }

    @Test
    fun testSimpleBlocked() {
        var r = p!!.check(mapOf("demo" to 1L), "TlPpWEHW6NUG2QkDYJlN")
        assertEquals(TicketCheckProvider.CheckResult.Type.BLOCKED, r.type)
        assertEquals("Merch", r.ticket)
    }

    @Test
    fun testSimpleValidUntil() {
        val p2 = AsyncCheckProvider(configStore!!, dataStore)

        p2.setNow(ISODateTimeFormat.dateTime().parseDateTime("2023-03-04T00:00:01.000Z"))
        var r = p2.check(mapOf("demo" to 1L), "dz4OBvVsTDSJ6T1nY1dD")
        assertEquals(TicketCheckProvider.CheckResult.Type.INVALID_TIME, r.type)

        p2.setNow(ISODateTimeFormat.dateTime().parseDateTime("2023-03-03T23:59:59.000Z"))
        r = p2.check(mapOf("demo" to 1L), "dz4OBvVsTDSJ6T1nY1dD")
        assertEquals(TicketCheckProvider.CheckResult.Type.VALID, r.type)
    }

    @Test
    fun testSimpleValidFrom() {
        val p2 = AsyncCheckProvider(configStore!!, dataStore)

        p2.setNow(ISODateTimeFormat.dateTime().parseDateTime("2023-03-03T23:59:59.000Z"))
        var r = p2.check(mapOf("demo" to 1L), "uG3H4hgRYEIrw4YNclyH")
        assertEquals(TicketCheckProvider.CheckResult.Type.INVALID_TIME, r.type)

        p2.setNow(ISODateTimeFormat.dateTime().parseDateTime("2023-03-04T00:00:01.000Z"))
        r = p2.check(mapOf("demo" to 1L), "uG3H4hgRYEIrw4YNclyH")
        assertEquals(TicketCheckProvider.CheckResult.Type.VALID, r.type)
    }

    @Test
    fun testSimpleUnpaidIgnoreWithoutIncludePending() {
        var r = p!!.check(mapOf("demo" to 2L), "h4t6w9ykuea4n5zaapy648y2dcfg8weq")
        assertEquals(TicketCheckProvider.CheckResult.Type.UNPAID, r.type)

        r = p!!.check(mapOf("demo" to 2L), "h4t6w9ykuea4n5zaapy648y2dcfg8weq", "barcode", null, true, false, TicketCheckProvider.CheckInType.ENTRY)
        assertEquals(TicketCheckProvider.CheckResult.Type.UNPAID, r.type)
    }

    @Test
    fun testSimpleUnpaidIgnoreWithoutIncludePendingButValidSetOnOrder() {
        var r = p!!.check(mapOf("demo" to 2L), "6BT7mkCwbVnIbrKZ5X8I")
        assertEquals(TicketCheckProvider.CheckResult.Type.VALID, r.type)
    }

    @Test
    fun testInvalidProduct() {
        val r = p!!.check(mapOf("demo" to 2L), "g2sc5ym78h5q5y5sbswses2b5h8pp6kt")
        assertEquals(TicketCheckProvider.CheckResult.Type.PRODUCT, r.type)
    }

    @Test
    fun testSimpleUnpaidAllowedIfSetOnOrder() {
        var r = p!!.check(mapOf("demo" to 1L), "6BT7mkCwbVnIbrKZ5X8I")
        assertEquals(TicketCheckProvider.CheckResult.Type.VALID, r.type)
        assertEquals("Regular ticket", r.ticket)
    }

    // TODO: invalid subevent

    @Test
    fun testSimpleRedeemed() {
        val r = p!!.check(mapOf("demo" to 1L), "g2sc5ym78h5q5y5sbswses2b5h8pp6kt")
        assertEquals(TicketCheckProvider.CheckResult.Type.USED, r.type)
    }

    @Test
    fun testSimpleRedeemedLocally() {
        var r = p!!.check(mapOf("demo" to 1L), "kfndgffgyw4tdgcacx6bb3bgemq69cxj")
        assertEquals(TicketCheckProvider.CheckResult.Type.VALID, r.type)
        assertEquals("Regular ticket", r.ticket)
        assertEquals(null, r.variation)
        assertEquals("Casey Flores", r.attendee_name)
        assertEquals(true, r.isRequireAttention)

        r = p!!.check(mapOf("demo" to 1L), "kfndgffgyw4tdgcacx6bb3bgemq69cxj")
        assertEquals(TicketCheckProvider.CheckResult.Type.USED, r.type)
        assertEquals("Regular ticket", r.ticket)
        assertEquals(null, r.variation)
        assertEquals("Casey Flores", r.attendee_name)
        assertEquals(true, r.isRequireAttention)
    }

    @Test
    fun testSimpleRedeemedOnOtherList() {
        var r = p!!.check(mapOf("demo" to 2L), "kfndgffgyw4tdgcacx6bb3bgemq69cxj")
        assertEquals(TicketCheckProvider.CheckResult.Type.VALID, r.type)
        r = p!!.check(mapOf("demo" to 1L), "kfndgffgyw4tdgcacx6bb3bgemq69cxj")
        assertEquals(TicketCheckProvider.CheckResult.Type.VALID, r.type)
    }

    @Test
    fun testAllowMultiEntry() {
        var r = p!!.check(mapOf("demo" to 2L), "kfndgffgyw4tdgcacx6bb3bgemq69cxj")
        assertEquals(TicketCheckProvider.CheckResult.Type.VALID, r.type)
        r = p!!.check(mapOf("demo" to 2L), "kfndgffgyw4tdgcacx6bb3bgemq69cxj")
        assertEquals(TicketCheckProvider.CheckResult.Type.VALID, r.type)
        assertEquals(dataStore.count(QueuedCheckIn::class.java).get().value(), 2)
        assertEquals(dataStore.count(CheckIn::class.java).join(OrderPosition::class.java).on(OrderPosition.ID.eq(CheckIn.POSITION_ID)).where(OrderPosition.SECRET.eq("kfndgffgyw4tdgcacx6bb3bgemq69cxj")).get().value(), 3)
    }

    @Test
    fun testAllowMultiExit() {
        var r = p!!.check(mapOf("demo" to 1L), "kfndgffgyw4tdgcacx6bb3bgemq69cxj")
        assertEquals(TicketCheckProvider.CheckResult.Type.VALID, r.type)
        r = p!!.check(mapOf("demo" to 1L), "kfndgffgyw4tdgcacx6bb3bgemq69cxj", "barcode", null, false, false, TicketCheckProvider.CheckInType.EXIT)
        assertEquals(TicketCheckProvider.CheckResult.Type.VALID, r.type)
        r = p!!.check(mapOf("demo" to 1L), "kfndgffgyw4tdgcacx6bb3bgemq69cxj", "barcode", null, false, false, TicketCheckProvider.CheckInType.EXIT)
        assertEquals(TicketCheckProvider.CheckResult.Type.VALID, r.type)
        assertEquals(dataStore.count(QueuedCheckIn::class.java).get().value(), 3)
        assertEquals(dataStore.select(QueuedCheckIn::class.java).get().toList().last().getType(), "exit")
    }

    @Test
    fun testAllowSingleEntryAfterExit() {
        var r = p!!.check(mapOf("demo" to 1L), "kfndgffgyw4tdgcacx6bb3bgemq69cxj")
        assertEquals(TicketCheckProvider.CheckResult.Type.VALID, r.type)
        Thread.sleep(1000)
        r = p!!.check(mapOf("demo" to 1L), "kfndgffgyw4tdgcacx6bb3bgemq69cxj", "barcode", null, false, false, TicketCheckProvider.CheckInType.EXIT)
        assertEquals(TicketCheckProvider.CheckResult.Type.VALID, r.type)
        r = p!!.check(mapOf("demo" to 1L), "kfndgffgyw4tdgcacx6bb3bgemq69cxj", "barcode", null, false, false, TicketCheckProvider.CheckInType.ENTRY)
        assertEquals(TicketCheckProvider.CheckResult.Type.VALID, r.type)
        r = p!!.check(mapOf("demo" to 1L), "kfndgffgyw4tdgcacx6bb3bgemq69cxj", "barcode", null, false, false, TicketCheckProvider.CheckInType.ENTRY)
        assertEquals(TicketCheckProvider.CheckResult.Type.USED, r.type)
        assertEquals(dataStore.count(QueuedCheckIn::class.java).get().value(), 3)
    }

    @Test
    fun testSingleEntryAfterExitForbidden() {
        var r = p!!.check(mapOf("demo" to 3L), "kfndgffgyw4tdgcacx6bb3bgemq69cxj")
        assertEquals(TicketCheckProvider.CheckResult.Type.VALID, r.type)
        r = p!!.check(mapOf("demo" to 3L), "kfndgffgyw4tdgcacx6bb3bgemq69cxj", "barcode", null, false, false, TicketCheckProvider.CheckInType.EXIT)
        assertEquals(TicketCheckProvider.CheckResult.Type.VALID, r.type)
        r = p!!.check(mapOf("demo" to 3L), "kfndgffgyw4tdgcacx6bb3bgemq69cxj", "barcode", null, false, false, TicketCheckProvider.CheckInType.ENTRY)
        assertEquals(TicketCheckProvider.CheckResult.Type.USED, r.type)
        assertEquals(dataStore.count(QueuedCheckIn::class.java).get().value(), 2)
    }

    @Test
    fun testAddonMatchDisabled() {
        val r = p!!.check(mapOf("demo" to 5L), "XwBltvZO50PKtygKtlIHgAFAxmhtDlzK")
        assertEquals(TicketCheckProvider.CheckResult.Type.PRODUCT, r.type)
        assertEquals(dataStore.count(QueuedCheckIn::class.java).get().value(), 0)
    }

    @Test
    fun testAddonMatchValid() {
        val r = p!!.check(mapOf("demo" to 3L), "XwBltvZO50PKtygKtlIHgAFAxmhtDlzK")
        assertEquals(TicketCheckProvider.CheckResult.Type.VALID, r.type)
        assertEquals(dataStore.count(QueuedCheckIn::class.java).get().value(), 1)
    }

    @Test
    fun testAddonMatchAmbiguous() {
        val r = p!!.check(mapOf("demo" to 4L), "XwBltvZO50PKtygKtlIHgAFAxmhtDlzK")
        assertEquals(TicketCheckProvider.CheckResult.Type.AMBIGUOUS, r.type)
        assertEquals(dataStore.count(QueuedCheckIn::class.java).get().value(), 0)
    }

    private fun setRuleOnList2(r: String) {
        val cl = dataStore.select(CheckInList::class.java).where(CheckInList.SERVER_ID.eq(2)).get().first()
        val j = cl.json
        j.put("rules", JSONObject(r))
        cl.setJson_data(j.toString())
        dataStore.update(cl)
    }

    @Test
    fun testRulesSimple() {
        setRuleOnList2("{\"and\": [false, true]}")
        var r = p!!.check(mapOf("demo" to 2L), "kfndgffgyw4tdgcacx6bb3bgemq69cxj")
        assertEquals(TicketCheckProvider.CheckResult.Type.RULES, r.type)
        r = p!!.check(mapOf("demo" to 2L), "kfndgffgyw4tdgcacx6bb3bgemq69cxj", "barcode", null, false, false, TicketCheckProvider.CheckInType.EXIT)
        assertEquals(TicketCheckProvider.CheckResult.Type.VALID, r.type)

        setRuleOnList2("{\"and\": [true, true]}")
        r = p!!.check(mapOf("demo" to 2L), "kfndgffgyw4tdgcacx6bb3bgemq69cxj")
        assertEquals(TicketCheckProvider.CheckResult.Type.VALID, r.type)
    }

    @Test
    fun testRulesProduct() {
        setRuleOnList2("{\n" +
                "        \"inList\": [\n" +
                "            {\"var\": \"product\"}, {\n" +
                "                \"objectList\": [\n" +
                "                    {\"lookup\": [\"product\", \"2\", \"Ticket\"]}\n" +
                "                ]\n" +
                "            }\n" +
                "        ]\n" +
                "    }")
        var r = p!!.check(mapOf("demo" to 2L), "kfndgffgyw4tdgcacx6bb3bgemq69cxj")
        assertEquals(TicketCheckProvider.CheckResult.Type.RULES, r.type)

        setRuleOnList2("{\n" +
                "        \"inList\": [\n" +
                "            {\"var\": \"product\"}, {\n" +
                "                \"objectList\": [\n" +
                "                    {\"lookup\": [\"product\", \"1\", \"Ticket\"]},\n" +
                "                    {\"lookup\": [\"product\", \"2\", \"Ticket\"]}\n" +
                "                ]\n" +
                "            }\n" +
                "        ]\n" +
                "    }")
        r = p!!.check(mapOf("demo" to 2L), "kfndgffgyw4tdgcacx6bb3bgemq69cxj")
        assertEquals(TicketCheckProvider.CheckResult.Type.VALID, r.type)
    }

    @Test
    fun testRulesVariation() {
        setRuleOnList2("{\n" +
                "        \"inList\": [\n" +
                "            {\"var\": \"variation\"}, {\n" +
                "                \"objectList\": [\n" +
                "                    {\"lookup\": [\"variation\", \"3\", \"Ticket\"]}\n" +
                "                ]\n" +
                "            }\n" +
                "        ]\n" +
                "    }")
        var r = p!!.check(mapOf("demo" to 2L), "kfndgffgyw4tdgcacx6bb3bgemq69cxj")
        assertEquals(TicketCheckProvider.CheckResult.Type.RULES, r.type)

        setRuleOnList2("{\n" +
                "        \"inList\": [\n" +
                "            {\"var\": \"variation\"}, {\n" +
                "                \"objectList\": [\n" +
                "                    {\"lookup\": [\"variation\", \"3\", \"Ticket\"]},\n" +
                "                    {\"lookup\": [\"variation\", \"2\", \"Ticket\"]}\n" +
                "                ]\n" +
                "            }\n" +
                "        ]\n" +
                "    }")
        r = p!!.check(mapOf("demo" to 2L), "kfndgffgyw4tdgcacx6bb3bgemq69cxj")
        assertEquals(TicketCheckProvider.CheckResult.Type.VALID, r.type)
    }

    @Test
    fun testRulesGate() {
        setRuleOnList2("{\n" +
                "        \"inList\": [\n" +
                "            {\"var\": \"gate\"}, {\n" +
                "                \"objectList\": [\n" +
                "                    {\"lookup\": [\"gate\", \"1\", \"Gate 1\"]},\n" +
                "                ]\n" +
                "            }\n" +
                "        ]\n" +
                "    }")
        configStore!!.deviceKnownGateID = 0
        var r = p!!.check(mapOf("demo" to 2L), "kfndgffgyw4tdgcacx6bb3bgemq69cxj")
        assertEquals(TicketCheckProvider.CheckResult.Type.RULES, r.type)

        configStore!!.deviceKnownGateID = 2
        r = p!!.check(mapOf("demo" to 2L), "kfndgffgyw4tdgcacx6bb3bgemq69cxj")
        assertEquals(TicketCheckProvider.CheckResult.Type.RULES, r.type)

        r = p!!.check(mapOf("demo" to 2L), "E4BibyTSylQOgeKjuMPiTDxi5HXPuTVsx1qCli3IL0143gj0EZXOB9iQInANxRFJTt4Pf9nXnHdB91Qk/RN0L5AIBABSxw2TKFnSUNUCKAEAPAQA")
        assertEquals(TicketCheckProvider.CheckResult.Type.RULES, r.type)

        setRuleOnList2("{\n" +
                "        \"inList\": [\n" +
                "            {\"var\": \"gate\"}, {\n" +
                "                \"objectList\": [\n" +
                "                    {\"lookup\": [\"gate\", \"1\", \"Gate 1\"]},\n" +
                "                    {\"lookup\": [\"gate\", \"2\", \"Gate 2\"]},\n" +
                "                ]\n" +
                "            }\n" +
                "        ]\n" +
                "    }")
        r = p!!.check(mapOf("demo" to 2L), "kfndgffgyw4tdgcacx6bb3bgemq69cxj")
        assertEquals(TicketCheckProvider.CheckResult.Type.VALID, r.type)
        r = p!!.check(mapOf("demo" to 2L), "E4BibyTSylQOgeKjuMPiTDxi5HXPuTVsx1qCli3IL0143gj0EZXOB9iQInANxRFJTt4Pf9nXnHdB91Qk/RN0L5AIBABSxw2TKFnSUNUCKAEAPAQA")
        assertEquals(TicketCheckProvider.CheckResult.Type.VALID, r.type)
    }

    @Test
    fun testRulesEntriesNumber() {
        setRuleOnList2("{\"<\": [{\"var\": \"entries_number\"}, 3]}")
        var r = p!!.check(mapOf("demo" to 2L), "kfndgffgyw4tdgcacx6bb3bgemq69cxj")
        assertEquals(TicketCheckProvider.CheckResult.Type.VALID, r.type)
        r = p!!.check(mapOf("demo" to 2L), "kfndgffgyw4tdgcacx6bb3bgemq69cxj")
        assertEquals(TicketCheckProvider.CheckResult.Type.VALID, r.type)
        r = p!!.check(mapOf("demo" to 2L), "kfndgffgyw4tdgcacx6bb3bgemq69cxj", "barcode", null, false, false, TicketCheckProvider.CheckInType.EXIT)
        assertEquals(TicketCheckProvider.CheckResult.Type.VALID, r.type)
        r = p!!.check(mapOf("demo" to 2L), "kfndgffgyw4tdgcacx6bb3bgemq69cxj")
        assertEquals(TicketCheckProvider.CheckResult.Type.VALID, r.type)
        r = p!!.check(mapOf("demo" to 2L), "kfndgffgyw4tdgcacx6bb3bgemq69cxj")
        assertEquals(TicketCheckProvider.CheckResult.Type.RULES, r.type)
    }

    @Test
    fun testRulesEntriesToday() {
        val p2 = AsyncCheckProvider(configStore!!, dataStore)

        p2.setNow(ISODateTimeFormat.dateTime().parseDateTime("2020-01-01T10:00:00.000Z"))
        setRuleOnList2("{\"<\": [{\"var\": \"entries_today\"}, 3]}")
        var r = p2.check(mapOf("demo" to 2L), "kfndgffgyw4tdgcacx6bb3bgemq69cxj")
        assertEquals(TicketCheckProvider.CheckResult.Type.VALID, r.type)
        r = p2.check(mapOf("demo" to 2L), "kfndgffgyw4tdgcacx6bb3bgemq69cxj")
        assertEquals(TicketCheckProvider.CheckResult.Type.VALID, r.type)
        r = p2.check(mapOf("demo" to 2L), "kfndgffgyw4tdgcacx6bb3bgemq69cxj", "barcode", null, false, false, TicketCheckProvider.CheckInType.EXIT)
        assertEquals(TicketCheckProvider.CheckResult.Type.VALID, r.type)
        r = p2.check(mapOf("demo" to 2L), "kfndgffgyw4tdgcacx6bb3bgemq69cxj")
        assertEquals(TicketCheckProvider.CheckResult.Type.VALID, r.type)
        r = p2.check(mapOf("demo" to 2L), "kfndgffgyw4tdgcacx6bb3bgemq69cxj")
        assertEquals(TicketCheckProvider.CheckResult.Type.RULES, r.type)

        p2.setNow(ISODateTimeFormat.dateTime().parseDateTime("2020-01-01T14:50:00.000Z"))
        r = p2.check(mapOf("demo" to 2L), "kfndgffgyw4tdgcacx6bb3bgemq69cxj")
        assertEquals(TicketCheckProvider.CheckResult.Type.RULES, r.type)

        p2.setNow(ISODateTimeFormat.dateTime().parseDateTime("2020-01-01T15:10:00.000Z"))
        r = p2.check(mapOf("demo" to 2L), "kfndgffgyw4tdgcacx6bb3bgemq69cxj")
        assertEquals(TicketCheckProvider.CheckResult.Type.VALID, r.type)
        r = p2.check(mapOf("demo" to 2L), "kfndgffgyw4tdgcacx6bb3bgemq69cxj")
        assertEquals(TicketCheckProvider.CheckResult.Type.VALID, r.type)
        r = p2.check(mapOf("demo" to 2L), "kfndgffgyw4tdgcacx6bb3bgemq69cxj", "barcode", null, false, false, TicketCheckProvider.CheckInType.EXIT)
        assertEquals(TicketCheckProvider.CheckResult.Type.VALID, r.type)
        r = p2.check(mapOf("demo" to 2L), "kfndgffgyw4tdgcacx6bb3bgemq69cxj")
        assertEquals(TicketCheckProvider.CheckResult.Type.VALID, r.type)
        r = p2.check(mapOf("demo" to 2L), "kfndgffgyw4tdgcacx6bb3bgemq69cxj")
        assertEquals(TicketCheckProvider.CheckResult.Type.RULES, r.type)
    }

    @Test
    fun testRulesEntriesDays() {
        val p2 = AsyncCheckProvider(configStore!!, dataStore)

        // Ticket is valid unlimited times, but only on two arbitrary days
        setRuleOnList2("{\"or\": [{\">\": [{\"var\": \"entries_today\"}, 0]}, {\"<\": [{\"var\": \"entries_days\"}, 2]}]}")

        p2.setNow(ISODateTimeFormat.dateTime().parseDateTime("2020-01-01T10:00:00.000Z"))
        var r = p2.check(mapOf("demo" to 2L), "kfndgffgyw4tdgcacx6bb3bgemq69cxj")
        assertEquals(TicketCheckProvider.CheckResult.Type.VALID, r.type)
        r = p2.check(mapOf("demo" to 2L), "kfndgffgyw4tdgcacx6bb3bgemq69cxj")
        assertEquals(TicketCheckProvider.CheckResult.Type.VALID, r.type)
        r = p2.check(mapOf("demo" to 2L), "kfndgffgyw4tdgcacx6bb3bgemq69cxj")
        assertEquals(TicketCheckProvider.CheckResult.Type.VALID, r.type)

        p2.setNow(ISODateTimeFormat.dateTime().parseDateTime("2020-01-03T10:00:00.000Z"))
        r = p2.check(mapOf("demo" to 2L), "kfndgffgyw4tdgcacx6bb3bgemq69cxj")
        assertEquals(TicketCheckProvider.CheckResult.Type.VALID, r.type)
        r = p2.check(mapOf("demo" to 2L), "kfndgffgyw4tdgcacx6bb3bgemq69cxj")
        assertEquals(TicketCheckProvider.CheckResult.Type.VALID, r.type)
        r = p2.check(mapOf("demo" to 2L), "kfndgffgyw4tdgcacx6bb3bgemq69cxj")
        assertEquals(TicketCheckProvider.CheckResult.Type.VALID, r.type)
        r = p2.check(mapOf("demo" to 2L), "kfndgffgyw4tdgcacx6bb3bgemq69cxj")
        assertEquals(TicketCheckProvider.CheckResult.Type.VALID, r.type)

        p2.setNow(ISODateTimeFormat.dateTime().parseDateTime("2020-01-03T14:50:00.000Z"))
        r = p2.check(mapOf("demo" to 2L), "kfndgffgyw4tdgcacx6bb3bgemq69cxj")
        assertEquals(TicketCheckProvider.CheckResult.Type.VALID, r.type)

        p2.setNow(ISODateTimeFormat.dateTime().parseDateTime("2020-01-03T15:10:00.000Z"))
        r = p2.check(mapOf("demo" to 2L), "kfndgffgyw4tdgcacx6bb3bgemq69cxj")
        assertEquals(TicketCheckProvider.CheckResult.Type.RULES, r.type)
        r = p2.check(mapOf("demo" to 2L), "kfndgffgyw4tdgcacx6bb3bgemq69cxj")
        assertEquals(TicketCheckProvider.CheckResult.Type.RULES, r.type)
    }

    @Test
    fun testRulesEntriesSince() {
        val p2 = AsyncCheckProvider(configStore!!, dataStore)

        // Ticket is valid once before X and once after X
        setRuleOnList2("{\n" +
                "        \"or\": [\n" +
                "            {\"<=\": [{\"var\": \"entries_number\"}, 0]},\n" +
                "            {\"and\": [\n" +
                "                {\"isAfter\": [{\"var\": \"now\"}, {\"buildTime\": [\"custom\", \"2020-01-01T23:00:00.000+01:00\"]}, 0]},\n" +
                "                {\"<=\": [{\"entries_since\": [{\"buildTime\": [\"custom\", \"2020-01-01T23:00:00.000+01:00\"]}]}, 0]},\n" +
                "            ]},\n" +
                "        ],\n" +
                "    }")

        p2.setNow(ISODateTimeFormat.dateTime().parseDateTime("2020-01-01T21:00:00.000Z"))

        var r = p2.check(mapOf("demo" to 2L), "kfndgffgyw4tdgcacx6bb3bgemq69cxj")
        assertEquals(TicketCheckProvider.CheckResult.Type.VALID, r.type)

        r = p2.check(mapOf("demo" to 2L), "kfndgffgyw4tdgcacx6bb3bgemq69cxj")
        assertEquals(TicketCheckProvider.CheckResult.Type.RULES, r.type)

        p2.setNow(ISODateTimeFormat.dateTime().parseDateTime("2020-01-01T22:10:00.000Z"))

        r = p2.check(mapOf("demo" to 2L), "kfndgffgyw4tdgcacx6bb3bgemq69cxj")
        assertEquals(TicketCheckProvider.CheckResult.Type.VALID, r.type)

        r = p2.check(mapOf("demo" to 2L), "kfndgffgyw4tdgcacx6bb3bgemq69cxj")
        assertEquals(TicketCheckProvider.CheckResult.Type.RULES, r.type)
    }

    @Test
    fun testRulesEntriesSinceTimeOfDay() {
        val p2 = AsyncCheckProvider(configStore!!, dataStore)

        // Ticket is valid once before X and once after X
        setRuleOnList2("{\n" +
                "        \"or\": [\n" +
                "            {\"<=\": [{\"var\": \"entries_today\"}, 0]},\n" +
                "            {\"and\": [\n" +
                "                {\"isAfter\": [{\"var\": \"now\"}, {\"buildTime\": [\"customtime\", \"23:00:00\"]}, 0]},\n" +
                "                {\"<=\": [{\"entries_since\": [{\"buildTime\": [\"customtime\", \"23:00:00\"]}]}, 0]},\n" +
                "            ]},\n" +
                "        ],\n" +
                "    }")

        val times = listOf(
            "2020-01-01T22:00:00.000+09:00",
            "2020-01-01T23:01:00.000+09:00",
            "2020-01-02T22:00:00.000+09:00",
            "2020-01-02T23:01:00.000+09:00"
        )

        for (t in times) {
            p2.setNow(ISODateTimeFormat.dateTime().parseDateTime(t))

            var r = p2.check(mapOf("demo" to 2L), "kfndgffgyw4tdgcacx6bb3bgemq69cxj")
            assertEquals(TicketCheckProvider.CheckResult.Type.VALID, r.type)

            r = p2.check(mapOf("demo" to 2L), "kfndgffgyw4tdgcacx6bb3bgemq69cxj")
            assertEquals(TicketCheckProvider.CheckResult.Type.RULES, r.type)
        }
    }

    @Test
    fun testRulesEntriesBefore() {
        val p2 = AsyncCheckProvider(configStore!!, dataStore)

        // Ticket is valid after 23:00 only if people already showed up before
        setRuleOnList2("{\n" +
                "        \"or\": [\n" +
                "            {\"isBefore\": [{\"var\": \"now\"}, {\"buildTime\": [\"custom\", \"2020-01-01T23:00:00.000+01:00\"]}, 0]},\n" +
                "            {\"and\": [\n" +
                "                {\"isAfter\": [{\"var\": \"now\"}, {\"buildTime\": [\"custom\", \"2020-01-01T23:00:00.000+01:00\"]}, 0]},\n" +
                "                {\">=\": [{\"entries_before\": [{\"buildTime\": [\"custom\", \"2020-01-01T23:00:00.000+01:00\"]}]}, 1]},\n" +
                "            ]},\n" +
                "        ],\n" +
                "    }")

        p2.setNow(ISODateTimeFormat.dateTime().parseDateTime("2020-01-01T21:00:00.000Z"))

        var r = p2.check(mapOf("demo" to 2L), "kfndgffgyw4tdgcacx6bb3bgemq69cxj")
        assertEquals(TicketCheckProvider.CheckResult.Type.VALID, r.type)

        p2.setNow(ISODateTimeFormat.dateTime().parseDateTime("2020-01-01T22:10:00.000Z"))

        r = p2.check(mapOf("demo" to 2L), "kfndgffgyw4tdgcacx6bb3bgemq69cxj")
        assertEquals(TicketCheckProvider.CheckResult.Type.VALID, r.type)

        dataStore.delete(CheckIn::class.java).get().value()

        r = p2.check(mapOf("demo" to 2L), "kfndgffgyw4tdgcacx6bb3bgemq69cxj")
        assertEquals(TicketCheckProvider.CheckResult.Type.RULES, r.type)
    }

    @Test
    fun testRulesEntriesDaysSince() {
        val p2 = AsyncCheckProvider(configStore!!, dataStore)

        // Ticket is valid once before X and on one day after X
        setRuleOnList2("{" +
                "        \"or\": [\n" +
                "            {\"<=\": [{\"var\": \"entries_number\"}, 0]},\n" +
                "            {\"and\": [\n" +
                "                {\"isAfter\": [{\"var\": \"now\"}, {\"buildTime\": [\"custom\", \"2020-01-01T23:00:00.000+01:00\"]}, 0]},\n" +
                "                {\"or\": [\n" +
                "                    {\">\": [{\"var\": \"entries_today\"}, 0]},\n" +
                "                    {\"<=\": [{\"entries_days_since\": [{\"buildTime\": [\"custom\", \"2020-01-01T23:00:00.000+01:00\"]}]}, 0]},\n" +
                "                ]}\n" +
                "            ]},\n" +
                "        ],\n" +
                "    }")

        p2.setNow(ISODateTimeFormat.dateTime().parseDateTime("2020-01-01T21:00:00.000Z"))

        var r = p2.check(mapOf("demo" to 2L), "kfndgffgyw4tdgcacx6bb3bgemq69cxj")
        assertEquals(TicketCheckProvider.CheckResult.Type.VALID, r.type)

        r = p2.check(mapOf("demo" to 2L), "kfndgffgyw4tdgcacx6bb3bgemq69cxj")
        assertEquals(TicketCheckProvider.CheckResult.Type.RULES, r.type)

        p2.setNow(ISODateTimeFormat.dateTime().parseDateTime("2020-01-02T22:10:00.000Z"))

        r = p2.check(mapOf("demo" to 2L), "kfndgffgyw4tdgcacx6bb3bgemq69cxj")
        assertEquals(TicketCheckProvider.CheckResult.Type.VALID, r.type)

        r = p2.check(mapOf("demo" to 2L), "kfndgffgyw4tdgcacx6bb3bgemq69cxj")
        assertEquals(TicketCheckProvider.CheckResult.Type.VALID, r.type)

        p2.setNow(ISODateTimeFormat.dateTime().parseDateTime("2020-01-03T22:10:00.000Z"))

        r = p2.check(mapOf("demo" to 2L), "kfndgffgyw4tdgcacx6bb3bgemq69cxj")
        assertEquals(TicketCheckProvider.CheckResult.Type.RULES, r.type)
    }

    @Test
    fun testRulesEntriesDaysBefore() {
        val p2 = AsyncCheckProvider(configStore!!, dataStore)

        // Ticket is valid after 23:00 only if people already showed up on two days before
        setRuleOnList2("{" +
                "        \"or\": [\n" +
                "            {\"isBefore\": [{\"var\": \"now\"}, {\"buildTime\": [\"custom\", \"2020-01-01T23:00:00.000+01:00\"]}, 0]},\n" +
                "            {\"and\": [\n" +
                "                {\"isAfter\": [{\"var\": \"now\"}, {\"buildTime\": [\"custom\", \"2020-01-01T23:00:00.000+01:00\"]}, 0]},\n" +
                "                {\">=\": [{\"entries_days_before\": [{\"buildTime\": [\"custom\", \"2020-01-01T23:00:00.000+01:00\"]}]}, 2]},\n" +
                "            ]},\n" +
                "        ],\n" +
                "    }")

        p2.setNow(ISODateTimeFormat.dateTime().parseDateTime("2019-12-30T21:00:00.000Z"))

        var r = p2.check(mapOf("demo" to 2L), "kfndgffgyw4tdgcacx6bb3bgemq69cxj")
        assertEquals(TicketCheckProvider.CheckResult.Type.VALID, r.type)

        p2.setNow(ISODateTimeFormat.dateTime().parseDateTime("2020-01-02T22:10:00.000Z"))

        r = p2.check(mapOf("demo" to 2L), "kfndgffgyw4tdgcacx6bb3bgemq69cxj")
        assertEquals(TicketCheckProvider.CheckResult.Type.RULES, r.type)

        p2.setNow(ISODateTimeFormat.dateTime().parseDateTime("2019-12-31T21:00:00.000Z"))

        r = p2.check(mapOf("demo" to 2L), "kfndgffgyw4tdgcacx6bb3bgemq69cxj")
        assertEquals(TicketCheckProvider.CheckResult.Type.VALID, r.type)

        p2.setNow(ISODateTimeFormat.dateTime().parseDateTime("2020-01-02T22:10:00.000Z"))

        r = p2.check(mapOf("demo" to 2L), "kfndgffgyw4tdgcacx6bb3bgemq69cxj")
        assertEquals(TicketCheckProvider.CheckResult.Type.VALID, r.type)
    }

    @Test
    fun testRulesMinutesSinceLastEntry() {
        val p2 = AsyncCheckProvider(configStore!!, dataStore)
        setRuleOnList2("{\"or\": [{\"<=\": [{\"var\": \"minutes_since_last_entry\"}, -1]}, {\">\": [{\"var\": \"minutes_since_last_entry\"}, 180]}]}")

        p2.setNow(ISODateTimeFormat.dateTime().parseDateTime("2020-01-01T10:00:00.000Z"))
        var r = p2.check(mapOf("demo" to 2L), "kfndgffgyw4tdgcacx6bb3bgemq69cxj")
        assertEquals(TicketCheckProvider.CheckResult.Type.VALID, r.type)

        p2.setNow(ISODateTimeFormat.dateTime().parseDateTime("2020-01-01T12:55:00.000Z"))
        r = p2.check(mapOf("demo" to 2L), "kfndgffgyw4tdgcacx6bb3bgemq69cxj")
        assertEquals(TicketCheckProvider.CheckResult.Type.RULES, r.type)

        p2.setNow(ISODateTimeFormat.dateTime().parseDateTime("2020-01-01T13:01:00.000Z"))
        r = p2.check(mapOf("demo" to 2L), "kfndgffgyw4tdgcacx6bb3bgemq69cxj")
        assertEquals(TicketCheckProvider.CheckResult.Type.VALID, r.type)

        p2.setNow(ISODateTimeFormat.dateTime().parseDateTime("2020-01-01T15:55:00.000Z"))
        r = p2.check(mapOf("demo" to 2L), "kfndgffgyw4tdgcacx6bb3bgemq69cxj")
        assertEquals(TicketCheckProvider.CheckResult.Type.RULES, r.type)

        p2.setNow(ISODateTimeFormat.dateTime().parseDateTime("2020-01-01T16:02:00.000Z"))
        r = p2.check(mapOf("demo" to 2L), "kfndgffgyw4tdgcacx6bb3bgemq69cxj")
        assertEquals(TicketCheckProvider.CheckResult.Type.VALID, r.type)
    }

    @Test
    fun testRulesMinutesSinceFirstEntry() {
        val p2 = AsyncCheckProvider(configStore!!, dataStore)
        setRuleOnList2("{\"or\": [{\"<=\": [{\"var\": \"minutes_since_first_entry\"}, -1]}, {\"<\": [{\"var\": \"minutes_since_first_entry\"}, 180]}]}")

        p2.setNow(ISODateTimeFormat.dateTime().parseDateTime("2020-01-01T10:00:00.000Z"))
        var r = p2.check(mapOf("demo" to 2L), "kfndgffgyw4tdgcacx6bb3bgemq69cxj")
        assertEquals(TicketCheckProvider.CheckResult.Type.VALID, r.type)

        p2.setNow(ISODateTimeFormat.dateTime().parseDateTime("2020-01-01T12:55:00.000Z"))
        r = p2.check(mapOf("demo" to 2L), "kfndgffgyw4tdgcacx6bb3bgemq69cxj")
        assertEquals(TicketCheckProvider.CheckResult.Type.VALID, r.type)

        p2.setNow(ISODateTimeFormat.dateTime().parseDateTime("2020-01-01T13:01:00.000Z"))
        r = p2.check(mapOf("demo" to 2L), "kfndgffgyw4tdgcacx6bb3bgemq69cxj")
        assertEquals(TicketCheckProvider.CheckResult.Type.RULES, r.type)
    }

    @Test
    fun testRulesIsAfterTolerance() {
        val p2 = AsyncCheckProvider(configStore!!, dataStore)

        // Ticket is valid unlimited times, but only on two arbitrary days
        setRuleOnList2("{\"isAfter\": [{\"var\": \"now\"}, {\"buildTime\": [\"date_admission\"]}, 10]}")

        p2.setNow(ISODateTimeFormat.dateTime().parseDateTime("2020-01-01T08:45:00.000Z"))
        var r = p2.check(mapOf("demo" to 2L), "kfndgffgyw4tdgcacx6bb3bgemq69cxj")
        assertEquals(TicketCheckProvider.CheckResult.Type.RULES, r.type)

        p2.setNow(ISODateTimeFormat.dateTime().parseDateTime("2020-01-01T08:51:00.000Z"))
        r = p2.check(mapOf("demo" to 2L), "kfndgffgyw4tdgcacx6bb3bgemq69cxj")
        assertEquals(TicketCheckProvider.CheckResult.Type.VALID, r.type)

        p2.setNow(ISODateTimeFormat.dateTime().parseDateTime("2020-01-01T09:10:00.000Z"))
        r = p2.check(mapOf("demo" to 2L), "kfndgffgyw4tdgcacx6bb3bgemq69cxj")
        assertEquals(TicketCheckProvider.CheckResult.Type.VALID, r.type)
    }

    @Test
    fun testRulesIsAfterSubevent() {
        val p2 = AsyncCheckProvider(configStore!!, dataStore)

        // Ticket is valid unlimited times, but only on two arbitrary days
        setRuleOnList2("{\"isAfter\": [{\"var\": \"now\"}, {\"buildTime\": [\"date_admission\"]}, 10]}")

        p2.setNow(ISODateTimeFormat.dateTime().parseDateTime("2020-02-01T08:45:00.000Z"))
        var r = p2.check(mapOf("demo" to 2L), "VQwFXDZWhoXDuXBvKxWqq76kVtLlFWaY")
        assertEquals(TicketCheckProvider.CheckResult.Type.RULES, r.type)

        p2.setNow(ISODateTimeFormat.dateTime().parseDateTime("2020-02-01T08:51:00.000Z"))
        r = p2.check(mapOf("demo" to 2L), "VQwFXDZWhoXDuXBvKxWqq76kVtLlFWaY")
        assertEquals(TicketCheckProvider.CheckResult.Type.VALID, r.type)

        p2.setNow(ISODateTimeFormat.dateTime().parseDateTime("2020-02-01T09:10:00.000Z"))
        r = p2.check(mapOf("demo" to 2L), "VQwFXDZWhoXDuXBvKxWqq76kVtLlFWaY")
        assertEquals(TicketCheckProvider.CheckResult.Type.VALID, r.type)
    }

    @Test
    fun testRulesIsAfterNoTolerance() {
        val p2 = AsyncCheckProvider(configStore!!, dataStore)

        // Ticket is valid unlimited times, but only on two arbitrary days
        setRuleOnList2("{\"isAfter\": [{\"var\": \"now\"}, {\"buildTime\": [\"date_admission\"]}, null]}")

        p2.setNow(ISODateTimeFormat.dateTime().parseDateTime("2020-01-01T08:51:00.000Z"))
        var r = p2.check(mapOf("demo" to 2L), "kfndgffgyw4tdgcacx6bb3bgemq69cxj")
        assertEquals(TicketCheckProvider.CheckResult.Type.RULES, r.type)

        p2.setNow(ISODateTimeFormat.dateTime().parseDateTime("2020-01-01T09:10:00.000Z"))
        r = p2.check(mapOf("demo" to 2L), "kfndgffgyw4tdgcacx6bb3bgemq69cxj")
        assertEquals(TicketCheckProvider.CheckResult.Type.VALID, r.type)
    }

    @Test
    fun testRulesIsBeforeTolerance() {
        val p2 = AsyncCheckProvider(configStore!!, dataStore)

        // Ticket is valid unlimited times, but only on two arbitrary days
        setRuleOnList2("{\"isBefore\": [{\"var\": \"now\"}, {\"buildTime\": [\"date_to\"]}, 10]}")

        p2.setNow(ISODateTimeFormat.dateTime().parseDateTime("2020-01-01T14:05:00.000Z"))
        var r = p2.check(mapOf("demo" to 2L), "kfndgffgyw4tdgcacx6bb3bgemq69cxj")
        assertEquals(TicketCheckProvider.CheckResult.Type.VALID, r.type)

        p2.setNow(ISODateTimeFormat.dateTime().parseDateTime("2020-01-01T14:15:00.000Z"))
        r = p2.check(mapOf("demo" to 2L), "kfndgffgyw4tdgcacx6bb3bgemq69cxj")
        assertEquals(TicketCheckProvider.CheckResult.Type.RULES, r.type)
    }

    @Test
    fun testRulesIsBeforeNoTolerance() {
        val p2 = AsyncCheckProvider(configStore!!, dataStore)

        // Ticket is valid unlimited times, but only on two arbitrary days
        setRuleOnList2("{\"isBefore\": [{\"var\": \"now\"}, {\"buildTime\": [\"date_to\"]}]}")

        p2.setNow(ISODateTimeFormat.dateTime().parseDateTime("2020-01-01T13:55:00.000Z"))
        var r = p2.check(mapOf("demo" to 2L), "kfndgffgyw4tdgcacx6bb3bgemq69cxj")
        assertEquals(TicketCheckProvider.CheckResult.Type.VALID, r.type)

        p2.setNow(ISODateTimeFormat.dateTime().parseDateTime("2020-01-01T14:05:00.000Z"))
        r = p2.check(mapOf("demo" to 2L), "kfndgffgyw4tdgcacx6bb3bgemq69cxj")
        assertEquals(TicketCheckProvider.CheckResult.Type.RULES, r.type)
    }

    @Test
    fun testRulesIsAfterCustomDateTime() {
        val p2 = AsyncCheckProvider(configStore!!, dataStore)

        // Ticket is valid unlimited times, but only on two arbitrary days
        setRuleOnList2("{\"isAfter\": [{\"var\": \"now\"}, {\"buildTime\": [\"custom\", \"2020-01-01T22:00:00.000Z\"]}]}")

        p2.setNow(ISODateTimeFormat.dateTime().parseDateTime("2020-01-01T21:51:00.000Z"))
        var r = p2.check(mapOf("demo" to 2L), "kfndgffgyw4tdgcacx6bb3bgemq69cxj")
        assertEquals(TicketCheckProvider.CheckResult.Type.RULES, r.type)

        p2.setNow(ISODateTimeFormat.dateTime().parseDateTime("2020-01-01T22:01:00.000Z"))
        r = p2.check(mapOf("demo" to 2L), "kfndgffgyw4tdgcacx6bb3bgemq69cxj")
        assertEquals(TicketCheckProvider.CheckResult.Type.VALID, r.type)
    }

    @Test
    fun testRulesIsAfterCustomTime() {
        val p2 = AsyncCheckProvider(configStore!!, dataStore)

        // Ticket is valid unlimited times, but only on two arbitrary days
        setRuleOnList2("{\"isAfter\": [{\"var\": \"now\"}, {\"buildTime\": [\"customtime\", \"14:00\"]}]}")

        p2.setNow(ISODateTimeFormat.dateTime().parseDateTime("2020-01-01T04:50:00.000Z"))
        var r = p2.check(mapOf("demo" to 2L), "kfndgffgyw4tdgcacx6bb3bgemq69cxj")
        assertEquals(TicketCheckProvider.CheckResult.Type.RULES, r.type)

        p2.setNow(ISODateTimeFormat.dateTime().parseDateTime("2020-01-01T05:01:00.000Z"))
        r = p2.check(mapOf("demo" to 2L), "kfndgffgyw4tdgcacx6bb3bgemq69cxj")
        assertEquals(TicketCheckProvider.CheckResult.Type.VALID, r.type)
    }

    @Test
    fun testRulesCompareIsoweekday() {
        val p2 = AsyncCheckProvider(configStore!!, dataStore)

        // Ticket is valid unlimited times, but only on two arbitrary days
        setRuleOnList2("{\">=\": [{\"var\": \"now_isoweekday\"}, 6]}")

        p2.setNow(ISODateTimeFormat.dateTime().parseDateTime("2022-04-06T21:55:00.000Z"))
        var r = p2.check(mapOf("demo" to 2L), "kfndgffgyw4tdgcacx6bb3bgemq69cxj")
        assertEquals(TicketCheckProvider.CheckResult.Type.RULES, r.type)

        p2.setNow(ISODateTimeFormat.dateTime().parseDateTime("2022-04-09T22:05:00.000Z"))
        r = p2.check(mapOf("demo" to 2L), "kfndgffgyw4tdgcacx6bb3bgemq69cxj")
        assertEquals(TicketCheckProvider.CheckResult.Type.VALID, r.type)
    }

    @Test
    fun testQuestionsForOtherItem() {
        QuestionSyncAdapter(dataStore, FakeFileStorage(), "demo", fakeApi, "", null).standaloneRefreshFromJSON(jsonResource("questions/question1.json"))

        val r = p!!.check(mapOf("demo" to 1L), "fem3hggggag8q38qkx35c2panqr5xjq8")
        assertEquals(TicketCheckProvider.CheckResult.Type.VALID, r.type)
    }

    @Test
    fun testQuestionNotDuringCheckin() {
        QuestionSyncAdapter(dataStore, FakeFileStorage(), "demo", fakeApi, "", null).standaloneRefreshFromJSON(jsonResource("questions/question3.json"))

        val r = p!!.check(mapOf("demo" to 1L), "fem3hggggag8q38qkx35c2panqr5xjq8")
        assertEquals(TicketCheckProvider.CheckResult.Type.VALID, r.type)
    }

    @Test
    fun testQuestionsFilled() {
        QuestionSyncAdapter(dataStore, FakeFileStorage(), "demo", fakeApi, "", null).standaloneRefreshFromJSON(jsonResource("questions/question1.json"))

        val r = p!!.check(mapOf("demo" to 1L), "kc855mh2e4cp6ye7xvpg3b4ye7n7xyma")
        assertEquals(TicketCheckProvider.CheckResult.Type.VALID, r.type)
    }

    @Test
    fun testQuestionsIgnored() {
        QuestionSyncAdapter(dataStore, FakeFileStorage(), "demo", fakeApi, "", null).standaloneRefreshFromJSON(jsonResource("questions/question1.json"))

        var r = p!!.check(mapOf("demo" to 1L), "kfndgffgyw4tdgcacx6bb3bgemq69cxj", "barcode", ArrayList(), false, true, TicketCheckProvider.CheckInType.ENTRY, allowQuestions = false)
        assertEquals(TicketCheckProvider.CheckResult.Type.VALID, r.type)
    }

    @Test
    fun testQuestionsRequired() {
        QuestionSyncAdapter(dataStore, FakeFileStorage(), "demo", fakeApi, "", null).standaloneRefreshFromJSON(jsonResource("questions/question1.json"))

        var r = p!!.check(mapOf("demo" to 1L), "kfndgffgyw4tdgcacx6bb3bgemq69cxj")
        assertEquals(TicketCheckProvider.CheckResult.Type.ANSWERS_REQUIRED, r.type)
        assertEquals(1, r.requiredAnswers?.size)
        val ra = r.requiredAnswers!![0]
        assertEquals(1, ra.question.getServer_id())

        val answers = ArrayList<Answer>()
        answers.add(Answer(ra.question, "True"))

        r = p!!.check(mapOf("demo" to 1L), "kfndgffgyw4tdgcacx6bb3bgemq69cxj", "barcode", answers, false, false, TicketCheckProvider.CheckInType.ENTRY)
        assertEquals(TicketCheckProvider.CheckResult.Type.VALID, r.type)

        val qciList = dataStore.select(QueuedCheckIn::class.java).get().toList()
        assertEquals(1, qciList.size.toLong())
        assertEquals("kfndgffgyw4tdgcacx6bb3bgemq69cxj", qciList[0].getSecret())
        assertEquals("[{\"answer\":\"True\",\"question\":1}]", qciList[0].getAnswers())
    }

    @Test
    fun testQuestionsInvalidInput() {
        QuestionSyncAdapter(dataStore, FakeFileStorage(), "demo", fakeApi, "", null).standaloneRefreshFromJSON(jsonResource("questions/question2.json"))

        var r = p!!.check(mapOf("demo" to 1L), "kfndgffgyw4tdgcacx6bb3bgemq69cxj")
        assertEquals(TicketCheckProvider.CheckResult.Type.ANSWERS_REQUIRED, r.type)
        assertEquals(1, r.requiredAnswers?.size)
        val ra = r.requiredAnswers!![0]

        val answers = ArrayList<Answer>()
        answers.add(Answer(ra.question, "True"))

        r = p!!.check(mapOf("demo" to 1L), "kfndgffgyw4tdgcacx6bb3bgemq69cxj", "barcode", answers, false, false, TicketCheckProvider.CheckInType.ENTRY)
        assertEquals(TicketCheckProvider.CheckResult.Type.ANSWERS_REQUIRED, r.type)

        val qciList = dataStore.select(QueuedCheckIn::class.java).get().toList()
        assertEquals(0, qciList.size.toLong())
    }

    @Test
    @Throws(CheckException::class)
    fun testSearch() {
        configStore!!.setAllow_search(true)

        // Short searches are empty
        var srList: List<TicketCheckProvider.SearchResult> = p!!.search(mapOf("demo" to 1L), "foo", 1)
        assertEquals(0, srList.size.toLong())

        // Search by secret
        srList = p!!.search(mapOf("demo" to 1L), "kFNDgffgyw4", 1)
        assertEquals(1, srList.size.toLong())
        srList = p!!.search(mapOf("demo" to 1L), "baaaaar", 1)
        assertEquals(0, srList.size.toLong())

        // Search by name
        srList = p!!.search(mapOf("demo" to 1L), "Einstein", 1)
        assertEquals(0, srList.size.toLong())
        srList = p!!.search(mapOf("demo" to 1L), "WATSON", 1)
        assertEquals(1, srList.size.toLong())

        // Search by email
        srList = p!!.search(mapOf("demo" to 1L), "foobar@example.org", 1)
        assertEquals(0, srList.size.toLong())
        srList = p!!.search(mapOf("demo" to 1L), "holmesConnie@kelly.com", 1)
        assertEquals(3, srList.size.toLong())

        // Search by order code
        srList = p!!.search(mapOf("demo" to 1L), "AAAAA", 1)
        assertEquals(0, srList.size.toLong())
        srList = p!!.search(mapOf("demo" to 1L), "Vh3d3", 1)
        assertEquals(3, srList.size.toLong())

        val r = srList[0]
        assertEquals("Regular ticket", r.ticket)
        assertEquals(null, r.variation)
        assertEquals("Casey Flores", r.attendee_name)
        assertEquals("kfndgffgyw4tdgcacx6bb3bgemq69cxj", r.secret)
        assertEquals(false, r.isRedeemed)
        assertEquals(true, r.status == TicketCheckProvider.SearchResult.Status.PAID)
        assertEquals(true, r.isRequireAttention)
        assertEquals("Merch", srList[1].ticket)
        assertEquals(true, srList[1].isRedeemed)
        assertEquals(false, srList[1].isRequireAttention)
    }

    @Test
    @Throws(JSONException::class, CheckException::class)
    fun testStatusInfo() {
        val sr = p!!.status("demo", 1L)
        assertEquals("All", sr.eventName)
        assertEquals(19, sr.totalTickets)
        assertEquals(2, sr.alreadyScanned)
        assertEquals(2, sr.items!!.size)
        val i = sr.items!![0]
        assertEquals(1, i.id)
        assertEquals(8, i.total)
        assertEquals(1, i.checkins)
        assertEquals(true, i.isAdmission)
        assertEquals(0, i.variations!!.size)
    }

    @Test
    fun testSignedAndValid() {
        val r = p!!.check(mapOf("demo" to 1L), "E4BibyTSylQOgeKjuMPiTDxi5HXPuTVsx1qCli3IL0143gj0EZXOB9iQInANxRFJTt4Pf9nXnHdB91Qk/RN0L5AIBABSxw2TKFnSUNUCKAEAPAQA")
        assertEquals(TicketCheckProvider.CheckResult.Type.VALID, r.type)
        assertEquals(dataStore.count(QueuedCheckIn::class.java).get().value(), 1)
    }

    @Test
    fun testSignedAndNotYetValid() {
        val p2 = AsyncCheckProvider(configStore!!, dataStore)
        p2.setNow(ISODateTimeFormat.dateTime().parseDateTime("2023-02-03T22:59:59.000Z"))

        val r = p2.check(mapOf("demo" to 1L), "Ok4EsqDRCr2cL6yDRtqeP7j5Usr1Vj1Db7J0izOuRGx6Qn0BS1ISW2nxlW8PXkYRk7PJhIBmsK1V1ucq5obBoBAMG4p9jCPKBAheRdFV0REVDZUCKAEAVAQA")
        assertEquals(TicketCheckProvider.CheckResult.Type.INVALID_TIME, r.type)

        p2.setNow(ISODateTimeFormat.dateTime().parseDateTime("2023-02-03T23:01:01.000Z"))

        val r2 = p2.check(mapOf("demo" to 1L), "Ok4EsqDRCr2cL6yDRtqeP7j5Usr1Vj1Db7J0izOuRGx6Qn0BS1ISW2nxlW8PXkYRk7PJhIBmsK1V1ucq5obBoBAMG4p9jCPKBAheRdFV0REVDZUCKAEAVAQA")
        assertEquals(TicketCheckProvider.CheckResult.Type.VALID, r2.type)
        assertEquals(dataStore.count(QueuedCheckIn::class.java).get().value(), 1)
    }

    @Test
    fun testSignedAndNotLongerValid() {
        val p2 = AsyncCheckProvider(configStore!!, dataStore)
        p2.setNow(ISODateTimeFormat.dateTime().parseDateTime("2023-02-03T11:01:01.000Z"))

        val r = p2.check(mapOf("demo" to 1L), "EU9dJn3k5jzwfY4JQAKrTOVFmo+BvZKwH6UAIFOz3XTxABa7tmjU5UoLD8hJr3440uY7IFEHzau1DVk0sP994bgnzLNswAAKBARdUdGMmNVSHVUCKAEAVAQA")
        assertEquals(TicketCheckProvider.CheckResult.Type.INVALID_TIME, r.type)

        p2.setNow(ISODateTimeFormat.dateTime().parseDateTime("2023-02-03T10:59:59.000Z"))

        val r2 = p2.check(mapOf("demo" to 1L), "EU9dJn3k5jzwfY4JQAKrTOVFmo+BvZKwH6UAIFOz3XTxABa7tmjU5UoLD8hJr3440uY7IFEHzau1DVk0sP994bgnzLNswAAKBARdUdGMmNVSHVUCKAEAVAQA")
        assertEquals(TicketCheckProvider.CheckResult.Type.VALID, r2.type)
        assertEquals(dataStore.count(QueuedCheckIn::class.java).get().value(), 1)
    }

    @Test
    fun testSignedAndRevoked() {
        val rev = RevokedTicketSecret()
        rev.setEvent_slug(configStore!!.eventSlug)
        rev.setCreated("2020-10-19T10:00:00+00:00")
        rev.setSecret("E4BibyTSylQOgeKjuMPiTDxi5HXPuTVsx1qCli3IL0143gj0EZXOB9iQInANxRFJTt4Pf9nXnHdB91Qk/RN0L5AIBABSxw2TKFnSUNUCKAEAPAQA")
        rev.setJson_data("{}")
        dataStore.insert(rev)

        val r = p!!.check(mapOf("demo" to 1L), "E4BibyTSylQOgeKjuMPiTDxi5HXPuTVsx1qCli3IL0143gj0EZXOB9iQInANxRFJTt4Pf9nXnHdB91Qk/RN0L5AIBABSxw2TKFnSUNUCKAEAPAQA")
        assertEquals(TicketCheckProvider.CheckResult.Type.REVOKED, r.type)
        assertEquals(dataStore.count(QueuedCheckIn::class.java).get().value(), 0)
    }

    @Test
    fun testSignedAndBlocked() {
        val rev = BlockedTicketSecret()
        rev.setEvent_slug(configStore!!.eventSlug)
        rev.setUpdated("2020-10-19T10:00:00+00:00")
        rev.setSecret("E4BibyTSylQOgeKjuMPiTDxi5HXPuTVsx1qCli3IL0143gj0EZXOB9iQInANxRFJTt4Pf9nXnHdB91Qk/RN0L5AIBABSxw2TKFnSUNUCKAEAPAQA")
        rev.isBlocked = true
        rev.setJson_data("{}")
        dataStore.insert(rev)

        val r = p!!.check(mapOf("demo" to 1L), "E4BibyTSylQOgeKjuMPiTDxi5HXPuTVsx1qCli3IL0143gj0EZXOB9iQInANxRFJTt4Pf9nXnHdB91Qk/RN0L5AIBABSxw2TKFnSUNUCKAEAPAQA")
        assertEquals(TicketCheckProvider.CheckResult.Type.BLOCKED, r.type)
        assertEquals(dataStore.count(QueuedCheckIn::class.java).get().value(), 0)
    }

    @Test
    fun testSignedUnknownProduct() {
        val r = p!!.check(mapOf("demo" to 1L), "OUmw2Ro3YOMQ4ktAlAIsDVe4Xsr1KXla/0SZVN34qIZWtUX0hx1DXDHxaCatGTNzOeCMjHQABR5E6ESCOOx1g7AIkBhVkdDdJJTVSZWCKAEAPAQA")
        assertEquals(TicketCheckProvider.CheckResult.Type.ERROR, r.type)
        assertEquals(dataStore.count(QueuedCheckIn::class.java).get().value(), 0)
    }

    @Test
    fun testSignedInvalidSignature() {
        val r = p!!.check(mapOf("demo" to 1L), "EFAKEyTSylQOgeKjuMPiTDxi5HXPuTVsx1qCli3IL0143gj0EZXOB9iQInANxRFJTt4Pf9nXnHdB91Qk/RN0L5AIBABSxw2TKFnSUNUCKAEAPAQA")
        assertEquals(TicketCheckProvider.CheckResult.Type.INVALID, r.type)
        assertEquals(dataStore.count(QueuedCheckIn::class.java).get().value(), 0)
    }

    @Test
    @Throws(CheckException::class)
    fun testSearchMultipleLists() {
        configStore!!.setAllow_search(true)

        // Search by email
        var srList = p!!.search(mapOf("demo" to 1L), "holmesConnie@kelly.com", 1)
        assertEquals(3, srList.size.toLong())
        srList = p!!.search(mapOf("demo" to 1L, "demo2" to 7L), "holmesConnie@kelly.com", 1)
        assertEquals(6, srList.size.toLong())
        assertEquals(1, srList.filter { it.secret == "kfndgffgyw4tdgcacx6bb3bgemq69cxj" }.size)
        assertEquals(1, srList.filter { it.secret == "hnu44vgdap9p3c7x634km9ftzg4j7454" }.size)
    }

    @Test
    fun testWrongEvent() {
        val r = p!!.check(mapOf("demo" to 1L), "hnu44vgdap9p3c7x634km9ftzg4j7454")
        assertEquals(TicketCheckProvider.CheckResult.Type.INVALID, r.type)
        assertNull(r.eventSlug)
    }

    @Test
    fun testSimpleMultipleLists() {
        var r = p!!.check(mapOf("demo" to 1L, "demo2" to 7L), "kfndgffgyw4tdgcacx6bb3bgemq69cxj")
        assertEquals(TicketCheckProvider.CheckResult.Type.VALID, r.type)
        assertEquals("demo", r.eventSlug)
        r = p!!.check(mapOf("demo" to 1L, "demo2" to 7L), "hnu44vgdap9p3c7x634km9ftzg4j7454")
        assertEquals(TicketCheckProvider.CheckResult.Type.VALID, r.type)
        assertEquals("demo2", r.eventSlug)
        r = p!!.check(mapOf("demo" to 1L, "demo2" to 7L), "kfndgffgyw4tdgcacx6bb3bgemq69cxj")
        assertEquals(TicketCheckProvider.CheckResult.Type.USED, r.type)
        assertEquals("demo", r.eventSlug)
        r = p!!.check(mapOf("demo" to 1L, "demo2" to 7L), "hnu44vgdap9p3c7x634km9ftzg4j7454")
        assertEquals(TicketCheckProvider.CheckResult.Type.VALID, r.type)
        assertEquals("demo2", r.eventSlug)
    }

    @Test
    fun testSignedMultipleLists() {
        var r = p!!.check(mapOf("demo2" to 7L), "E4BibyTSylQOgeKjuMPiTDxi5HXPuTVsx1qCli3IL0143gj0EZXOB9iQInANxRFJTt4Pf9nXnHdB91Qk/RN0L5AIBABSxw2TKFnSUNUCKAEAPAQA")
        assertEquals(TicketCheckProvider.CheckResult.Type.INVALID, r.type)
        assertEquals(dataStore.count(QueuedCheckIn::class.java).get().value(), 0)
        r = p!!.check(mapOf("demo2" to 7L, "demo" to 1L), "E4BibyTSylQOgeKjuMPiTDxi5HXPuTVsx1qCli3IL0143gj0EZXOB9iQInANxRFJTt4Pf9nXnHdB91Qk/RN0L5AIBABSxw2TKFnSUNUCKAEAPAQA")
        assertEquals(TicketCheckProvider.CheckResult.Type.VALID, r.type)
        assertEquals(dataStore.count(QueuedCheckIn::class.java).get().value(), 1)
    }
}
