package eu.pretix.pretixscan.scanproxy.tests

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
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
import org.hamcrest.CoreMatchers.either
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Before
import org.junit.Test
import java.util.*


class PretixApiTest : BaseDatabaseTest() {
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
        SubEventSyncAdapter(proxyDeps.syncData, "demo", "14", proxyDeps.pretixApi, "", null).standaloneRefreshFromJSON(jsonResource("subevents/subevent1.json"))

        val osa = OrderSyncAdapter(proxyDeps.db, FakeFileStorage(), "demo", 0, true, false, proxyDeps.pretixApi, "", null)
        osa.standaloneRefreshFromJSON(jsonResource("orders/order1.json"))
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
            "/api/v1/organizers/demo/subevents/",
            "/api/v1/organizers/demo/events/demo/categories/",
            "/api/v1/organizers/demo/events/demo/items/",
            "/api/v1/organizers/demo/events/demo/questions/",
            "/api/v1/organizers/demo/events/demo/badgelayouts/",
            "/api/v1/organizers/demo/events/demo/checkinlists/",
            "/api/v1/organizers/demo/events/demo/orders/",
            "/api/v1/organizers/demo/events/demo/badgeitems/",
            "/api/v1/organizers/demo/events/demo/settings/",
            "/api/v1/organizers/demo/events/demo/revokedsecrets/",
            "/api/v1/organizers/demo/events/demo/seubevents/2/",
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
    fun `UpstreamVersion returns versions`() = JavalinTest.test(app) { server, client ->
        val resp = client.get("/api/v1/version")
        assertThat(resp.code, equalTo(200))
        val json = jacksonObjectMapper().readValue<MutableMap<Any, Any>>(resp.body!!.string())
        assertThat(json["pretix_numeric"], equalTo(proxyDeps.configStore.knownPretixVersion.toInt()))
        assertThat(json["pretixscan_proxy"], equalTo(Server.VERSION))
        assertThat(json["pretixscan_proxy_numeric"], equalTo(Server.VERSION_CODE))
    }


    @Test
    fun `SetupDownstream rejects invalid init token`() = JavalinTest.test(app) { server, client ->
        val resp = client.post("/api/v1/device/initialize", mapOf(
            "token" to "foobar",
            "hardware_brand" to "A",
            "hardware_model" to "A",
            "software_brand" to "A",
            "software_version" to "A",
        ))
        assertThat(resp.code, equalTo(400))
    }

    @Test
    fun `SetupDownstream returns API token`() = JavalinTest.test(app) { server, client ->
        val device = createDevice(false)
        val resp = client.post("/api/v1/device/initialize", mapOf(
            "token" to device.init_token,
            "hardware_brand" to "A",
            "hardware_model" to "A",
            "software_brand" to "A",
            "software_version" to "A",
        ))
        assertThat(resp.code, equalTo(200))
        val json = jacksonObjectMapper().readValue<MutableMap<Any, Any>>(resp.body!!.string())
        proxyDeps.proxyData.refresh(device)
        assertThat(json["api_token"], equalTo(device.api_token))
    }

    @Test
    fun `EventsEndpoint returns list of future events`() = JavalinTest.test(app) { server, client ->
        assertThat(proxyDeps.syncData.count(Event::class.java).get().value(), equalTo(2))
        val device = createDevice()
        var resp = client.get("/api/v1/organizers/demo/events/") {
            it.header("Authorization", "Device ${device.api_token}")
        }
        assertThat(resp.code, equalTo(200))
        var json = jacksonObjectMapper().readValue<MutableMap<Any, Any>>(resp.body!!.string())
        assertThat(json["count"], equalTo(0))

        // Set date to today
        val ev1 = proxyDeps.syncData.select(Event::class.java).where(Event.SLUG.eq("demo")).get().first()
        ev1.setDate_to(Date())
        proxyDeps.syncData.update(ev1)

        resp = client.get("/api/v1/organizers/demo/events/") {
            it.header("Authorization", "Device ${device.api_token}")
        }
        assertThat(resp.code, equalTo(200))
        json = jacksonObjectMapper().readValue<MutableMap<Any, Any>>(resp.body!!.string())
        assertThat(json["count"], equalTo(1))
        assertThat((json["results"] as List<Map<Any, Any>>)[0]["slug"], equalTo("demo"))
    }

    @Test
    fun `SubEventsEndpoint returns list of future subevents and subevent details`() = JavalinTest.test(app) { server, client ->
        val device = createDevice()
        var resp = client.get("/api/v1/organizers/demo/subevents/") {
            it.header("Authorization", "Device ${device.api_token}")
        }
        assertThat(resp.code, equalTo(200))
        var json = jacksonObjectMapper().readValue<MutableMap<Any, Any>>(resp.body!!.string())
        assertThat(json["count"], equalTo(0))

        // Set date to today
        val ev1 = proxyDeps.syncData.select(SubEvent::class.java).get().first()
        ev1.setDate_to(Date())
        proxyDeps.syncData.update(ev1)

        resp = client.get("/api/v1/organizers/demo/subevents/") {
            it.header("Authorization", "Device ${device.api_token}")
        }
        assertThat(resp.code, equalTo(200))
        json = jacksonObjectMapper().readValue<MutableMap<Any, Any>>(resp.body!!.string())
        assertThat(json["count"], equalTo(1))
        val subeventId = (json["results"] as List<Map<Any, Any>>)[0]["id"]

        resp = client.get("/api/v1/organizers/demo/events/demo/subevents/$subeventId/") {
            it.header("Authorization", "Device ${device.api_token}")
        }
        json = jacksonObjectMapper().readValue<MutableMap<Any, Any>>(resp.body!!.string())
        assertThat(resp.code, equalTo(200))
        assertThat(json["id"], equalTo(subeventId))
    }

