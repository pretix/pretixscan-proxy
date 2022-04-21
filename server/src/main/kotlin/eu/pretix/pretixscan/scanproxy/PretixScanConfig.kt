package eu.pretix.pretixscan.scanproxy

import eu.pretix.libpretixsync.api.PretixApi
import eu.pretix.libpretixsync.config.ConfigStore
import org.json.JSONObject
import java.io.File
import java.util.prefs.Preferences


class PretixScanConfig(private var data_dir: String, private val eventSlug: String, private val subEvent: Long?) : ConfigStore {
    private val prefs = Preferences.userNodeForPackage(PretixScanConfig::class.java)

    private val PREFS_KEY_API_URL = "pretix_api_url"
    private val PREFS_KEY_API_KEY = "pretix_api_key"
    private val PREFS_KEY_API_DEVICE_ID = "pretix_api_device_id"
    private val PREFS_KEY_ORGANIZER_SLUG = "pretix_api_organizer_slug"
    private val PREFS_KEY_API_VERSION = "pretix_api_version"
    private val PREFS_KEY_DEVICE_NAME = "device_name"
    private val PREFS_KEY_LAST_SYNC = "last_sync"
    private val PREFS_KEY_LAST_FAILED_SYNC = "last_failed_sync"
    private val PREFS_KEY_LAST_FAILED_SYNC_MSG = "last_failed_sync_msg"
    private val PREFS_KEY_LAST_DOWNLOAD = "last_download"
    private val PREFS_KEY_LAST_STATUS_DATA = "last_status_data"
    private val PREFS_KEY_LAST_CLEANUP = "last_cleanup"
    private val PREFS_KEY_KNOWN_DEVICE_VERSION = "known_device_version"
    private val PREFS_KEY_KNOWN_DEVICE_INFO = "known_device_info"
    private val PREFS_KEY_KNOWN_PRETIX_VERSION = "known_pretix_version"
    private val PREFS_KEY_KNOWN_GATE_NAME = "known_gate_name"

    fun setDeviceConfig(
        url: String,
        key: String,
        orga_slug: String,
        device_id: Long
    ) {
        prefs.put(PREFS_KEY_API_URL, url)
        prefs.put(PREFS_KEY_API_KEY, key)
        prefs.putLong(PREFS_KEY_API_DEVICE_ID, device_id)
        prefs.put(PREFS_KEY_ORGANIZER_SLUG, orga_slug)
        prefs.remove(PREFS_KEY_LAST_DOWNLOAD)
        prefs.remove(PREFS_KEY_LAST_SYNC)
        prefs.remove(PREFS_KEY_LAST_FAILED_SYNC)
        prefs.remove(PREFS_KEY_LAST_CLEANUP)
        prefs.remove(PREFS_KEY_LAST_STATUS_DATA)
        prefs.remove(PREFS_KEY_DEVICE_NAME)
        prefs.flush()
    }

    fun wipe() {
        prefs.clear()
        val f = File(data_dir, PREFS_KEY_LAST_STATUS_DATA + ".json")
        if (f.exists()) {
            f.delete()
        }
        prefs.flush()
    }

    override fun isDebug(): Boolean {
        return false
    }

    override fun isConfigured(): Boolean {
        return prefs.get(PREFS_KEY_API_URL, null) != null && apiUrl.isNotEmpty()
    }

    override fun getApiUrl(): String {
        return prefs.get(PREFS_KEY_API_URL, "")
    }

    override fun getApiKey(): String {
        return prefs.get(PREFS_KEY_API_KEY, "")
    }

    override fun getLastDownload(): Long {
        return prefs.getLong(PREFS_KEY_LAST_DOWNLOAD + "_" + eventSlug, 0)
    }

    override fun getAutoSwitchRequested(): Boolean {
        return false
    }

    override fun setLastDownload(value: Long) {
        prefs.putLong(PREFS_KEY_LAST_DOWNLOAD + "_" + eventSlug, value)
        prefs.flush()
    }

