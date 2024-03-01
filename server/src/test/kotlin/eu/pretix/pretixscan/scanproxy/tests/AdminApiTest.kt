package eu.pretix.pretixscan.scanproxy.tests

import io.javalin.testtools.JavalinTest
import org.hamcrest.CoreMatchers.either
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test

class AdminApiTest: BaseDatabaseTest() {

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
                it.header("Foo", "bar")
            }.code, equalTo(401))
            assertThat(client.get(u) {
                it.header("Authorization", "Basic Zm9vOmJhcg==")
            }.code, either(equalTo(405)).or(equalTo(400)).or(equalTo(200)).or(equalTo(404)))
        }
    }
}