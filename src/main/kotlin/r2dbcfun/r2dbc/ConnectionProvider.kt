package r2dbcfun.r2dbc

import io.r2dbc.spi.Clob
import io.r2dbc.spi.R2dbcException
import io.r2dbc.spi.Row
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitSingle
import r2dbcfun.RepositoryException
import r2dbcfun.transaction.transaction

class ConnectionProvider(val connection: Connection) {
    constructor(connection: io.r2dbc.spi.Connection) : this(Connection(connection))

    suspend fun <T> transaction(function: suspend () -> T): T = connection.transaction(function)
}

class Connection(val connection: io.r2dbc.spi.Connection) : io.r2dbc.spi.Connection by connection {
    suspend fun executeSelect(
        parameterValues: Sequence<Any>,
        sql: String
    ): Result {
        val statement = try {
            parameterValues.foldIndexed(createStatement(sql))
            { idx, statement, property -> statement.bind(idx, property) }
        } catch (e: R2dbcException) {
            throw RepositoryException("error creating statement for sql:$sql", e)
        }
        return Result(
            try {
                statement.execute().awaitSingle()
            } catch (e: R2dbcException) {
                throw RepositoryException("error executing select: $sql", e)
            }
        )
    }

}

class Result(private val result: io.r2dbc.spi.Result) {
    suspend fun rowsUpdated(): Int = result.rowsUpdated.awaitSingle()

    fun <T : Any> map(mappingFunction: (t: ResultRow) -> T): Flow<T> {
        return result.map { row, _ -> mappingFunction(ResultRow(row)) }.asFlow()
    }
}

class ResultRow(private val row: Row) {
    fun getLazy(key: String): LazyResult<Any?> {
        val value = row.get(key)
        return if (value is Clob) LazyResult { resolveClob(value) } else LazyResult { value }
    }

    private suspend fun resolveClob(result: Clob): String {
        val sb = StringBuilder()
        result.stream()
            .asFlow()
            .collect { chunk -> @Suppress("BlockingMethodInNonBlockingContext") sb.append(chunk) }
        result.discard()
        return sb.toString()
    }

}

class LazyResult<T>(val get: suspend () -> T) {
    suspend fun resolve() = get()
}
