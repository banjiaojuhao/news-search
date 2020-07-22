package io.github.banjiaojuhao.search

import io.vertx.core.Vertx
import io.vertx.ext.web.client.WebClient
import io.vertx.kotlin.core.closeAwait
import io.vertx.kotlin.ext.web.client.sendAwait
import io.vertx.kotlin.ext.web.client.webClientOptionsOf
import kotlinx.coroutines.runBlocking
import java.util.*

private const val UA_Win_Chrome = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/83.0.4103.106 Safari/537.36"

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
    val currentWorkerId = UUID.randomUUID().toString()

    while (true) {
        val task = getTask(currentWorkerId)
        if (task == null) {
            println("can't get more task, exit")
            break
        }
        val resultList = arrayListOf<Pair<Int, String>>()
        for (book_id in task.taskCurrentNo..task.taskEndNo) {
            val response = webClient.getAbs("https://opac.lib.utsz.edu.cn/Search/searchdetail.jsp"
                + "?v_tablearray=bibliosm,serbibm,apabibibm,mmbibm,&v_curtable=bibliosm&v_recno="
                + book_id.toString().padStart(7, '0'))
                .sendAwait()
            if (response.statusCode() != 200) {
                println("invalid status code(${response.statusCode()}) for book: $book_id")
                break
            }
            val text = response.bodyAsString()
            resultList.add(book_id to text)

            // save into db every 20 requests
            if (book_id % 20 == 0) {

            }
        }
    }

    vertx.closeAwait()
    StoreConnection.close()
}