    @Test
    fun `EventsEndpoint returns event details`() = JavalinTest.test(app) { server, client ->
        assertThat(proxyDeps.syncData.count(Event::class.java).get().value(), equalTo(2))
        val device = createDevice()
        val resp = client.get("/api/v1/organizers/demo/events/demo/") {
            it.header("Authorization", "Device ${device.api_token}")
        }
        assertThat(resp.code, equalTo(200))
        val json = jacksonObjectMapper().readValue<MutableMap<Any, Any>>(resp.body!!.string())
        assertThat(json["slug"], equalTo("demo"))
    }

    @Test
    fun `EventsEndpoint for unknown event triggers sync`() = JavalinTest.test(app) { server, client ->
        assertThat(proxyDeps.proxyData.count(SyncedEventEntity::class).get().value(), equalTo(2))
        val device = createDevice()
        val resp = client.get("/api/v1/organizers/demo/events/unknown/") {
            it.header("Authorization", "Device ${device.api_token}")
        }
        assertThat(resp.code, equalTo(404))
        assertThat(proxyDeps.proxyData.count(SyncedEventEntity::class).get().value(), equalTo(3))
    }

    @Test
    fun `Empty endpoints respond empty`() = JavalinTest.test(app) { server, client ->
        val device = createDevice()
        val urls = listOf(
            "/api/v1/organizers/demo/events/demo/categories/",
            "/api/v1/organizers/demo/events/demo/questions/",
            "/api/v1/organizers/demo/events/demo/orders/",
            "/api/v1/organizers/demo/events/demo/revokedsecrets/",
        )
        for (u in urls) {
            val resp = client.get(u) {
                it.header("Authorization", "Device ${device.api_token}")
            }
            assertThat(resp.code, equalTo(200))
            val json = jacksonObjectMapper().readValue<MutableMap<Any, Any>>(resp.body!!.string())
            assertThat(json["count"], equalTo(0))
        }
    }

    @Test
    fun `ItemsEndpoint endpoint responds with items of the correct event`() = JavalinTest.test(app) { server, client ->
        val device = createDevice()
        val resp = client.get("/api/v1/organizers/demo/events/demo/items/") {
            it.header("Authorization", "Device ${device.api_token}")
        }
        assertThat(resp.code, equalTo(200))
        val json = jacksonObjectMapper().readValue<MutableMap<Any, Any>>(resp.body!!.string())
        assertThat(json["count"], equalTo(2))
        assertThat((json["results"] as List<Map<Any, Any>>)[0]["id"], equalTo(1))
    }

    @Test
    fun `CheckInListEndpoint responds with checkinlists`() = JavalinTest.test(app) { server, client ->
        val device = createDevice()
        val resp = client.get("/api/v1/organizers/demo/events/demo/checkinlists/") {
            it.header("Authorization", "Device ${device.api_token}")
        }
        assertThat(resp.code, equalTo(200))
        val json = jacksonObjectMapper().readValue<MutableMap<Any, Any>>(resp.body!!.string())
        assertThat(json["count"], equalTo(1))
        assertThat((json["results"] as List<Map<Any, Any>>)[0]["id"], equalTo(1))
        assertThat((json["results"] as List<Map<Any, Any>>)[0]["name"], equalTo("All"))
    }

    @Test
    fun `BadgeItemEndpoint responds with badge items (currently empty)`() = JavalinTest.test(app) { server, client ->
        val device = createDevice()
        val resp = client.get("/api/v1/organizers/demo/events/demo/badgeitems/") {
            it.header("Authorization", "Device ${device.api_token}")
        }
        assertThat(resp.code, equalTo(200))
        val json = jacksonObjectMapper().readValue<MutableMap<Any, Any>>(resp.body!!.string())
        assertThat(json["count"], equalTo(0))
    }

    @Test
    fun `SettingsEndpoint responds with event settings`() = JavalinTest.test(app) { server, client ->
        val s = Settings()
        s.setSlug("demo")
        s.setJson_data("{\"foo\": \"bar\"}")
        proxyDeps.syncData.insert(s)

        val device = createDevice()
        val resp = client.get("/api/v1/organizers/demo/events/demo/settings/") {
            it.header("Authorization", "Device ${device.api_token}")
        }
        assertThat(resp.code, equalTo(200))
        val json = jacksonObjectMapper().readValue<MutableMap<Any, Any>>(resp.body!!.string())
        assertThat(json["foo"], equalTo("bar"))
    }
}