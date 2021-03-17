package eu.pretix.pretixscan.scanproxy.db

import io.requery.Entity
import io.requery.Generated
import io.requery.Key
import io.requery.Persistable

@Entity
interface DownstreamDevice : Persistable {
    @get:Key
    var uuid: String

    var init_token: String?
    var api_token: String?
    var added_datetime: String?
}