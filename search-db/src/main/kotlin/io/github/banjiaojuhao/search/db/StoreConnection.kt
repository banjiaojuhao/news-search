package io.github.banjiaojuhao.search.db

import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.Closeable
import java.sql.DriverManager
import java.util.concurrent.Executors

class StoreConnection : Closeable {
    private val db by lazy {
        val url = "jdbc:mysql://localhost:3306/news_search"
        val dbUser = "root"
        val dbPassword = "pwdofmysql8!"
        Database
            .connect({ DriverManager.getConnection(url, dbUser, dbPassword) })
            .apply {
                transaction(db = this) {
                    SchemaUtils.createMissingTablesAndColumns(
                        SpiderTaskTable, WebPageTable
                    )
                }
                Unit
            }
    }
    private val context = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    suspend fun <T> execute(task: Transaction.() -> T): T =
        withContext(context) {
            transaction(db = db) {
                task()
            }
        }

    override fun close() {
        context.close()
    }
}
