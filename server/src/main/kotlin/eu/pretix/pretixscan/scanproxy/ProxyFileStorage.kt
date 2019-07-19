package eu.pretix.pretixscan.scanproxy

import eu.pretix.libpretixsync.sync.FileStorage
import java.io.File
import java.io.OutputStream

class ProxyFileStorage : FileStorage {

    fun getDir(): File {
        val dir = File(Server.dataDir, "dbcache")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    override fun contains(filename: String): Boolean {
        return File(getDir(), filename).exists()
    }

    fun getFile(filename: String): File {
        return File(getDir(), filename)
    }

    override fun writeStream(filename: String): OutputStream? {
        return File(getDir(), filename).outputStream()
    }

    override fun delete(filename: String) {
        File(getDir(), filename).delete()
    }
}

