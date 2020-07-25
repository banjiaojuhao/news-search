package io.github.banjiaojuhao.search

import org.apache.lucene.analysis.cn.smart.SmartChineseAnalyzer
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.store.FSDirectory
import java.nio.file.Paths

internal fun query(queryText: String): List<SearchResult> {
    val fsDirectory = FSDirectory.open(Paths.get("index"))
    val reader = DirectoryReader.open(fsDirectory)
    val indexSearcher = IndexSearcher(reader)
    val parser = MultiFieldQueryParser(arrayOf("title", "content"), SmartChineseAnalyzer())
    val query = parser.parse(queryText)
    val maxResultCount = 100
    val topDocs = indexSearcher.search(query, maxResultCount)
    return topDocs.scoreDocs.map {
        val document = indexSearcher.doc(it.doc)
        val title = document["title"]
        val articleId = document["article_id"]
        SearchResult(title, articleId)
    }
}

data class SearchResult(val title: String, val articleId: String)