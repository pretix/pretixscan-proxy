package eu.pretix.pretixscan.scanproxy

import eu.pretix.libpretixsync.sync.SyncManager
import org.slf4j.LoggerFactory

interface ConnectivityChangedListener {
    fun onConnectivityChanged(isOffline: Boolean)
}

class ConnectivityHelper(val autoOfflineMode: String) : SyncManager.CheckConnectivityFeedback {
    private val resultHistory = mutableListOf<Long?>()
    private val HISTORY_SIZE = 5
    private val MAX_ERRORS_IN_HISTORY = 2
    private val listeners = mutableListOf<ConnectivityChangedListener>()
    private var hardOffline = false
    private val LOG = LoggerFactory.getLogger("eu.pretix.pretixscan.scanproxy.ConnectivtyHelper")

    var isOffline = when (autoOfflineMode) {
        "off" -> true
        "1s", "2s", "3s", "5s", "10s", "15s", "20s", "errors", "on" -> false
        else -> true
    }

    init {
        if (isOffline) {
            LOG.info("Starting in offline mode")
        } else {
            LOG.info("Starting in online mode")
        }
    }

    private fun ensureHistorySize() {
        while (resultHistory.size > HISTORY_SIZE) {
            resultHistory.removeAt(0)
        }
    }

    fun resetHistory() {
        resultHistory.clear()
    }

    private fun checkConditions() {
        val maxDuration = when (autoOfflineMode) {
            "off" -> return
            "1s" -> 1000
            "2s" -> 2000
            "3s" -> 3000
            "5s" -> 5000
            "10s" -> 10000
            "15s" -> 15000
            "20s" -> 20000
            "errors" -> 3600000
            "on" -> 3600000
            else -> return
        }

        if (isOffline) {
            val switchToOnline = !hardOffline && (resultHistory.size == 0 || (resultHistory.count { it == null } == 0 && resultHistory.size >= MAX_ERRORS_IN_HISTORY && resultHistory.filterNotNull().toLongArray().average() < maxDuration))
            if (switchToOnline) {
                LOG.info("Switching to online mode (history: $resultHistory)")
                isOffline = false
                this.listeners.forEach { it.onConnectivityChanged(isOffline) }
            }
        } else {
            val switchToOffline = hardOffline || resultHistory.count { it == null } >= MAX_ERRORS_IN_HISTORY || (resultHistory.size >= MAX_ERRORS_IN_HISTORY && resultHistory.filterNotNull().toLongArray().average() >= maxDuration)
            if (switchToOffline) {
                LOG.info("Switching to offline mode (history: $resultHistory)")
                isOffline = true
                this.listeners.forEach { it.onConnectivityChanged(isOffline) }
            }
        }
    }

    override fun recordSuccess(durationInMillis: Long) {
        resultHistory.add(durationInMillis)
        ensureHistorySize()
        checkConditions()
    }

    override fun recordError() {
        resultHistory.add(null)
        ensureHistorySize()
        checkConditions()
    }

    fun setHardOffline(hardOffline: Boolean) {
        this.hardOffline = hardOffline
        checkConditions()
    }

    fun addListener(listener: ConnectivityChangedListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: ConnectivityChangedListener) {
        listeners.remove(listener)
    }
}
