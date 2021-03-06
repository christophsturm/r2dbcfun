package io.the.orm.test.functional.exp.ar

import failgood.FailGood
import io.the.orm.dbio.ConnectionProvider
import io.the.orm.test.DBS
import io.the.orm.test.describeOnAllDbs
import io.the.orm.test.functional.Color
import io.the.orm.test.functional.UserPK
import java.math.BigDecimal
import java.time.LocalDate

/*
lay out how an active record api could look like
 */
data class User(
    val id: UserPK? = null,
    val name: String,
    val email: String?,
    val isCool: Boolean? = false,
    val bio: String? = null,
    val favoriteColor: Color? = null,
    val birthday: LocalDate? = null,
    val weight: Double? = null,
    val balance: BigDecimal? = null
) : ActiveRecord

interface ActiveRecord

fun main() {
    FailGood.runTest()
}

object ActiveRecordFunctionalTest {

    val context = describeOnAllDbs("Active Record API", DBS.databases) { connectionProvider ->
        it("just works") {
            val connection = connectionProvider()
            val user = User(
                name = "chris",
                email = "email",
                birthday = LocalDate.parse("2020-06-20")
            )
            user.create(connection)
        }
    }
}

suspend inline fun <reified T : ActiveRecord> T.create(connection: ConnectionProvider): T =
    io.the.orm.RepositoryImpl(T::class).create(connection, this)

