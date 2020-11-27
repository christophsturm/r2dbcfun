package r2dbcfun.transaction

import io.kotest.core.spec.style.FunSpec
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.mockkStatic
import io.r2dbc.spi.Connection
import kotlinx.coroutines.reactive.awaitFirstOrNull
import strikt.api.expectThat
import strikt.api.expectThrows
import strikt.assertions.isEqualTo
import strikt.assertions.isTrue

class TransactionTest : FunSpec({
    context("transaction support") {
        mockkStatic("kotlinx.coroutines.reactive.AwaitKt")
        val connection = mockk<Connection>(relaxed = true)
        coEvery { connection.beginTransaction().awaitFirstOrNull() }.returns(null)
        coEvery { connection.commitTransaction().awaitFirstOrNull() }.returns(null)
        coEvery { connection.rollbackTransaction().awaitFirstOrNull() }.returns(null)
        test("calls block") {
            var called = false
            connection.transaction {
                coVerify { connection.beginTransaction().awaitFirstOrNull() }
                called = true
            }
            expectThat(called).isTrue()
            coVerify { connection.commitTransaction().awaitFirstOrNull() }
        }
        test("rolls back transaction if exception occurs") {
            val runtimeException = RuntimeException("failed")
            expectThrows<RuntimeException> {
                connection.transaction {
                    throw runtimeException
                }
            }.isEqualTo(runtimeException)
            coVerify { connection.rollbackTransaction().awaitFirstOrNull() }

        }
    }
})