package eu.pretix.pretixscan.scanproxy

import eu.pretix.libpretixsync.api.DefaultHttpClientFactory
import eu.pretix.libpretixsync.api.HttpClientFactory
import eu.pretix.libpretixsync.api.PretixApi
import eu.pretix.pretixscan.scanproxy.db.JvmLocalCacheFactory
import eu.pretix.pretixscan.scanproxy.sqldelight.proxy.ProxyDatabase
import eu.pretix.pretixscan.scanproxy.sqldelight.sync.SyncDatabase
import net.harawata.appdirs.AppDirsFactory


lateinit var proxyDeps: ProxyDependencies

fun isProxyDepsInitialized(): Boolean {
    return ::proxyDeps.isInitialized
}

abstract class ProxyDependencies {
    abstract val db: SyncDatabase
    abstract val proxyDb: ProxyDatabase
    abstract val dataDir: String

    open val connectivityHelper = ConnectivityHelper(System.getProperty("pretixscan.autoOfflineMode", "off"))
    open val httpClientFactory: HttpClientFactory by lazy {
        DefaultHttpClientFactory()
    }
    open val configStore: ProxyScanConfig by lazy {
        PretixScanConfig(proxyDeps.dataDir)
    }
    open val fileStorage: ProxyFileStorage by lazy {
        ProxyFileStorage()
    }
    open val pretixApi: PretixApi by lazy {
        PretixApi.fromConfig(configStore, proxyDeps.httpClientFactory, null)
    }

    open fun init() {}
}

class ServerProxyDependencies: ProxyDependencies() {
    private val appDirs = AppDirsFactory.getInstance()!!
    override val dataDir = appDirs.getUserDataDir("pretixscanproxy", "1", "pretix")

    override val proxyDb: ProxyDatabase by lazy {
        JvmLocalCacheFactory().getProxyDataSource()
    }

    override val db: SyncDatabase by lazy {
        JvmLocalCacheFactory().getSyncDataSource()
    }
}
