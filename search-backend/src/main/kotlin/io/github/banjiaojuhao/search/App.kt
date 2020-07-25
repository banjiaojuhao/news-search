package io.github.banjiaojuhao.search

import io.github.banjiaojuhao.search.db.StoreConnection
import io.vertx.core.Vertx
import io.vertx.ext.web.Router
import io.vertx.ext.web.handler.StaticHandler
import io.vertx.kotlin.core.http.httpServerOptionsOf
import io.vertx.kotlin.core.http.listenAwait
import io.vertx.kotlin.coroutines.awaitEvent
import kotlinx.coroutines.runBlocking
import org.apache.lucene.analysis.cn.smart.SmartChineseAnalyzer
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.store.FSDirectory
import sun.misc.Signal
import java.nio.file.Paths


data class SearchResult(val title: String, val articleId: String)

internal val dbConnection = StoreConnection()

fun main(args: Array<String>) = runBlocking<Unit> {
    if (args.isNotEmpty() && args[0] == "index") {
        println("making index")
        makeIndex()
        println("finish making index")
    }

    val vertx = Vertx.vertx()

    val httpServerOptionsOf = httpServerOptionsOf(
        compressionSupported = true
    )
    val httpServer = vertx.createHttpServer(httpServerOptionsOf)
    val router = Router.router(vertx)
    httpServer.requestHandler(router)

    router.route().handler(StaticHandler.create())

    val template = this::class.java.getResourceAsStream("/webroot/google-result.html").use {
        it.readAllBytes().toString(Charsets.UTF_8)
    }

    router.get("/search").handler { routingContext ->
        val keyword = routingContext.queryParams()["q"]
        if (keyword == null || keyword == "") {
            routingContext.response()
                .setStatusCode(503)
                .end("invalid query string")
        } else {
            vertx.executeBlocking<String>({
                val resultHtml = query(keyword)
                    .joinToString(separator = "") {
                        """<div class="g">
                        <div class="rc">
                            <div class="r"><a href="http://www.hitsz.edu.cn/article/view/id-${it.articleId}.html"><br>
                                <h3 class="LC20lb DKV0Md">${it.title}</h3>
                            </a>
                            </div>
                            <div class="s">
                                <div>
                                </div>
                            </div>
                        </div>
                    </div>"""
                    }

                it.complete(resultHtml)
            }) {
                routingContext.response()
                    .end(template
                        .replace("<!--keyword-->", keyword)
                        .replace("<!--result-->", it.result()))
            }
        }

    }

    httpServer.listenAwait(8080)
    println("server started, visit: http://localhost:8080/")

    awaitEvent<Unit> { handler ->
        Signal.handle(Signal("INT")) {
            handler.handle(Unit)
        }
    }
    println("stop program")

    dbConnection.close()
}


private fun query(queryText: String): List<SearchResult> {
    val fsDirectory = FSDirectory.open(Paths.get("index"))
    val reader = DirectoryReader.open(fsDirectory)
    val indexSearcher = IndexSearcher(reader)
    val parser = MultiFieldQueryParser(arrayOf("title", "content"), SmartChineseAnalyzer())
    val query = parser.parse(queryText)
    val topDocs = indexSearcher.search(query, 100)
    return topDocs.scoreDocs.map {
        val document = indexSearcher.doc(it.doc)
        val title = document["title"]
        val articleId = document["article_id"]
        SearchResult(title, articleId)
    }
}
