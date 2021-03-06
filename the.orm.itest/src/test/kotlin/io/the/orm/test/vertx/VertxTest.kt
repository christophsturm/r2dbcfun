package io.the.orm.test.vertx


import failgood.FailGood
import failgood.describe
import io.the.orm.test.DBS
import io.the.orm.test.TestUtilConfig
import io.the.orm.test.schemaSql
import io.vertx.kotlin.coroutines.await
import io.vertx.pgclient.PgConnectOptions
import io.vertx.pgclient.PgPool
import io.vertx.sqlclient.PoolOptions
import io.vertx.sqlclient.Row
import io.vertx.sqlclient.RowSet
import io.vertx.sqlclient.SqlClient
import io.vertx.sqlclient.Tuple
import strikt.api.expectThat
import strikt.assertions.containsExactly
import strikt.assertions.isEqualTo

fun main() {
    FailGood.runTest()
}

@Suppress("SqlNoDataSourceInspection", "SqlResolve")
object VertxTest {
    val context = describe("vertx support", disabled = TestUtilConfig.H2_ONLY) {
        val db by dependency({ DBS.psql13.preparePostgresDB() }) { it.close() }


        val client: SqlClient by dependency({
            PgPool.pool(
                PgConnectOptions()
                    .setPort(db.port)
                    .setHost(db.host)
                    .setDatabase(db.databaseName)
                    .setUser("test")
                    .setPassword("test"), PoolOptions().setMaxSize(5)
            ).also { it.query(schemaSql).execute().await() }
        }) { it.close() }
        it("can run sql queries") {
            val result: RowSet<Row> = client.query("SELECT * FROM users WHERE id=1").execute().await()
            expectThat(result.size()).isEqualTo(0)
        }
        it("can run prepared queries") {
            val result: RowSet<Row> =
                client.preparedQuery("SELECT * FROM users WHERE id=$1").execute(Tuple.of(1)).await()
            expectThat(result.size()).isEqualTo(0)
        }
        it("can insert with autoincrement") {
            val result: RowSet<Row> =
                client.preparedQuery("insert into users(name) values ($1) returning id").execute(Tuple.of("belle"))
                    .await()
            expectThat(result.size()).isEqualTo(1)
            expectThat(result.columnsNames()).containsExactly("id")
            expectThat(result.single().get(Integer::class.java, "id").toInt()).isEqualTo(1)
        }
    }
}
