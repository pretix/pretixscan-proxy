package eu.pretix.pretixscan.scanproxy.tests

import eu.pretix.pretixscan.scanproxy.Server
import io.javalin.testtools.JavalinTest
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test

class SampleTest: BaseDatabaseTest() {
    @Test
    fun `GET to fetch users returns list of users`() = JavalinTest.test(app) { server, client ->
        assertThat(client.get("/api/v1/version").code, equalTo(200))
    }

}