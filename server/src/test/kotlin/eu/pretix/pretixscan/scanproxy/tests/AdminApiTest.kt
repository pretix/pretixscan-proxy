package eu.pretix.pretixscan.scanproxy.tests

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import eu.pretix.pretixscan.scanproxy.PretixScanConfig
import eu.pretix.pretixscan.scanproxy.db.SyncedEventEntity
import eu.pretix.pretixscan.scanproxy.proxyDeps
import eu.pretix.pretixscan.scanproxy.tests.utils.BaseDatabaseTest
import eu.pretix.pretixscan.scanproxy.tests.utils.TestConfigStore
import io.javalin.testtools.JavalinTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.hamcrest.CoreMatchers.*
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test


class AdminApiTest : BaseDatabaseTest() {

    @Test
    fun `require admin authentication on all admin URLs`() = JavalinTest.test(app) { server, client ->
        val urls = listOf(
            "/proxyapi/v1/configure",
            "/proxyapi/v1/state",
            "/proxyapi/v1/removeevent",
            "/proxyapi/v1/addevent",
            "/proxyapi/v1/init",
            "/proxyapi/v1/initready",
            "/proxyapi/v1/remove",
            "/proxyapi/v1/sync",
            "/proxyapi/v1/synceventlist",
        )
        for (u in urls) {
            assertThat(client.get(u).code, equalTo(401))
            assertThat(client.get(u) {
                it.header("Authorization", "Basic Zm9vOndyb25n")
            }.code, equalTo(401))
            assertThat(client.get(u) {
                it.header("Authorization", "Basic Zm9vOmJhcg==")
            }.code, either(equalTo(405)).or(equalTo(400)).or(equalTo(200)).or(equalTo(404)))
        }
    }

    @Test
    fun `SetupUpstream fails if already configured`() = JavalinTest.test(app) { server, client ->
        (proxyDeps.configStore as TestConfigStore).configured = true
        assertThat(
            client.post(
                "/proxyapi/v1/configure", mapOf(
                    "url" to "http://example.org",
                    "token" to "foobar"
                )
            ) {
                it.header("Authorization", "Basic Zm9vOmJhcg==")
            }.code, equalTo(400)
        )
    }

    @Test
    fun `ConfigState returns state`() = JavalinTest.test(app) { server, client ->
        val resp = client.get("/proxyapi/v1/state") {
            it.header("Authorization", "Basic Zm9vOmJhcg==")
        }
        assertThat(resp.code, equalTo(200))
        val json = jacksonObjectMapper().readValue<MutableMap<Any, Any>>(resp.body!!.string())
        assertThat(json["configured"], equalTo(true))
        assertThat(json["organizer"], equalTo("demo"))
        assertThat(json["upstreamUrl"], equalTo("https://pretix.eu"))
    }

    @Test
    fun `AddEvent and RemoveEvent`() = JavalinTest.test(app) { server, client ->
        var resp = client.post(
            "/proxyapi/v1/addevent", mapOf(
                "slug" to "foobar"
            )
        ) {
            it.header("Authorization", "Basic Zm9vOmJhcg==")
        }
        assertThat(
            proxyDeps.proxyData.count(SyncedEventEntity::class).where(SyncedEventEntity.SLUG eq "foobar").get().value(),
            equalTo(1)
        )
        assertThat(resp.code, equalTo(200))

        resp = client.post(
            "/proxyapi/v1/removeevent", mapOf(
                "slug" to "foobar"
            )
        ) {
            it.header("Authorization", "Basic Zm9vOmJhcg==")
        }
        assertThat(
            proxyDeps.proxyData.count(SyncedEventEntity::class).where(SyncedEventEntity.SLUG eq "foobar").get().value(),
            equalTo(0)
        )
        assertThat(resp.code, equalTo(200))
    }

    @Test
    fun `SetupDownstreamInit creates downstream device and returns init token`() = JavalinTest.test(app) { server, client ->
        var resp = client.post(
            "/proxyapi/v1/init", mapOf(
                "name" to "Dev 1"
            )
        ) {
            it.header("Authorization", "Basic Zm9vOmJhcg==")
        }

        val dev = proxyDeps.proxyDb.downstreamDeviceQueries.testSelectByName("Dev 1").executeAsOne()
        assertThat(resp.code, equalTo(200))
        val json = jacksonObjectMapper().readValue<MutableMap<Any, Any>>(resp.body!!.string())
        assertThat(json["url"], equalTo("http://URLNOTSET"))
        assertThat(json["token"], equalTo(dev.init_token))
        assertThat(json["handshake_version"], equalTo(1))
    }

    @Test
    fun `SetupDownstreamInitReady creates downstream device and returns api token`() = JavalinTest.test(app) { server, client ->
        var resp = client.post(
            "/proxyapi/v1/initready", mapOf(
                "name" to "Dev 1"
            )
        ) {
            it.header("Authorization", "Basic Zm9vOmJhcg==")
        }
        val dev = proxyDeps.proxyDb.downstreamDeviceQueries.testSelectByName("Dev 1").executeAsOne()
        assertThat(resp.code, equalTo(200))
        val json = jacksonObjectMapper().readValue<MutableMap<Any, Any>>(resp.body!!.string())
        assertThat(json["url"], equalTo("http://URLNOTSET"))
        assertThat(json["token"], equalTo(dev.api_token))
        assertThat(json["handshake_version"], equalTo(1))
    }

    @Test
    fun `SetupDownstreamRemove removes downstream device`() = JavalinTest.test(app) { server, client ->
        var resp = client.post(
            "/proxyapi/v1/initready", mapOf(
                "name" to "Dev 1"
            )
        ) {
            it.header("Authorization", "Basic Zm9vOmJhcg==")
        }
        val dev = proxyDeps.proxyDb.downstreamDeviceQueries.testSelectByName("Dev 1").executeAsOne()
        assertThat(resp.code, equalTo(200))

        resp = client.post(
            "/proxyapi/v1/remove", mapOf(
                "uuid" to dev.uuid
            )
        ) {
            it.header("Authorization", "Basic Zm9vOmJhcg==")
        }
        assertThat(resp.code, equalTo(200))
        assertThat(proxyDeps.proxyDb.downstreamDeviceQueries.testCountWithName("Dev 1").executeAsOne(), equalTo(0L))
    }
}