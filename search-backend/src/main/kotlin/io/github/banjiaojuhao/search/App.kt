package io.github.banjiaojuhao.search

import io.github.banjiaojuhao.search.db.StoreConnection
import io.github.banjiaojuhao.search.db.WebPageTable
import io.vertx.core.Vertx
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Router
import io.vertx.ext.web.handler.StaticHandler
import io.vertx.kotlin.core.http.httpServerOptionsOf
import io.vertx.kotlin.core.http.listenAwait
import io.vertx.kotlin.core.json.JsonArray
import io.vertx.kotlin.core.json.jsonObjectOf
import io.vertx.kotlin.coroutines.awaitEvent
import kotlinx.coroutines.runBlocking
import org.apache.lucene.analysis.cn.smart.SmartChineseAnalyzer
import org.apache.lucene.document.*
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.store.FSDirectory
import org.jetbrains.exposed.sql.select
import org.jsoup.Jsoup
import sun.misc.Signal
import java.nio.file.Paths
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter


private val dbConnection = StoreConnection()

fun main(args: Array<String>) = runBlocking<Unit> {
    println("make index?[y/n]")
    if (readLine() == "y") {
        makeIndex()
    }

    val vertx = Vertx.vertx()

    val httpServerOptionsOf = httpServerOptionsOf(
        compressionSupported = true
    )
    val httpServer = vertx.createHttpServer(httpServerOptionsOf)
    val router = Router.router(vertx)
    httpServer.requestHandler(router)

    router.route().handler(StaticHandler.create())

    val errorEmptyQuery = jsonObjectOf(
        "code" to 1,
        "msg" to "non query string"
    ).toString()
    router.get("/query").handler { routingContext ->
        val query = routingContext.queryParams()["q"]
        if (query == null || query == "") {
            routingContext.response().end(errorEmptyQuery)
        } else {
            vertx.executeBlocking<JsonObject>({
                it.complete(query(query))
            }) {
                routingContext.response().end(it.result().toBuffer())
            }
        }

    }

    httpServer.listenAwait(8080)

    awaitEvent<Unit> { handler ->
        Signal.handle(Signal("INT")) {
            handler.handle(Unit)
        }
    }
    println("stop program")

    dbConnection.close()
}


private val formatterYMDHM = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
private suspend fun makeIndex() {
    val indexDir = FSDirectory.open(Paths.get("index"))
    val analyzer = SmartChineseAnalyzer()
    val indexWriterConfig = IndexWriterConfig(analyzer)
    indexWriterConfig.openMode = IndexWriterConfig.OpenMode.CREATE
    IndexWriter(indexDir, indexWriterConfig).use { indexWriter ->
        dbConnection.execute {
            WebPageTable.slice(WebPageTable.articleId, WebPageTable.webPage).select {
                WebPageTable.state eq 2
            }.toList()
        }.forEach {
            val doc = Document()
            val articleId = it[WebPageTable.articleId]

            val dom = Jsoup.parse(it[WebPageTable.webPage])
            val title = dom.select("div.title").text()
            val tip = dom.select("div.tip > span.item")
            if (tip.size != 2) {
                println("error in article $articleId")
                return@forEach
            }
            val issueTimeText = tip[0].text().substringAfter("：").trim()
            val issueTime = LocalDateTime.parse(issueTimeText, formatterYMDHM)
                .toEpochSecond(ZoneOffset.ofHours(8))
            val viewed = tip[1].text().trim().toInt()
            val content = dom.select("div.detail").text()

            doc.add(StoredField("article_id", articleId))
            doc.add(TextField("title", title.reader()))
            doc.add(TextField("content", content.reader()))
            doc.add(LongPoint("issue_time", issueTime))
            doc.add(IntPoint("view", viewed))
            indexWriter.addDocument(doc)
            println("finish index article $articleId")
        }
    }
}

private fun query(queryText: String): JsonObject {
    val fsDirectory = FSDirectory.open(Paths.get("index"))
    val reader = DirectoryReader.open(fsDirectory)
    val indexSearcher = IndexSearcher(reader)
    val parser = MultiFieldQueryParser(arrayOf("title", "content"), SmartChineseAnalyzer())
    val query = parser.parse(queryText)
    val topDocs = indexSearcher.search(query, 100)
    val resultList = topDocs.scoreDocs.map {
        val document = indexSearcher.doc(it.doc)
        val title = document["title"]
        val articleId = document["article_id"]
        jsonObjectOf("title" to title, "article_id" to articleId)
    }
    return jsonObjectOf(
        "code" to 0,
        "msg" to "ok",
        "data" to JsonArray(resultList)
    )
}