    override fun getLastSync(): Long {
        return prefs.getLong(PREFS_KEY_LAST_SYNC + "_" + eventSlug, 0)
    }

    override fun setLastSync(value: Long) {
        prefs.putLong(PREFS_KEY_LAST_SYNC + "_" + eventSlug, value)
        prefs.flush()
    }

    override fun getLastFailedSync(): Long {
        return prefs.getLong(PREFS_KEY_LAST_FAILED_SYNC + "_" + eventSlug, 0)
    }

    override fun setLastFailedSync(value: Long) {
        prefs.putLong(PREFS_KEY_LAST_FAILED_SYNC + "_" + eventSlug, value)
        prefs.flush()
    }

    override fun getLastFailedSyncMsg(): String {
        return prefs.get(PREFS_KEY_LAST_FAILED_SYNC_MSG + "_" + eventSlug, "")
    }

    override fun getDeviceKnownGateName(): String {
        return prefs.get(PREFS_KEY_KNOWN_GATE_NAME, "")
    }

    override fun setDeviceKnownGateName(value: String?) {
        if (value == null) {
            prefs.remove(PREFS_KEY_KNOWN_GATE_NAME)
        } else {
            prefs.put(PREFS_KEY_KNOWN_GATE_NAME, value)
        }
        prefs.flush()
    }

    override fun setLastFailedSyncMsg(value: String?) {
        prefs.put(PREFS_KEY_LAST_FAILED_SYNC_MSG + "_" + eventSlug, value)
        prefs.flush()
    }

    override fun getOrganizerSlug(): String {
        return prefs.get(PREFS_KEY_ORGANIZER_SLUG, "")
    }

    fun setOrganizerSlug(value: String) {
        prefs.put(PREFS_KEY_ORGANIZER_SLUG, value)
        prefs.flush()
    }

    override fun getPosId(): Long {
        return 0;
    }

    override fun getApiVersion(): Int {
        return prefs.getInt(PREFS_KEY_API_VERSION, PretixApi.SUPPORTED_API_VERSION)
    }

    override fun getEventSlug(): String {
        return eventSlug
    }

    override fun getSyncCycleId(): String {
        return "cycle"
    }

    override fun getSubEventId(): Long? {
        return subEvent
    }

    override fun getDeviceKnownVersion(): Int {
        return prefs.getInt(PREFS_KEY_KNOWN_DEVICE_VERSION, 0)
    }

    override fun setDeviceKnownVersion(value: Int) {
        prefs.putInt(PREFS_KEY_KNOWN_DEVICE_VERSION, value)
        prefs.flush()
    }

    override fun getDeviceKnownInfo(): JSONObject {
        return JSONObject(prefs.get(PREFS_KEY_KNOWN_DEVICE_INFO, "{}") ?: "{}")
    }

    override fun setDeviceKnownInfo(value: JSONObject?) {
        prefs.put(PREFS_KEY_KNOWN_DEVICE_INFO, value?.toString() ?: "{}")
        prefs.flush()
    }

    override fun setKnownPretixVersion(value: Long) {
        prefs.putLong(PREFS_KEY_KNOWN_PRETIX_VERSION, value)
        prefs.flush()
    }

    override fun getKnownPretixVersion(): Long {
        return prefs.getLong(PREFS_KEY_KNOWN_PRETIX_VERSION, 0)
    }

    override fun getDeviceKnownName(): String {
        return prefs.get(PREFS_KEY_DEVICE_NAME, "")
    }

    override fun setDeviceKnownName(value: String?) {
        prefs.put(PREFS_KEY_DEVICE_NAME, value)
        prefs.flush()
    }

    override fun getLastCleanup(): Long {
        return prefs.getLong(PREFS_KEY_LAST_CLEANUP, 0)
    }

    override fun setLastCleanup(`val`: Long) {
        prefs.putLong(PREFS_KEY_LAST_CLEANUP, `val`)
        prefs.flush()
    }

}
