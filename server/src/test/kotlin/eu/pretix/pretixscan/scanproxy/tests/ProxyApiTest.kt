package eu.pretix.pretixscan.scanproxy.tests

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import eu.pretix.libpretixsync.check.TicketCheckProvider
import eu.pretix.libpretixsync.db.Event
import eu.pretix.libpretixsync.db.Settings
import eu.pretix.libpretixsync.db.SubEvent
import eu.pretix.libpretixsync.sync.*
import eu.pretix.pretixscan.scanproxy.Server
import eu.pretix.pretixscan.scanproxy.db.DownstreamDeviceEntity
import eu.pretix.pretixscan.scanproxy.db.SyncedEventEntity
import eu.pretix.pretixscan.scanproxy.proxyDeps
import eu.pretix.pretixscan.scanproxy.tests.test.FakeFileStorage
import eu.pretix.pretixscan.scanproxy.tests.test.jsonResource
import eu.pretix.pretixscan.scanproxy.tests.utils.BaseDatabaseTest
import io.javalin.testtools.JavalinTest
import org.hamcrest.CoreMatchers.*
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.util.*


class ProxyApiTest : BaseDatabaseTest() {
    @Before
    fun setUpFakes() {
        var s = SyncedEventEntity()
        s.slug = "demo"
        proxyDeps.proxyData.insert(s)
        s = SyncedEventEntity()
        s.slug = "demo2"
        proxyDeps.proxyData.insert(s)
        EventSyncAdapter(proxyDeps.syncData, "demo", "demo", proxyDeps.pretixApi, "", null).standaloneRefreshFromJSON(jsonResource("events/event1.json"))
        EventSyncAdapter(proxyDeps.syncData, "demo", "demo", proxyDeps.pretixApi, "", null).standaloneRefreshFromJSON(jsonResource("events/event2.json"))
        ItemSyncAdapter(proxyDeps.db, FakeFileStorage(), "demo", proxyDeps.pretixApi, "", null).standaloneRefreshFromJSON(jsonResource("items/item1.json"))
        ItemSyncAdapter(proxyDeps.db, FakeFileStorage(), "demo", proxyDeps.pretixApi, "", null).standaloneRefreshFromJSON(jsonResource("items/item2.json"))
        ItemSyncAdapter(proxyDeps.db, FakeFileStorage(), "demo2", proxyDeps.pretixApi, "", null).standaloneRefreshFromJSON(jsonResource("items/event2-item3.json"))
        CheckInListSyncAdapter(proxyDeps.syncData, FakeFileStorage(), "demo", proxyDeps.pretixApi, "", null, 0).standaloneRefreshFromJSON(
            jsonResource("checkinlists/list1.json")
        )
        CheckInListSyncAdapter(proxyDeps.syncData, FakeFileStorage(), "demo", proxyDeps.pretixApi, "", null, 0).standaloneRefreshFromJSON(
            jsonResource("checkinlists/list2.json")
        )
        CheckInListSyncAdapter(proxyDeps.syncData, FakeFileStorage(), "demo", proxyDeps.pretixApi, "", null, 0).standaloneRefreshFromJSON(
            jsonResource("checkinlists/list3.json")
        )
        CheckInListSyncAdapter(proxyDeps.syncData, FakeFileStorage(), "demo", proxyDeps.pretixApi, "", null, 0).standaloneRefreshFromJSON(
            jsonResource("checkinlists/list4.json")
        )
        CheckInListSyncAdapter(proxyDeps.syncData, FakeFileStorage(), "demo", proxyDeps.pretixApi, "", null, 0).standaloneRefreshFromJSON(
            jsonResource("checkinlists/list5.json")
        )
        CheckInListSyncAdapter(proxyDeps.syncData, FakeFileStorage(), "demo", proxyDeps.pretixApi, "", null, 0).standaloneRefreshFromJSON(
            jsonResource("checkinlists/list6.json")
        )
        CheckInListSyncAdapter(proxyDeps.syncData, FakeFileStorage(), "demo2", proxyDeps.pretixApi, "", null, 0).standaloneRefreshFromJSON(
            jsonResource("checkinlists/event2-list7.json")
        )
        SubEventSyncAdapter(proxyDeps.syncData, "demo", "14", proxyDeps.pretixApi, "", null).standaloneRefreshFromJSON(jsonResource("subevents/subevent1.json"))

        val osa = OrderSyncAdapter(proxyDeps.db, FakeFileStorage(), "demo", 0, true, false, proxyDeps.pretixApi, "", null)
        osa.standaloneRefreshFromJSON(jsonResource("orders/order1.json"))
        osa.standaloneRefreshFromJSON(jsonResource("orders/order2.json"))
        osa.standaloneRefreshFromJSON(jsonResource("orders/order3.json"))
        osa.standaloneRefreshFromJSON(jsonResource("orders/order4.json"))
        osa.standaloneRefreshFromJSON(jsonResource("orders/order5.json"))
        osa.standaloneRefreshFromJSON(jsonResource("orders/order6.json"))
        osa.standaloneRefreshFromJSON(jsonResource("orders/order7.json"))
        osa.standaloneRefreshFromJSON(jsonResource("orders/order8.json"))
        osa.standaloneRefreshFromJSON(jsonResource("orders/order9.json"))
        val osa2 = OrderSyncAdapter(proxyDeps.db, FakeFileStorage(), "demo2", 0, true, false, proxyDeps.pretixApi, "", null)
        osa2.standaloneRefreshFromJSON(jsonResource("orders/event2-order1.json"))
    }

