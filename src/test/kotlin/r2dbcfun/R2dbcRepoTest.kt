package r2dbcfun

import dev.minutest.ContextBuilder
import dev.minutest.TestContextBuilder
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.junit.experimental.applyRule
import dev.minutest.rootContext
import io.r2dbc.spi.Connection
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.debug.junit4.CoroutinesTimeout
import kotlinx.coroutines.flow.toCollection
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import reactor.blockhound.BlockHound
import strikt.api.expectCatching
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.containsExactlyInAnyOrder
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import strikt.assertions.isFailure
import strikt.assertions.isFalse
import strikt.assertions.isNotNull
import strikt.assertions.isNull
import strikt.assertions.message
import kotlin.reflect.KClass


object TestConfig {
    val H2_ONLY = System.getenv("H2_ONLY") != null
    val CI = System.getenv("CI") != null
    val PITEST = System.getenv("PITEST") != null
}

@Suppress("SqlResolve")
@ExperimentalCoroutinesApi
class R2dbcRepoTest : JUnit5Minutests {
    init {
        BlockHound.install()
    }

    private val characters = ('A'..'Z').toList() + (('a'..'z').toList()).plus(' ')
    private val reallyLongString = (1..20000).map { characters.random() }.joinToString("")

    data class UserPK(override val id: Long) : PK


    enum class Color {
        RED,

        @Suppress("unused")
        BLUE
    }

    data class User(
        val id: UserPK? = null,
        val name: String,
        val email: String?,
        val isCool: Boolean = false,
        val bio: String? = null,
        val favoriteColor: Color? = null
    )


    class Fixture<T : Any>(val connection: Connection, type: KClass<T>) {
        val repo = R2dbcRepo(connection, type)
        val timeout = CoroutinesTimeout(if (TestConfig.PITEST) 500000 else if (TestConfig.CI) 50000 else 500)
    }

