package io.github.banjiaojuhao.search.db

import org.jetbrains.exposed.dao.IntIdTable

object SpiderTaskTable : IntIdTable() {
    override val tableName: String
        get() = "spider_task_table"
    val topicId = varchar("topic_id", 5)
    val nextFetchOffset = integer("next_fetch_page").default(0)

    val lastFetchTime = long("last_fetch_time").default(0)
    val workerId = varchar("worker_id", 36).default("")
}

object WebPageTable : IntIdTable() {
    override val tableName: String
        get() = "web_page_table"
    val articleId = varchar("news_id", 5).uniqueIndex()
    val webPage = text("web_page")

    val lastFetchTime = long("last_fetch_time").default(0)

    // 0:init 1:fetching 2:finish 3:error
    val state = integer("state").default(0)
}

