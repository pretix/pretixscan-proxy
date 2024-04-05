package eu.pretix.pretixscan.scanproxy.db

import eu.pretix.libpretixsync.utils.HashUtils
import io.requery.Entity
import io.requery.Key
import io.requery.Persistable

@Entity
interface DownstreamDevice : Persistable {
    @get:Key
    var uuid: String

    var init_token: String?
    var api_token: String?
    var added_datetime: String?
    var name: String?
}
