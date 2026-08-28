package eu.pretix.pretixscan.scanproxy.db

import net.harawata.appdirs.AppDirsFactory
import java.io.File

fun getUserDataFolder(): String {
    val appDirsInstance = AppDirsFactory.getInstance()!!
    return appDirsInstance.getUserDataDir("pretixscan", "2", "pretix")
}

fun getUserCacheFolder(): String {
    val appDirsInstance = AppDirsFactory.getInstance()!!
    return appDirsInstance.getUserCacheDir("pretixscan", "2", "pretix")
}

fun getLogDirectory(): String {
    val appDirsInstance = AppDirsFactory.getInstance()!!
    return appDirsInstance.getUserLogDir("pretixscan", "2", "pretix")
}

internal fun getSyncDatabasePath(): File {
    val dataDir = getUserDataFolder()
    // make sure the path exists so we can later create files in it
    File(dataDir).mkdirs()
    val dbFile = File("$dataDir/sync.sqlite")
    return dbFile
}

internal fun getProxyDatabasePath(): File {
    val dataDir = getUserDataFolder()
    // make sure the path exists so we can later create files in it
    File(dataDir).mkdirs()
    val dbFile = File("$dataDir/proxy.sqlite")
    return dbFile
}

internal fun getUserDataDir(): File {
    val dataDir = getUserDataFolder()
    // make sure the path exists so we can later create files in it
    File(dataDir).mkdirs()
    return File(dataDir)
}
