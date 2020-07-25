package io.github.banjiaojuhao.search

import io.github.banjiaojuhao.search.db.StoreConnection
import kotlinx.coroutines.runBlocking


fun main(args: Array<String>) = runBlocking<Unit> {
    if (args.isNotEmpty() && args[0] == "index") {
        println("making index")
        StoreConnection().use {
            makeIndex(it)
        }
        println("finish making index")
    }
    runSearchServer()
}

