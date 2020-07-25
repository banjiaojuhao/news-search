package io.github.banjiaojuhao.search

import io.github.banjiaojuhao.search.db.StoreConnection
import io.github.banjiaojuhao.search.db.WebPageTable
import org.apache.lucene.analysis.cn.smart.SmartChineseAnalyzer
import org.apache.lucene.document.*
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.store.FSDirectory
import org.jetbrains.exposed.sql.select
import org.jsoup.Jsoup
import java.nio.file.Paths
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val formatterYMDHM = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
internal suspend fun makeIndex(dbConnection: StoreConnection) {
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
            doc.add(TextField("title", title, Field.Store.YES))
            doc.add(TextField("content", content.reader()))
            doc.add(LongPoint("issue_time", issueTime))
            doc.add(IntPoint("view", viewed))
            indexWriter.addDocument(doc)
            println("finish index article $articleId")
        }
    }
}