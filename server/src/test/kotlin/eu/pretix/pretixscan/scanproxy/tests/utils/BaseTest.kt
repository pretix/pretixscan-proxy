package eu.pretix.pretixscan.scanproxy.tests.utils

import eu.pretix.pretixscan.scanproxy.Server
import eu.pretix.pretixscan.scanproxy.isProxyDepsInitialized
import eu.pretix.pretixscan.scanproxy.proxyDeps
import eu.pretix.pretixscan.scanproxy.tests.test.FakePretixApi
import org.junit.Before
import org.junit.BeforeClass

abstract class BaseTest {
    protected val app = Server.createApp()

    companion object {
        @BeforeClass
        @JvmStatic
        fun initializeDependencies() {
            if (!isProxyDepsInitialized()) {
                proxyDeps = TestProxyDependencies()
                proxyDeps.init()
            }
        }
    }

    @Before
    fun resetMocks() {
        (proxyDeps.pretixApi as FakePretixApi).reset()
    }
}