    private fun createDevice(initialized: Boolean=true): DownstreamDeviceEntity {
        val charPool: List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9')
        val d = DownstreamDeviceEntity()
        d.uuid = UUID.randomUUID().toString()
        d.name = "Test"
        d.added_datetime = System.currentTimeMillis().toString()
        if (initialized) {
            d.api_token = (1..64)
                .map { kotlin.random.Random.nextInt(0, charPool.size) }
                .map(charPool::get)
                .joinToString("")
        } else {
            d.init_token = (1..16)
                .map { kotlin.random.Random.nextInt(0, charPool.size) }
                .map(charPool::get)
                .joinToString("")
        }
        proxyDeps.proxyData.insert(d)
        return d
    }

    @Test
    fun `require device authentication on all privileged URLs`() = JavalinTest.test(app) { server, client ->
        val device = createDevice()
        val urls = listOf(
            "/proxyapi/v1/rpc/demo/1/status/",
            "/proxyapi/v1/rpc/demo/1/search/",
            "/proxyapi/v1/rpc/demo/1/check/",
            "/proxyapi/v1/rpc/search/",
            "/proxyapi/v1/rpc/check/",
        )
        for (u in urls) {
            assertThat(client.get(u).code, equalTo(401))
            assertThat(client.get(u) {
                it.header("Authorization", "Device WRONG")
            }.code, equalTo(401))
            assertThat(client.get(u) {
                it.header("Authorization", "Device ${device.api_token}")
            }.code, either(equalTo(405)).or(equalTo(400)).or(equalTo(200)).or(equalTo(404)))
        }
    }


    @Test
    fun `StatusEndpoint returns StatusResult`() = JavalinTest.test(app) { server, client ->
        val device = createDevice()
        val resp = client.get("/proxyapi/v1/rpc/demo/1/status/") {
            it.header("Authorization", "Device ${device.api_token}")
        }
        assertThat(resp.code, equalTo(200))
        val json = jacksonObjectMapper().readValue<MutableMap<Any, Any>>(resp.body!!.string())
        assertThat(json["eventName"], equalTo("All"))
        assertThat(json["totalTickets"], equalTo(19))
        assertThat(json["alreadyScanned"], equalTo(2))
        assertThat(json["currentlyInside"], equalTo(null))
        assertThat((json["items"] as List<Map<Any, Any>>)[0]["id"], equalTo(1))
        assertThat((json["items"] as List<Map<Any, Any>>)[0]["name"], notNullValue())
        assertThat((json["items"] as List<Map<Any, Any>>)[0]["total"], equalTo(8))
        assertThat((json["items"] as List<Map<Any, Any>>)[0]["checkins"], equalTo(1))
        assertThat((json["items"] as List<Map<Any, Any>>)[0]["isAdmission"], equalTo(true))
        assertThat((json["items"] as List<Map<Any, Any>>)[0]["variations"], equalTo(emptyList<Map<Any, Any>>()))
    }


