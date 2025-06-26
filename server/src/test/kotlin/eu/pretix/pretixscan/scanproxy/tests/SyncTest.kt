package eu.pretix.pretixscan.scanproxy.tests

import eu.pretix.libpretixsync.api.PermissionDeniedApiException
import eu.pretix.pretixscan.scanproxy.proxyDeps
import eu.pretix.pretixscan.scanproxy.syncAllEvents
import eu.pretix.pretixscan.scanproxy.tests.test.FakePretixApi
import eu.pretix.pretixscan.scanproxy.tests.utils.BaseDatabaseTest
import io.javalin.testtools.JavalinTest
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Before
import org.junit.Test


class SyncTest : BaseDatabaseTest() {
    @Before
    fun setUpFakes() {
        proxyDeps.proxyDb.syncedEventQueries.insert(
            slug = "demo",
        )
    }

    private fun response(code: Int, data: JSONObject): Response {
        return Response.Builder()
            .request(Request.Builder().url("https://foo").build())
            .code(code)
            .message("OK")
            .protocol(Protocol.HTTP_1_1)
            .body(ResponseBody.create("application/json".toMediaTypeOrNull(), data.toString()))
            .build()
    }

    @Test
    fun `Event without permission is removed from sync`() = JavalinTest.test(app) { server, client ->
        assertThat(proxyDeps.proxyDb.syncedEventQueries.testCountAll().executeAsOne(), equalTo(1L))

        val api = proxyDeps.pretixApi as FakePretixApi
        api.postResponses.add { // device update version
            api.ApiResponse(JSONObject(), response(200, JSONObject()))
        }
        api.fetchResponses.add { // device info, error will be ignored (old pretix version assumed)
            val respdata = JSONObject("{\n" +
                    "  \"device\": {\n" +
                    "    \"organizer\": \"foo\",\n" +
                    "    \"device_id\": 5,\n" +
                    "    \"unique_serial\": \"HHZ9LW9JWP390VFZ\",\n" +
                    "    \"api_token\": \"1kcsh572fonm3hawalrncam4l1gktr2rzx25a22l8g9hx108o9oi0rztpcvwnfnd\",\n" +
                    "    \"name\": \"Bar\",\n" +
                    "    \"gate\": {\n" +
                    "      \"id\": 3,\n" +
                    "      \"name\": \"South entrance\"\n" +
                    "    }\n" +
                    "  },\n" +
                    "  \"server\": {\n" +
                    "    \"version\": {\n" +
                    "      \"pretix\": \"3.6.0.dev0\",\n" +
                    "      \"pretix_numeric\": 30060001000\n" +
                    "    }\n" +
                    "  },\n" +
                    "  \"medium_key_sets\": []\n" +
                    "}")
            api.ApiResponse(respdata, response(200, respdata))
        }
        api.fetchResponses.add { // subevent list
            val respdata = JSONObject()
            respdata.put("results", JSONArray())
            respdata.put("count", 0)
            respdata.put("next", JSONObject.NULL)
            api.ApiResponse(respdata, response(200, respdata))
        }
        api.fetchResponses.add { // event detail
            throw PermissionDeniedApiException("permission denied")
        }

        syncAllEvents(true)

        assertThat(proxyDeps.proxyDb.syncedEventQueries.testCountAll().executeAsOne(), equalTo(0L))
    }
}
