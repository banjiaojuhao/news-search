package io.github.banjiaojuhao.search

import io.github.banjiaojuhao.search.db.StoreConnection
import io.github.banjiaojuhao.search.db.WebPageTable
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.selectAll
import java.nio.file.Files
import java.nio.file.Path


fun main(args: Array<String>) = runBlocking<Unit> {
    if (args.contains("export")) {
        exportUrl()
        println("exported url into url.txt")
    }
    if (args.contains("index")) {
        println("making index")
        StoreConnection().use {
            makeIndex(it)
        }
        println("finish making index")
    }
    runSearchServer()
}

private suspend fun exportUrl() {
    StoreConnection().use {
        val urlList = it.execute {
            WebPageTable.slice(WebPageTable.articleId).selectAll().map { it[WebPageTable.articleId] }
        }.joinToString(separator = "\n") { "http://www.hitsz.edu.cn/article/view/id-$it.html" }
        Files.newBufferedWriter(Path.of("url.txt")).use {
            it.write(urlList)
        }
    }
}