    @Test
    fun `SearchEndpoint returns SearchResult`() = JavalinTest.test(app) { server, client ->
        val device = createDevice()
        val resp = client.post("/proxyapi/v1/rpc/demo/1/search/", mapOf(
            "page" to 1,
            "query" to "holmesConnie@kelly.com"
        )) {
            it.header("Authorization", "Device ${device.api_token}")
        }
        assertThat(resp.code, equalTo(200))
        val json = jacksonObjectMapper().readValue<MutableList<MutableMap<Any, Any>>>(resp.body!!.string())
        assertThat(json.size, equalTo(3))
        assertThat(json[0]["secret"], notNullValue())
        assertThat(json[0]["ticket"], notNullValue())
        assertThat(json[0]["variation"], nullValue())
        assertThat(json[0]["seat"], nullValue())
        assertThat(json[0]["attendee_name"], notNullValue())
        assertThat(json[0]["orderCode"], notNullValue())
        assertThat(json[0]["positionId"], notNullValue())
        assertThat(json[0]["addonText"], nullValue())
        assertThat(json[0]["status"], equalTo("PAID"))
        assertThat(json[0]["isRedeemed"], equalTo(false))
        assertThat(json[0]["isRequireAttention"], equalTo(true))
        assertThat(json[0]["position"], notNullValue())
    }


    @Test
    fun `MultiSearchEndpoint returns SearchResult`() = JavalinTest.test(app) { server, client ->
        val device = createDevice()
        val resp = client.post("/proxyapi/v1/rpc/search/", mapOf(
            "events_and_checkin_lists" to mapOf(
                "demo" to 1,
                "demo2" to 7,
            ),
            "page" to 1,
            "query" to "holmesConnie@kelly.com"
        )) {
            it.header("Authorization", "Device ${device.api_token}")
        }
        assertThat(resp.code, equalTo(200))
        val json = jacksonObjectMapper().readValue<MutableList<MutableMap<Any, Any>>>(resp.body!!.string())
        assertThat(json.size, equalTo(6))
        assertThat(json[0]["secret"], notNullValue())
        assertThat(json[0]["ticket"], notNullValue())
        assertThat(json[0]["variation"], nullValue())
        assertThat(json[0]["seat"], nullValue())
        assertThat(json[0]["attendee_name"], notNullValue())
        assertThat(json[0]["orderCode"], notNullValue())
        assertThat(json[0]["positionId"], notNullValue())
        assertThat(json[0]["addonText"], nullValue())
        assertThat(json[0]["status"], equalTo("PAID"))
        assertThat(json[0]["isRedeemed"], equalTo(false))
        assertThat(json[0]["isRequireAttention"], equalTo(true))
        assertThat(json[0]["position"], notNullValue())
    }


