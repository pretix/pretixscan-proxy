package eu.pretix.pretixscan.scanproxy.db

import eu.pretix.pretixscan.scanproxy.proxyDeps
import java.io.File

internal fun getSyncDatabasePath(): File {
    val dataDir = proxyDeps.dataDir
    // make sure the path exists so we can later create files in it
    File(dataDir).mkdirs()
    val dbFile = File("$dataDir/sync.sqlite")
    return dbFile
}

internal fun getProxyDatabasePath(): File {
    val dataDir = proxyDeps.dataDir
    // make sure the path exists so we can later create files in it
    File(dataDir).mkdirs()
    val dbFile = File("$dataDir/proxy.sqlite")
    return dbFile
}

internal fun getUserDataDir(): File {
    val dataDir = proxyDeps.dataDir
    // make sure the path exists so we can later create files in it
    File(dataDir).mkdirs()
    return File(dataDir)
}
