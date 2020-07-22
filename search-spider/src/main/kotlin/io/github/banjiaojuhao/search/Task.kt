package io.github.banjiaojuhao.search

import io.github.banjiaojuhao.search.db.SpiderTaskTable
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

private const val END_BOOK_ID = 4700000
private const val REDISPATCH_TIME = 3600_000


data class Task(var taskCurrentNo: Int, var taskEndNo: Int)


suspend fun getTask(currentWorkerId: String): Task? {
    return StoreConnection.execute {
        val needInit = SpiderTaskTable.selectAll().count() == 0
        if (needInit) {
            SpiderTaskTable.insert {
                it[lastHitNo] = -1
                it[currentFetchNo] = 0
                it[endFetchNo] = END_BOOK_ID

                it[workerId] = currentWorkerId
                it[lastFetchTime] = 0
            }
            Task(0, END_BOOK_ID)
        } else {
            SpiderTaskTable.update({
                SpiderTaskTable.lastFetchTime less (System.currentTimeMillis() - REDISPATCH_TIME)
            }, limit = 1) {
                it[workerId] = currentWorkerId
                it[lastFetchTime] = System.currentTimeMillis()
            }
            val task = SpiderTaskTable.select {
                SpiderTaskTable.workerId eq currentWorkerId
            }.firstOrNull()
            if (task == null) {
                null
            } else {
                Task(task[SpiderTaskTable.currentFetchNo], task[SpiderTaskTable.endFetchNo])
            }
        }
    }
}