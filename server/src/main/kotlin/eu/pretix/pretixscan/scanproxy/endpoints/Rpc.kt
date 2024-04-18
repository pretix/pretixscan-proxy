package eu.pretix.pretixscan.scanproxy.endpoints

import eu.pretix.libpretixsync.check.AsyncCheckProvider
import eu.pretix.libpretixsync.check.CheckException
import eu.pretix.libpretixsync.check.OnlineCheckProvider
import eu.pretix.libpretixsync.check.TicketCheckProvider
import eu.pretix.libpretixsync.db.Answer
import eu.pretix.libpretixsync.db.Question
import eu.pretix.libpretixsync.db.QuestionOption
import eu.pretix.pretixscan.scanproxy.PretixScanConfig
import eu.pretix.pretixscan.scanproxy.ProxyFileStorage
import eu.pretix.pretixscan.scanproxy.db.DownstreamDeviceEntity
import eu.pretix.pretixscan.scanproxy.db.SyncedEventEntity
import eu.pretix.pretixscan.scanproxy.proxyDeps
import io.javalin.http.Context
import io.javalin.http.Handler
import org.slf4j.LoggerFactory
import java.util.*


fun getCheckProvider(): TicketCheckProvider {
    if (proxyDeps.connectivityHelper.isOffline) {
        return AsyncCheckProvider(
            proxyDeps.configStore,
            proxyDeps.syncData,
        )
    } else {
        return OnlineCheckProvider(
            proxyDeps.configStore,
            proxyDeps.httpClientFactory,
            proxyDeps.syncData,
            proxyDeps.fileStorage,
        )
    }
}


object StatusEndpoint : Handler {
    override fun handle(ctx: Context) {
        val acp = getCheckProvider()
        registerEventIfNotExists(ctx.pathParam("event"))
        try {
            ctx.json(acp.status(ctx.pathParam("event"), ctx.pathParam("list").toLong())!!)
        } catch (e: CheckException) {
            ctx.status(400).json(mapOf("title" to e.message))
        }
    }
}

class CheckInputAnswer(var question: Question, var value: String, var options: List<QuestionOption>? = null) {
    fun toAnswer(): Answer {
        return Answer(question, value, options)
    }
}
data class CheckInput(
    val ticketid: String,
    val answers: List<CheckInputAnswer>?,
    val ignore_unpaid: Boolean,
    val with_badge_data: Boolean,
    val type: String?,
    val source_type: String?
)

object CheckEndpoint : JsonBodyHandler<CheckInput>(CheckInput::class.java) {
    override fun handle(ctx: Context, body: CheckInput) {
        val LOG = LoggerFactory.getLogger("eu.pretix.pretixscan.scanproxy.endpoints.CheckEndpoint")

        registerEventIfNotExists(ctx.pathParam("event"))
        val acp = getCheckProvider()
        val startedAt = System.currentTimeMillis()
        try {
            val type = TicketCheckProvider.CheckInType.valueOf((body.type ?: "entry").uppercase(Locale.getDefault()))
            val result = acp.check(
                mapOf(ctx.pathParam("event") to ctx.pathParam("list").toLong()),
                body.ticketid,
                body.source_type ?: "barcode",
                body.answers?.map { it.toAnswer() },
                body.ignore_unpaid,
                body.with_badge_data,
                type
            )
            val requiredAnswers = result.requiredAnswers
            if (requiredAnswers != null) {
                val questions = requiredAnswers.map { it.question }
                for (q in questions) {
                    q.resolveDependency(questions)
                }
            }
            val device: DownstreamDeviceEntity = ctx.attribute("device")!!
            LOG.info("Scanned ticket '${body.ticketid}' result '${result.type}' time '${(System.currentTimeMillis() - startedAt) / 1000f}s' device '${device.name}' provider '${acp.javaClass.simpleName}'")
            ctx.json(result)
            if (acp is OnlineCheckProvider) {
                if (result.type == TicketCheckProvider.CheckResult.Type.ERROR) {
                    proxyDeps.connectivityHelper.recordError()
                } else {
                    proxyDeps.connectivityHelper.recordSuccess(System.currentTimeMillis() - startedAt)
                }
            }
        } catch (e: CheckException) {
            proxyDeps.connectivityHelper.recordError()
            ctx.status(400).json(mapOf("title" to e.message))
        }
    }
}

