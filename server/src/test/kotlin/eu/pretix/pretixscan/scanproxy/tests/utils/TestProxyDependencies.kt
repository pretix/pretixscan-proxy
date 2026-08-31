package eu.pretix.pretixscan.scanproxy.tests.utils

import eu.pretix.libpretixsync.api.HttpClientFactory
import eu.pretix.libpretixsync.api.PretixApi
import eu.pretix.libpretixsync.api.RateLimitInterceptor
import eu.pretix.pretixscan.scanproxy.ProxyDependencies
import eu.pretix.pretixscan.scanproxy.ProxyScanConfig
import eu.pretix.pretixscan.scanproxy.tests.test.FakePretixApi
import okhttp3.OkHttpClient
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.io.path.absolutePathString

class TestProxyDependencies : ProxyDependencies() {

    override val dataDir: String by lazy {
        Files.createTempDirectory("proxytests").absolutePathString()
    }

    override val httpClientFactory: HttpClientFactory by lazy {
        object : HttpClientFactory {
            override fun buildClient(ignore_ssl: Boolean): OkHttpClient {
                return OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .addInterceptor(RateLimitInterceptor())
                    .build()
            }
        }
    }

    override val configStore: ProxyScanConfig by lazy {
        TestConfigStore()
    }

    override val pretixApi: PretixApi by lazy {
        FakePretixApi()
    }

    override fun init() {
        super.init()
        System.setProperty("pretixscan.adminauth", "foo:bar")
    }
}
