package io.github.banjiaojuhao.search

import io.github.banjiaojuhao.search.db.SpiderTaskTable
import io.github.banjiaojuhao.search.db.StoreConnection
import io.github.banjiaojuhao.search.db.WebPageTable
import jdk.javadoc.internal.tool.Main.execute
import org.jetbrains.exposed.sql.*

private const val REDISPATCH_TIME = 3600_000


data class Task(val topicId: String,var nextFetchOffset: Int)


suspend fun getTask(dbConnection: StoreConnection, currentWorkerId: String): Task? {
    return dbConnection.execute {
        val needInit = SpiderTaskTable.selectAll().count() == 0
            && WebPageTable.selectAll().count() == 0
        if (needInit) {
            val topicIdList = listOf("116", "75", "74", "81", "78")
            SpiderTaskTable.batchInsert(topicIdList) {
                this[SpiderTaskTable.topicId] = it
            }
        }
        SpiderTaskTable.update({
            (SpiderTaskTable.lastFetchTime less (System.currentTimeMillis() - REDISPATCH_TIME))
                .or(SpiderTaskTable.workerId eq "")
        }, limit = 1) {
            it[workerId] = currentWorkerId
            it[lastFetchTime] = System.currentTimeMillis()
        }
        val task = SpiderTaskTable.select {
            (SpiderTaskTable.workerId eq currentWorkerId) and (
                SpiderTaskTable.nextFetchOffset greaterEq 0
                )
        }.firstOrNull()
        if (task == null) {
            null
        } else {
            Task(task[SpiderTaskTable.topicId], task[SpiderTaskTable.nextFetchOffset])
        }

    }
}

suspend fun saveResult(dbConnection: StoreConnection, currentWorkerId: String, nextFetchOffset: Int, result: Collection<String>) {
    dbConnection.execute {
        WebPageTable.batchInsert(result, ignore = true) {
            this[WebPageTable.articleId] = it
            this[WebPageTable.webPage] = ""
        }
        SpiderTaskTable.update({
            SpiderTaskTable.workerId eq currentWorkerId
        }) {
            it[lastFetchTime] = System.currentTimeMillis()
            it[this.nextFetchOffset] = nextFetchOffset
        }
        SpiderTaskTable.deleteWhere {
            SpiderTaskTable.nextFetchOffset eq -1
        }
    }
}