data class MultiCheckInput(
    val events_and_checkin_lists: Map<String, Long>,
    val ticketid: String,
    val answers: List<CheckInputAnswer>?,
    val ignore_unpaid: Boolean,
    val with_badge_data: Boolean,
    val type: String?,
    val source_type: String?
)

object MultiCheckEndpoint : JsonBodyHandler<MultiCheckInput>(MultiCheckInput::class.java) {
    override fun handle(ctx: Context, body: MultiCheckInput) {
        val LOG = LoggerFactory.getLogger("eu.pretix.pretixscan.scanproxy.endpoints.MultiCheckEndpoint")

        for (event in body.events_and_checkin_lists.keys) {
            registerEventIfNotExists(event)
        }

        val acp = getCheckProvider()
        val startedAt = System.currentTimeMillis()
        try {
            val type = TicketCheckProvider.CheckInType.valueOf((body.type ?: "entry").uppercase(Locale.getDefault()))
            val result = acp.check(
                body.events_and_checkin_lists,
                body.ticketid,
                body.source_type ?: "barcode",
                body.answers?.map { it.toAnswer() },
                body.ignore_unpaid,
                body.with_badge_data,
                type
            )
            val requiredAnswers = result.requiredAnswers
            if (requiredAnswers != null) {
                val questions = requiredAnswers.map { it.question }
                for (q in questions) {
                    q.resolveDependency(questions)
                }
            }
            val device: DownstreamDeviceEntity = ctx.attribute("device")!!
            LOG.info("Scanned ticket '${body.ticketid}' result '${result.type}' time '${(System.currentTimeMillis() - startedAt) / 1000f}s' device '${device.name}' provider '${acp.javaClass.simpleName}'")
            ctx.json(result)
            if (acp is OnlineCheckProvider) {
                if (result.type == TicketCheckProvider.CheckResult.Type.ERROR) {
                    proxyDeps.connectivityHelper.recordError()
                } else {
                    proxyDeps.connectivityHelper.recordSuccess(System.currentTimeMillis() - startedAt)
                }
            }
        } catch (e: CheckException) {
            proxyDeps.connectivityHelper.recordError()
            ctx.status(400).json(mapOf("title" to e.message))
        }
    }
}

data class SearchInput(
    val query: String,
    val page: Int
)

object SearchEndpoint : JsonBodyHandler<SearchInput>(SearchInput::class.java) {
    override fun handle(ctx: Context, body: SearchInput) {
        val acp = getCheckProvider()
        registerEventIfNotExists(ctx.pathParam("event"))
        try {
            ctx.json(acp.search(
                mapOf(ctx.pathParam("event") to ctx.pathParam("list").toLong()),
                body.query, body.page
            ))
        } catch (e: CheckException) {
            proxyDeps.connectivityHelper.recordError()
            ctx.status(400).json(mapOf("title" to e.message))
        }
    }
}

data class MultiSearchInput(
    val events_and_checkin_lists: Map<String, Long>,
    val query: String,
    val page: Int
)

object MultiSearchEndpoint : JsonBodyHandler<MultiSearchInput>(MultiSearchInput::class.java) {
    override fun handle(ctx: Context, body: MultiSearchInput) {
        for (event in body.events_and_checkin_lists.keys) {
            registerEventIfNotExists(event)
        }
        val acp = getCheckProvider()
        try {
            ctx.json(acp.search(
                body.events_and_checkin_lists,
                body.query,
                body.page
            ))
        } catch (e: CheckException) {
            proxyDeps.connectivityHelper.recordError()
            ctx.status(400).json(mapOf("title" to e.message))
        }
    }
}
