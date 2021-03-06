package r2dbcfun

import kotlinx.coroutines.flow.single
import r2dbcfun.dbio.ConnectionProvider
import r2dbcfun.internal.ClassInfo
import r2dbcfun.internal.ExceptionInspector
import r2dbcfun.internal.IDHandler
import r2dbcfun.query.QueryFactory
import r2dbcfun.query.QueryFactory.Companion.isEqualToCondition
import r2dbcfun.util.toSnakeCase
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredMemberProperties

interface PK {
    val id: Long
}

class Repository<T : Any>(kClass: KClass<T>, otherClasses: Set<KClass<*>> = emptySet()) {
    companion object {
        /** creates a Repo for the entity <T> */
        inline fun <reified T : Any> create(): Repository<T> = Repository(T::class)
    }

    private val properties = kClass.declaredMemberProperties.associateBy({ it.name }, { it })
    private val propertyReaders =
        properties.filter { it.key != "id" }.values.map { PropertyReader(it) }

    private val tableName = "${kClass.simpleName!!.toSnakeCase().toLowerCase()}s"

    @Suppress("UNCHECKED_CAST")
    private val idProperty =
        (properties["id"]
            ?: throw RepositoryException("class ${kClass.simpleName} has no field named id")) as
                KProperty1<T, Any>

    private val idHandler = IDHandler(kClass)

    private val exceptionInspector = ExceptionInspector(tableName, kClass)

    private val inserter = Inserter(tableName, propertyReaders, idHandler, exceptionInspector)

    private val updater = Updater(tableName, propertyReaders, idHandler, idProperty)

    private val classInfo = ClassInfo(kClass, idHandler, otherClasses)

    val queryFactory: QueryFactory<T> =
        QueryFactory(kClass, ResultMapper(tableName, classInfo), this, idHandler, idProperty)

    /**
     * creates a new record in the database.
     *
     * @param instance the instance that will be used to set the fields of the newly created record
     * @return a copy of the instance with an assigned id field.
     */
    suspend fun create(connectionProvider: ConnectionProvider, instance: T): T =
        connectionProvider.withConnection { connection ->
            inserter.create(connection, instance)
        }

    /**
     * updates a record in the database.
     *
     * @param instance the instance that will be used to update the record
     */
    suspend fun update(connectionProvider: ConnectionProvider, instance: T) {
        connectionProvider.withConnection { connection ->
            updater.update(connection, instance)
        }
    }

    private val byIdQuery = queryFactory.createQuery(isEqualToCondition(idProperty))

    /**
     * loads an object from the database
     *
     * @param id the primary key of the object to load
     */
    suspend fun findById(connectionProvider: ConnectionProvider, id: PK): T {
        return try {
            byIdQuery.with(connectionProvider, id.id).find().single()
        } catch (e: NoSuchElementException) {
            throw NotFoundException("No $tableName found for id ${id.id}")
        }
    }
}
