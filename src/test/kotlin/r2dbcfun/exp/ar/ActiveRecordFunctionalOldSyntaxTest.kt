package r2dbcfun.exp.ar

import failfast.describe
import r2dbcfun.test.DBS
import r2dbcfun.test.forAllDatabases
import java.time.LocalDate

/**
 * leaving this here as an example for [forAllDatabases]
 */

object ActiveRecordFunctionalOldSyntaxTest {

    val context = describe("Active Record API", disabled = true) {
        forAllDatabases(DBS.databases) { connectionProvider ->
            val connection = connectionProvider()
            it("just works") {
                val user = User(
                    name = "chris",
                    email = "email",
                    birthday = LocalDate.parse("2020-06-20")
                )
                user.create(connection)
            }
        }
    }
}