    private fun ContextBuilder<Connection>.repoTests() {
        derivedContext<Fixture<User>>("a repo with a user data class") {
            makeFixture()
            context("Creating Rows") {
                test("can insert data class and return primary key") {
                    runBlocking {
                        val user = repo.create(User(name = "chris", email = "my email", bio = reallyLongString))
                        expectThat(user).and {
                            get { id }.isEqualTo(UserPK(1))
                            get { name }.isEqualTo("chris")
                            get { email }.isEqualTo("my email")
                        }
                    }
                }

                test("supports nullable values") {
                    runBlocking {
                        val user = repo.create(User(name = "chris", email = null))
                        expectThat(user).and {
                            get { id }.isEqualTo(UserPK(1))
                            get { name }.isEqualTo("chris")
                            get { email }.isNull()
                        }
                    }
                }
            }
            context("loading data objects") {
                test("can load data object by id") {
                    runBlocking {
                        repo.create(User(name = "anotherUser", email = "my email"))
                        val id = repo.create(
                            User(
                                name = "chris", email = "my email", bio = reallyLongString, isCool = false,
                                favoriteColor = Color.RED
                            )
                        ).id!!
                        val user = repo.findById(id)
                        expectThat(user).and {
                            get { id }.isEqualTo(id)
                            get { name }.isEqualTo("chris")
                            get { email }.isEqualTo("my email")
                            get { isCool }.isFalse()
                            get { bio }.isEqualTo(reallyLongString)
                            get { favoriteColor }.isEqualTo(Color.RED)
                        }
                    }
                }
                test("can load data object by field value") {
                    runBlocking {
                        val firstUser = repo.create(User(name = "chris", email = "my email"))
                        val secondUser = repo.create(User(name = "chris", email = "different email"))
                        val users = repo.findBy(User::name, "chris").toCollection(mutableListOf())
                        expectThat(users).containsExactlyInAnyOrder(firstUser, secondUser)
                    }
                }
                test("throws NotFoundException when id does not exist") {
                    runBlocking {
                        expectCatching {
                            repo.findById(UserPK(1))
                        }.isFailure().isA<NotFoundException>().message.isNotNull().isEqualTo("No users found for id 1")

                    }
                }
            }
            context("updating objects") {
                test("can update objects") {
                    val originalUser = User(name = "chris", email = "my email", bio = reallyLongString)
                    runBlocking {
                        val id = repo.create(originalUser).id!!
                        val readBackUser = repo.findById(id)
                        repo.update(readBackUser.copy(name = "updated name", email = null))
                        val readBackUpdatedUser = repo.findById(id)
                        expectThat(readBackUpdatedUser).isEqualTo(
                            originalUser.copy(
                                id = id,
                                name = "updated name",
                                email = null
                            )
                        )
                    }

                }
            }
            context("enum fields") {
                test("enum fields are serialized as upper case strings") {
                    runBlocking {
                        val id = repo.create(
                            User(
                                name = "chris", email = "my email", bio = reallyLongString, isCool = false,
                                favoriteColor = Color.RED
                            )
                        ).id!!
                        val color =
                            connection.createStatement("select * from Users where id = $1").bind("$1", id.id).execute()
                                .awaitSingle()
                                .map { row, _ -> row.get(User::favoriteColor.name.toSnakeCase(), String::class.java) }
                                .awaitSingle()
                        expectThat(color).isEqualTo("RED")
                    }

                }
            }
        }

        @Serializable
        data class SerializableUserPK(override val id: Long) : PK

        @Serializable
        data class SerializableUser(
            val id: SerializableUserPK? = null,
            val name: String,
            val email: String?
        )

        derivedContext<Fixture<SerializableUser>>("interop with kotlinx.serializable") {
            makeFixture()
            test("can insert data class and return primary key") {
                runBlocking {
                    val user = repo.create(SerializableUser(name = "chris", email = "my email"))
                    expectThat(user).and {
                        get { id }.isEqualTo(SerializableUserPK(1))
                        get { name }.isEqualTo("chris")
                        get { email }.isEqualTo("my email")
                    }
                }
            }

        }

        context("fail fast error handling") {
            test("fails fast if PK has more than one field") {
                data class MismatchPK(override val id: Long, val blah: String) : PK
                data class Mismatch(val id: MismatchPK)
                runBlocking {
                    expectCatching {
                        R2dbcRepo.create<Mismatch>(fixture)
                    }.isFailure().isA<R2dbcRepoException>().message.isNotNull()
                        .contains("PK classes must have a single field of type long")
                }

            }
            test("fails if class contains unsupported fields") {
                data class Unsupported(val field: String)
                data class ClassWithUnsupportedType(val id: Long, val unsupported: Unsupported)
                runBlocking {
                    expectCatching {
                        R2dbcRepo.create<ClassWithUnsupportedType>(fixture)
                    }.isFailure().isA<R2dbcRepoException>().message.isNotNull()
                        .contains("type Unsupported not supported")
                }
            }
        }

    }

    private inline fun <reified T : Any> TestContextBuilder<Connection, Fixture<T>>.makeFixture() {
        applyRule { timeout }
        deriveFixture {
            val connection = this
            runBlocking {
                Fixture(connection, T::class)
            }
        }
    }

    @Suppress("unused")
    fun tests() = rootContext<Unit> {
        if (!TestConfig.H2_ONLY) { // if we need the postgres container, start loading it while running the h2 tests, and outside of the main context which has a timeout rule
            test("warm up psql container") {
                container
            }
        }
        derivedContext<Connection>("run on H2") {
            fixture {
                runBlocking {
                    prepareH2().create().awaitSingle()
                }
            }
            repoTests()
            after {
                runBlocking {
                    fixture.close().awaitFirstOrNull()
                }
            }
        }
        if (!TestConfig.H2_ONLY) {
            derivedContext<Connection>("run on postgresql") {
                fixture {
                    runBlocking {
                        preparePostgreSQL().create().awaitSingle()
                    }
                }
                repoTests()
                after {
                    runBlocking {
                        fixture.close().awaitFirstOrNull()
                    }
                }
            }
        }

    }
}

