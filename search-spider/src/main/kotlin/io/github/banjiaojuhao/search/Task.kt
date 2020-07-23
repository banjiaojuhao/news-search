package io.github.banjiaojuhao.search

import io.github.banjiaojuhao.search.db.SpiderTaskTable
import io.github.banjiaojuhao.search.db.WebPageTable
import org.jetbrains.exposed.sql.*

private const val END_BOOK_ID = 4700000
private const val REDISPATCH_TIME = 3600_000


data class Task(var nextFetchNo: Int, var endNo: Int)


suspend fun getTask(currentWorkerId: String): Task? {
    return StoreConnection.execute {
        val needInit = SpiderTaskTable.selectAll().count() == 0
            && WebPageTable.selectAll().count() == 0
        if (needInit) {
            SpiderTaskTable.insert {
                it[lastHitNo] = -1
                it[nextFetchNo] = 0
                it[endFetchNo] = END_BOOK_ID

                it[workerId] = currentWorkerId
                it[lastFetchTime] = 0
            }
            Task(0, END_BOOK_ID)
        } else {
            SpiderTaskTable.update({
                (SpiderTaskTable.lastFetchTime less (System.currentTimeMillis() - REDISPATCH_TIME))
                    .or(SpiderTaskTable.workerId eq "")
            }, limit = 1) {
                it[workerId] = currentWorkerId
                it[lastFetchTime] = System.currentTimeMillis()
            }
            val task = SpiderTaskTable.select {
                (SpiderTaskTable.workerId eq currentWorkerId) and (
                    SpiderTaskTable.nextFetchNo.lessEq(SpiderTaskTable.endFetchNo)
                    )
            }.firstOrNull()
            if (task == null) {
                null
            } else {
                Task(task[SpiderTaskTable.nextFetchNo], task[SpiderTaskTable.endFetchNo])
            }
        }
    }
}

suspend fun saveResult(currentWorkerId: String, nextFetchNo: Int, result: List<Pair<Int, String>>) {
    StoreConnection.execute {
        WebPageTable.batchInsert(result) {
            this[WebPageTable.newsId] = it.first
            this[WebPageTable.webPage] = it.second
        }
        SpiderTaskTable.update({
            SpiderTaskTable.workerId eq currentWorkerId
        }) {
            if (result.isNotEmpty()) {
                it[lastHitNo] = result.last().first
            }
            it[lastFetchTime] = System.currentTimeMillis()
            it[this.nextFetchNo] = nextFetchNo
        }
        SpiderTaskTable.deleteWhere {
            SpiderTaskTable.nextFetchNo.greater(SpiderTaskTable.endFetchNo)
        }
    }
}