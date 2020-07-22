package io.github.banjiaojuhao.search.db

import org.jetbrains.exposed.dao.IntIdTable

object SpiderTaskTable : IntIdTable() {
    override val tableName: String
        get() = "spider_task_table"
    val lastHitNo = integer("last_hit_no")
    val currentFetchNo = integer("current_fetch_no")
    val endFetchNo = integer("end_fetch_no")

    val lastFetchTime = long("last_fetch_time")
    val workerId = text("worker_id")
}

object WebPageTable : IntIdTable() {
    override val tableName: String
        get() = "web_page_table"
    val bookId = integer("book_id").uniqueIndex()
    val webPage = text("web_page")
}

