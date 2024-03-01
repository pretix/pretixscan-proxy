package eu.pretix.pretixscan.scanproxy.tests

import eu.pretix.pretixscan.scanproxy.Server
import eu.pretix.pretixscan.scanproxy.isProxyDepsInitialized
import eu.pretix.pretixscan.scanproxy.proxyDeps
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
}