    @Test
    fun `CheckEndpoint returns CheckResult`() = JavalinTest.test(app) { server, client ->
        val device = createDevice()
        val resp = client.post("/proxyapi/v1/rpc/demo/1/check/", mapOf(
            "ticketid" to "kfndgffgyw4tdgcacx6bb3bgemq69cxj",
            "answers" to emptyList<Map<String, String>>(),
            "ignore_unpaid" to false,
            "with_badge_data" to true,
            "type" to "exit",
            "source_type" to "barcode"
        )) {
            it.header("Authorization", "Device ${device.api_token}")
        }
        assertThat(resp.code, equalTo(200))
        val json = jacksonObjectMapper().readValue<MutableMap<Any, Any>>(resp.body!!.string())
        assertThat(json["type"], equalTo("VALID"))
        assertThat(json["scanType"], equalTo("EXIT"))
        assertThat(json["ticket"], equalTo("Regular ticket"))
        assertThat(json["variation"], nullValue())
        assertThat(json["attendee_name"], equalTo("Casey Flores"))
        assertThat(json["seat"], nullValue())
        assertThat(json["message"], nullValue())
        assertThat(json["orderCode"], equalTo("VH3D3"))
        assertThat(json["positionId"], equalTo(1))
        assertThat(json["firstScanned"], nullValue())
        assertThat(json["addonText"], nullValue())
        assertThat(json["reasonExplanation"], nullValue())
        assertThat(json["checkinTexts"], equalTo(emptyList<String>()))
        assertThat(json["isRequireAttention"], equalTo(true))
        assertThat(json["isCheckinAllowed"], equalTo(true))
        assertThat(json["requiredAnswers"], nullValue())
        assertThat(json["position"], notNullValue())
        assertThat(json["eventSlug"], equalTo("demo"))
        assertThat(json["offline"], equalTo(true))
    }


    @Test
    fun `CheckEndpoint for unknown event triggers sync`() = JavalinTest.test(app) { server, client ->
        assertThat(proxyDeps.proxyData.count(SyncedEventEntity::class).get().value(), equalTo(2))
        val device = createDevice()
        val resp = client.post("/proxyapi/v1/rpc/unknown/1/check/", mapOf(
            "ticketid" to "kfndgffgyw4tdgcacx6bb3bgemq69cxj",
            "answers" to emptyList<Map<String, String>>(),
            "ignore_unpaid" to false,
            "with_badge_data" to true,
            "type" to "exit",
            "source_type" to "barcode"
        )) {
            it.header("Authorization", "Device ${device.api_token}")
        }
        assertThat(resp.code, equalTo(200))
        assertThat(proxyDeps.proxyData.count(SyncedEventEntity::class).get().value(), equalTo(3))
    }


    @Test
    fun `MultiCheckEndpoint returns CheckResult`() = JavalinTest.test(app) { server, client ->
        val device = createDevice()
        val resp = client.post("/proxyapi/v1/rpc/check/", mapOf(
            "events_and_checkin_lists" to mapOf(
                "demo" to 1,
                "demo2" to 7,
            ),
            "ticketid" to "kfndgffgyw4tdgcacx6bb3bgemq69cxj",
            "answers" to emptyList<Map<String, String>>(),
            "ignore_unpaid" to false,
            "with_badge_data" to true,
            "type" to "exit",
            "source_type" to "barcode"
        )) {
            it.header("Authorization", "Device ${device.api_token}")
        }
        assertThat(resp.code, equalTo(200))
        val json = jacksonObjectMapper().readValue<MutableMap<Any, Any>>(resp.body!!.string())
        assertThat(json["type"], equalTo("VALID"))
        assertThat(json["scanType"], equalTo("EXIT"))
        assertThat(json["ticket"], equalTo("Regular ticket"))
        assertThat(json["variation"], nullValue())
        assertThat(json["attendee_name"], equalTo("Casey Flores"))
        assertThat(json["seat"], nullValue())
        assertThat(json["message"], nullValue())
        assertThat(json["orderCode"], equalTo("VH3D3"))
        assertThat(json["positionId"], equalTo(1))
        assertThat(json["firstScanned"], nullValue())
        assertThat(json["addonText"], nullValue())
        assertThat(json["reasonExplanation"], nullValue())
        assertThat(json["checkinTexts"], equalTo(emptyList<String>()))
        assertThat(json["isRequireAttention"], equalTo(true))
        assertThat(json["isCheckinAllowed"], equalTo(true))
        assertThat(json["requiredAnswers"], nullValue())
        assertThat(json["position"], notNullValue())
        assertThat(json["eventSlug"], equalTo("demo"))
        assertThat(json["offline"], equalTo(true))
    }


