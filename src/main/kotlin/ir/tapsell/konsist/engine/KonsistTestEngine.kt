package ir.tapsell.konsist.engine

import io.github.classgraph.ClassGraph
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.platform.engine.EngineDiscoveryRequest
import org.junit.platform.engine.EngineExecutionListener
import org.junit.platform.engine.ExecutionRequest
import org.junit.platform.engine.TestDescriptor
import org.junit.platform.engine.TestEngine
import org.junit.platform.engine.TestExecutionResult
import org.junit.platform.engine.TestSource
import org.junit.platform.engine.TestTag
import org.junit.platform.engine.UniqueId
import org.junit.platform.engine.support.descriptor.AbstractTestDescriptor
import org.junit.platform.engine.support.descriptor.ClassSource
import org.junit.platform.engine.support.descriptor.EngineDescriptor
import org.junit.platform.engine.support.descriptor.MethodSource
import java.lang.reflect.Method
import java.util.Optional

/**
 * JUnit Platform [TestEngine] that discovers and executes Konsist architecture rules.
 *
 * Registered via `META-INF/services/org.junit.platform.engine.TestEngine` so JUnit's
 * launcher picks it up automatically — no explicit registration needed in consuming projects.
 *
 * Discovery scans `ir.tapsell.konsist.rules` for classes that contain at least one
 * [@Test][Test]-annotated method (see [RULE_CLASSES]). Each such class becomes a
 * [KonsistClassDescriptor] (CONTAINER) whose methods become [KonsistMethodDescriptor]s (TEST).
 */
class KonsistTestEngine : TestEngine {

    /** Stable engine ID used by JUnit Platform to route discovery/execution requests. */
    override fun getId(): String = "tapsell-konsist"

    /**
     * Builds the test tree from [RULE_CLASSES].
     *
     * Classes with no [@Test][Test] methods are silently dropped so empty rule files
     * do not pollute the test report.
     */
    override fun discover(request: EngineDiscoveryRequest, uniqueId: UniqueId): TestDescriptor {
        val engineDescriptor = EngineDescriptor(uniqueId, "Tapsell Konsist Rules")

        for (ruleClass in RULE_CLASSES) {
            val classDescriptor = buildClassDescriptor(uniqueId, ruleClass)
            if (classDescriptor.children.isNotEmpty()) {
                engineDescriptor.addChild(classDescriptor)
            }
        }

        return engineDescriptor
    }

    /** Wraps [ruleClass] in a [KonsistClassDescriptor] and attaches one child per [@Test][Test] method. */
    private fun buildClassDescriptor(engineId: UniqueId, ruleClass: Class<*>): KonsistClassDescriptor {
        val classId = engineId.append("class", ruleClass.name)
        val classDescriptor = KonsistClassDescriptor(classId, ruleClass)

        ruleClass.declaredMethods
            .filter { it.isAnnotationPresent(Test::class.java) }
            .sortedBy { it.name }
            .forEach { method ->
                classDescriptor.addChild(KonsistMethodDescriptor(classId.append("method", method.name), method))
            }

        return classDescriptor
    }

    override fun execute(request: ExecutionRequest) {
        val engine = request.rootTestDescriptor
        val listener = request.engineExecutionListener

        listener.executionStarted(engine)

        engine.children
            .filterIsInstance<KonsistClassDescriptor>()
            .forEach { executeClass(it, listener) }

        listener.executionFinished(engine, TestExecutionResult.successful())
    }

    /**
     * Instantiates the rule class via its no-arg constructor and runs each method.
     *
     * Construction failure is reported as a class-level failure so the remaining
     * classes still execute.
     */
    private fun executeClass(classDescriptor: KonsistClassDescriptor, listener: EngineExecutionListener) {
        listener.executionStarted(classDescriptor)

        val instance = try {
            classDescriptor.testClass.getDeclaredConstructor().newInstance()
        } catch (e: Exception) {
            listener.executionFinished(classDescriptor, TestExecutionResult.failed(e))
            return
        }

        classDescriptor.children
            .filterIsInstance<KonsistMethodDescriptor>()
            .forEach { executeMethod(it, instance, listener) }

        listener.executionFinished(classDescriptor, TestExecutionResult.successful())
    }

    /**
     * Invokes a single rule method and translates the outcome to a [TestExecutionResult].
     *
     * [java.lang.reflect.InvocationTargetException] is unwrapped so the underlying Konsist assertion
     * (not the reflection wrapper) appears in the failure report.
     */
    private fun executeMethod(
        methodDescriptor: KonsistMethodDescriptor,
        instance: Any,
        listener: EngineExecutionListener,
    ) {
        listener.executionStarted(methodDescriptor)
        val result = runCatching { methodDescriptor.method.invoke(instance) }
            .fold(
                onSuccess = { TestExecutionResult.successful() },
                onFailure = { e ->
                    val cause = (e as? java.lang.reflect.InvocationTargetException)?.cause ?: e
                    TestExecutionResult.failed(cause)
                },
            )
        listener.executionFinished(methodDescriptor, result)
    }

    private companion object {
        /**
         * All rule classes in `ir.tapsell.konsist.rules`, discovered lazily at first use.
         *
         * ClassGraph is used instead of a hand-rolled JDK solution because Spring Boot fat
         * JARs use a custom URL protocol (`jar:nested:…`) that [java.net.JarURLConnection]
         * cannot open, causing standard classpath scanning to miss classes entirely.
         */
        val RULE_CLASSES: List<Class<*>> by lazy {
            ClassGraph()
                .enableClassInfo()
                .enableMethodInfo()
                .enableAnnotationInfo()
                .acceptPackages("ir.tapsell.konsist.rules")
                .scan()
                .use { result ->
                    result.getClassesWithMethodAnnotation(Test::class.java)
                        .loadClasses()
                }
        }
    }
}

/**
 * CONTAINER descriptor for a Konsist rule class.
 *
 * Forwards the class-level [@Tag][Tag] annotations to the JUnit Platform so consumers
 * can filter rule sets with `--include-tag konsist-<area>`.
 */
class KonsistClassDescriptor(
    uniqueId: UniqueId,
    val testClass: Class<*>,
) : AbstractTestDescriptor(uniqueId, testClass.simpleName) {

    private val source: TestSource = ClassSource.from(testClass)

    override fun getType(): TestDescriptor.Type = TestDescriptor.Type.CONTAINER
    override fun getSource(): Optional<TestSource> = Optional.of(source)
    override fun getTags(): Set<TestTag> =
        testClass.getAnnotationsByType(Tag::class.java)
            .mapTo(mutableSetOf()) { TestTag.create(it.value) }
}

/** TEST descriptor for a single [@Test][Test]-annotated rule method. */
class KonsistMethodDescriptor(
    uniqueId: UniqueId,
    val method: Method,
) : AbstractTestDescriptor(uniqueId, method.name) {

    private val source: TestSource = MethodSource.from(method.declaringClass, method)

    override fun getType(): TestDescriptor.Type = TestDescriptor.Type.TEST
    override fun getSource(): Optional<TestSource> = Optional.of(source)
}
