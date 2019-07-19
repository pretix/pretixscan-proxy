package eu.pretix.pretixscan.scanproxy.db

import io.requery.Entity
import io.requery.Generated
import io.requery.Key
import io.requery.Persistable

@Entity
interface SyncedEvent : Persistable {
    var slug: String
}