    @Test
    fun `MultiCheckEndpoint for unknown event triggers sync`() = JavalinTest.test(app) { server, client ->
        assertThat(proxyDeps.proxyData.count(SyncedEventEntity::class).get().value(), equalTo(2))
        val device = createDevice()
        val resp = client.post("/proxyapi/v1/rpc/check/", mapOf(
            "events_and_checkin_lists" to mapOf(
                "unknown" to 9999,
            ),
            "ticketid" to "kfndgffgyw4tdgcacx6bb3bgemq69cxj",
            "answers" to emptyList<Map<String, String>>(),
            "ignore_unpaid" to false,
            "with_badge_data" to true,
            "type" to "exit",
            "source_type" to "barcode"
        )) {
            it.header("Authorization", "Device ${device.api_token}")
        }
        assertThat(resp.code, equalTo(200))
        assertThat(proxyDeps.proxyData.count(SyncedEventEntity::class).get().value(), equalTo(3))
    }


    @Test
    fun `MultiCheckEndpoint supports questions`() = JavalinTest.test(app) { server, client ->
        QuestionSyncAdapter(proxyDeps.syncData, FakeFileStorage(), "demo", proxyDeps.pretixApi, "", null).standaloneRefreshFromJSON(
            jsonResource("questions/question1.json")
        )
        val device = createDevice()
        var resp = client.post("/proxyapi/v1/rpc/check/", mapOf(
            "events_and_checkin_lists" to mapOf(
                "demo" to 1,
            ),
            "ticketid" to "kfndgffgyw4tdgcacx6bb3bgemq69cxj",
            "answers" to emptyList<Map<String, String>>(),
            "ignore_unpaid" to false,
            "with_badge_data" to true,
            "type" to "entry",
            "source_type" to "barcode"
        )) {
            it.header("Authorization", "Device ${device.api_token}")
        }
        assertThat(resp.code, equalTo(200))
        var json = jacksonObjectMapper().readValue<MutableMap<Any, Any>>(resp.body!!.string())
        assertThat(json["type"], equalTo("ANSWERS_REQUIRED"))
        val requiredAnswer = (json["requiredAnswers"] as List<Map<Any, Any>>)[0]
        assertThat((requiredAnswer["question"] as Map<Any, Any>)["id"], equalTo(1))
        assertThat((requiredAnswer["question"] as Map<Any, Any>)["server_id"], equalTo(1))
        assertThat((requiredAnswer["question"] as Map<Any, Any>)["event_slug"], equalTo("demo"))
        assertThat((requiredAnswer["question"] as Map<Any, Any>)["required"], equalTo(true))
        assertThat((requiredAnswer["question"] as Map<Any, Any>)["json_data"], notNullValue())
        assertThat((requiredAnswer["question"] as Map<Any, Any>)["identifier"], equalTo("ABTBAB8S"))
        assertThat((requiredAnswer["question"] as Map<Any, Any>)["type"], equalTo("B"))
        assertThat((requiredAnswer["question"] as Map<Any, Any>)["hidden"], equalTo(false))
        assertThat(requiredAnswer["currentValue"], equalTo(""))

        resp = client.post("/proxyapi/v1/rpc/check/", mapOf(
            "events_and_checkin_lists" to mapOf(
                "demo" to 1,
            ),
            "ticketid" to "kfndgffgyw4tdgcacx6bb3bgemq69cxj",
            "answers" to listOf(mapOf(
                "question" to mapOf(
                    "server_id" to 1
                ),
                "value" to "True"
            )),
            "ignore_unpaid" to false,
            "with_badge_data" to true,
            "type" to "entry",
            "source_type" to "barcode"
        )) {
            it.header("Authorization", "Device ${device.api_token}")
        }
        assertThat(resp.code, equalTo(200))
        json = jacksonObjectMapper().readValue<MutableMap<Any, Any>>(resp.body!!.string())
        assertThat(json["type"], equalTo("VALID"))
    }
}