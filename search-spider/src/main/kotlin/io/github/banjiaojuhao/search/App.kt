package io.github.banjiaojuhao.search

import io.github.banjiaojuhao.search.db.StoreConnection
import io.github.banjiaojuhao.search.db.WebPageTable
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.ext.web.client.HttpRequest
import io.vertx.ext.web.client.HttpResponse
import io.vertx.ext.web.client.WebClient
import io.vertx.kotlin.core.closeAwait
import io.vertx.kotlin.ext.web.client.sendAwait
import io.vertx.kotlin.ext.web.client.webClientOptionsOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.update
import org.jsoup.Jsoup
import java.util.*

private const val UA_Win_Chrome = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/83.0.4103.106 Safari/537.36"

private val dbConnection = StoreConnection()

fun main(args: Array<String>) = runBlocking<Unit> {
    val vertx = Vertx.vertx()
    val webClientOptions = webClientOptionsOf(
        userAgent = UA_Win_Chrome,
        keepAlive = true,
        trustAll = true,
        verifyHost = false,
        ssl = true,
        tryUseCompression = true
//        , proxyOptions = proxyOptionsOf(host = "127.0.0.1", port = 8888)
    )
    val webClient = WebClient.create(vertx, webClientOptions)
    fetchList(webClient)
    fetchArticle(webClient)

    vertx.closeAwait()
    dbConnection.close()
}

private suspend fun fetchList(webClient: WebClient) {
    val currentWorkerId = UUID.randomUUID().toString()

    while (true) {
        val task = getTask(dbConnection, currentWorkerId)
        if (task == null) {
            println("can't get more task, exit")
            break
        }
        val resultList = hashSetOf<String>()
        var nextFetchOffset = task.nextFetchOffset
        while (true) {
            val request = webClient
                .getAbs("http://www.hitsz.edu.cn/article/id-${task.topicId}.html?maxPageItems=20&pager.offset=$nextFetchOffset")
                .timeout(10_000)
            val response = httpGet(request)
            if (response == null) {
                println("end program")
                break
            }
            if (response.statusCode() != 200) {
                println("invalid status code(${response.statusCode()}) for id, offset: ${task.topicId}, $nextFetchOffset")
                break
            }
            val text = response.bodyAsString()
            val dom = Jsoup.parse(text)
            val result = dom.select("a")
                .map { it.attr("href").substringAfter("/article/view/id-").substringBefore(".html") }
                .filter { it.length == 5 }
            if (result.isEmpty()) {
                nextFetchOffset = -1
                break
            }
            println("got ${result.size} article on [${task.topicId}, ${task.nextFetchOffset}]")
            resultList.addAll(result)

            nextFetchOffset += 20
            // save into db every 5 requests
            if (nextFetchOffset % 100 == 0) {
                saveResult(dbConnection, currentWorkerId, nextFetchOffset, resultList)
                println("saved ${resultList.size} article")
                resultList.clear()
            }
            delay(300L)
        }
        saveResult(dbConnection, currentWorkerId, nextFetchOffset, resultList)
        resultList.clear()
        println("finish task[${task.topicId}, ${task.nextFetchOffset}]")
    }
}

private suspend fun fetchArticle(webClient: WebClient) {
    while (true) {
        val taskList = dbConnection.execute {
            WebPageTable.select {
                WebPageTable.state eq 0
            }.limit(20).map { it[WebPageTable.articleId] }
        }
        if (taskList.isEmpty()) {
            println("finish all article")
            break
        }
        val resultList = arrayListOf<Pair<String, String>>()
        for (newsId in taskList) {
            val request = webClient.getAbs("http://www.hitsz.edu.cn/article/view/id-$newsId.html")
            val response = httpGet(request)
            if (response == null) {
                println("end program")
                break
            }
            if (response.statusCode() != 200) {
                println("invalid status code(${response.statusCode()}) for newsId: $newsId")
                break
            }
            val text = response.bodyAsString()
            resultList.add(newsId to text)
        }
        dbConnection.execute {
            for (pair in resultList) {
                WebPageTable.update({
                    WebPageTable.articleId eq pair.first
                }) {
                    it[this.webPage] = pair.second
                    it[this.state] = 2
                }
            }
        }
        println("saved ${resultList.size} article")
    }
}

private suspend fun httpGet(request: HttpRequest<Buffer>): HttpResponse<Buffer>? {
    var response: HttpResponse<Buffer>? = null
    for (i in 0..2) {
        try {
            val tmpRequest = request.copy()
            response = tmpRequest.sendAwait()
            break
        } catch (e: IllegalStateException) {
            if (e.message?.contains("Client is closed") == true) {
                println("end program")
                break
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    return response
}
