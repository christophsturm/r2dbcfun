package r2dbcfun.query

import io.kotest.core.spec.style.FunSpec
import io.mockk.mockk
import io.r2dbc.spi.Connection
import r2dbcfun.ResultMapper
import r2dbcfun.test.TestObjects.Entity

class QueryFactoryTest : FunSpec({
    context("typesafe query factory") {
        val resultMapper = mockk<ResultMapper<Entity>>(relaxed = true)
        val queryFactory = QueryFactory(Entity::class, resultMapper)
        val connection = mockk<Connection>()
        val condition = Entity::id.isEqualTo()
        test("can create query with one parameter") {
            val query = queryFactory.createQuery(condition)
            query.with(connection, 1)
        }
        test("can create query with two parameter") {
            val query = queryFactory.createQuery(condition, condition)
            query.with(connection, 1, 1)
        }
        test("can create query with three parameter") {
            val query = queryFactory.createQuery(condition, condition, condition)
            query.with(connection, 1, 1, 1)
        }
    }

})
