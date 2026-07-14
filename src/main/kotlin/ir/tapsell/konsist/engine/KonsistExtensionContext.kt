package ir.tapsell.konsist.engine

import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.TestInstances
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.platform.engine.ConfigurationParameters
import java.lang.reflect.AnnotatedElement
import java.lang.reflect.Method
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Function

/**
 * Simple in-memory [ExtensionContext.Store] backed by a [ConcurrentHashMap].
 */
class InMemoryStore : ExtensionContext.Store {

    private val map = ConcurrentHashMap<Any, Any?>()

    override fun get(key: Any): Any? = map[key]

    @Suppress("UNCHECKED_CAST")
    override fun <V> get(key: Any, requiredType: Class<V>): V =
        requiredType.cast(map[key]) as V

    override fun <K, V> getOrComputeIfAbsent(
        key: K,
        creator: Function<K, V>,
    ): Any? = map.computeIfAbsent(key as Any) { creator.apply(key) }

    @Suppress("UNCHECKED_CAST")
    override fun <K, V> getOrComputeIfAbsent(
        key: K,
        creator: Function<K, V>,
        requiredType: Class<V>,
    ): V = requiredType.cast(getOrComputeIfAbsent(key, creator))

    override fun put(key: Any, value: Any?) {
        if (value == null) map.remove(key) else map[key] = value
    }

    override fun remove(key: Any): Any? = map.remove(key)

    @Suppress("UNCHECKED_CAST")
    override fun <V> remove(key: Any, requiredType: Class<V>): V =
        requiredType.cast(map.remove(key)) as V
}

/**
 * Minimal [ExtensionContext] that bridges JUnit Jupiter extensions into the
 * custom [KonsistTestEngine].
 *
 * Each context level (engine / class / method) has its own instance with its
 * own namespaced stores. Configuration parameters are forwarded from the
 * JUnit Platform launch request so that extensions can read consumer-side
 * settings (e.g. `baseline.record`).
 */
class KonsistExtensionContext(
    private val configurationParameters: ConfigurationParameters,
    private val testClass: Class<*>,
    private val testMethod: Method? = null,
    private val testInstance: Any? = null,
    private val parentContext: ExtensionContext? = null,
    private val uniqueId: String,
) : ExtensionContext {

    /** One [InMemoryStore] per [ExtensionContext.Namespace], matching Jupiter semantics. */
    private val stores = ConcurrentHashMap<ExtensionContext.Namespace, InMemoryStore>()

    // ---- hierarchy ----

    override fun getUniqueId(): String = uniqueId

    override fun getDisplayName(): String =
        if (testMethod != null) "${testClass.simpleName}.${testMethod.name}"
        else testClass.simpleName

    override fun getTags(): Set<String> = emptySet()

    override fun getParent(): Optional<ExtensionContext> = Optional.ofNullable(parentContext)

    override fun getRoot(): ExtensionContext = parentContext?.root ?: this

    // ---- element / class / method / instance ----

    override fun getElement(): Optional<AnnotatedElement> =
        Optional.of(testMethod ?: testClass)

    override fun getTestClass(): Optional<Class<*>> = Optional.of(testClass)

    override fun getTestMethod(): Optional<Method> = Optional.ofNullable(testMethod)

    override fun getTestInstance(): Optional<Any> = Optional.ofNullable(testInstance)

    override fun getTestInstanceLifecycle(): Optional<TestInstance.Lifecycle> =
        Optional.of(TestInstance.Lifecycle.PER_METHOD)

    override fun getTestInstances(): Optional<TestInstances> =
        testInstance?.let { inst ->
            Optional.of(TestInstancesImpl(inst))
        } ?: Optional.empty()

    // ---- configuration ----

    override fun getConfigurationParameter(key: String): Optional<String> =
        configurationParameters.get(key)

    override fun <T> getConfigurationParameter(
        key: String,
        transformer: java.util.function.Function<String, T>,
    ): Optional<T> =
        getConfigurationParameter(key).map { transformer.apply(it) }

    // ---- store ----

    override fun getStore(namespace: ExtensionContext.Namespace): ExtensionContext.Store =
        stores.computeIfAbsent(namespace) { InMemoryStore() }

    // ---- extension-supported features not used by BaselineExtension ----

    override fun getExecutionException(): Optional<Throwable> = Optional.empty()

    override fun publishReportEntry(map: Map<String, String>) { /* no-op */ }

    override fun getExecutionMode(): ExecutionMode = ExecutionMode.SAME_THREAD

    override fun getExecutableInvoker(): org.junit.jupiter.api.extension.ExecutableInvoker =
        throw UnsupportedOperationException("ExecutableInvoker is not supported by KonsistTestEngine")
}

/**
 * Minimal [TestInstances] for a single non-nested test instance.
 */
private class TestInstancesImpl(
    private val instance: Any,
) : TestInstances {
    override fun getInnermostInstance(): Any = instance
    override fun getEnclosingInstances(): List<Any> = emptyList()
    override fun getAllInstances(): List<Any> = listOf(instance)
    @Suppress("UNCHECKED_CAST")
    override fun <T> findInstance(requiredType: Class<T>): Optional<T> {
        val result: Optional<*> = if (requiredType.isInstance(instance)) {
            Optional.of<Any>(instance)
        } else {
            Optional.empty<Any>()
        }
        return result as Optional<T>
    }
}