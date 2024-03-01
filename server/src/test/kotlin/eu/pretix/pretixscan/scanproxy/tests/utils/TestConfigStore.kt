package eu.pretix.pretixscan.scanproxy.tests.utils

import eu.pretix.libpretixsync.config.ConfigStore
import org.json.JSONObject

class TestConfigStore : ConfigStore {
    var configured = true

    override fun isDebug(): Boolean {
        return true
    }

    override fun isConfigured(): Boolean {
        return configured
    }

    override fun getApiVersion(): Int {
        return 1
    }

    override fun getApiUrl(): String {
        return "https://pretix.eu"
    }

    override fun getDeviceKnownName(): String {
        return "Device"
    }

    override fun setDeviceKnownName(value: String?) {
    }

    override fun getDeviceKnownGateName(): String {
        return "Gate"
    }

    override fun setDeviceKnownGateName(value: String?) {
    }

    override fun getDeviceKnownGateID(): Long {
        return 1
    }

    override fun setDeviceKnownGateID(value: Long?) {
    }

    override fun getDeviceKnownVersion(): Int {
        return 1
    }

    override fun setDeviceKnownVersion(value: Int) {
    }

    override fun getDeviceKnownInfo(): JSONObject {
        return JSONObject()
    }

    override fun setDeviceKnownInfo(value: JSONObject?) {
    }

    override fun getApiKey(): String {
        return "abc"
    }

    override fun getOrganizerSlug(): String {
        return "demo"
    }

    override fun getSyncCycleId(): String {
        return "1"
    }

    override fun getSynchronizedEvents(): List<String> {
        return listOf("demo")
    }

    override fun getSelectedSubeventForEvent(event: String?): Long {
        return 0
    }

    override fun getSelectedCheckinListForEvent(event: String?): Long {
        return 12
    }

    override fun getLastDownload(): Long {
        return 0
    }

    override fun setLastDownload(`val`: Long) {
    }

    override fun getLastSync(): Long {
        return 0
    }

    override fun setLastSync(`val`: Long) {
    }

    override fun getLastCleanup(): Long {
        return 0
    }

    override fun setLastCleanup(`val`: Long) {
    }

    override fun getLastFailedSync(): Long {
        return 0
    }

    override fun setLastFailedSync(`val`: Long) {
    }

    override fun getLastFailedSyncMsg(): String {
        return ""
    }

    override fun setLastFailedSyncMsg(`val`: String?) {
    }

    override fun getPosId(): Long {
        return 0
    }

    override fun setKnownPretixVersion(`val`: Long?) {
    }

    override fun getKnownPretixVersion(): Long {
        return 12
    }

    override fun getAutoSwitchRequested(): Boolean {
        return false
    }

    override fun getKnownLiveEventSlugs(): Set<String> {
        return setOf("demo")
    }

    override fun setKnownLiveEventSlugs(slugs: Set<String>?) {
    }
}