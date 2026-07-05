package ir.tapsell.konsist.rules
import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.ext.list.withNameEndingWith
import com.lemonappdev.konsist.api.verify.assertFalse
import com.lemonappdev.konsist.api.verify.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * Keeps the unit-test suite fast and isolated.
 *
 * The first rule scans `src/test` directly (not the konsist source set), so the
 * `*Rules.kt` files installed under `src/konsistTest` are never matched. The
 * second rule only constrains a test whose name (minus `Test`) maps to a real
 * production class, so helper/util tests are left alone.
 */
@Tag("konsist-test-conventions")
class TestConventionRulesTest {

    @Test
    fun `unit tests must not load a Spring context`() {
        val springContextAnnotations = listOf(
            "SpringBootTest",
            "WebMvcTest",
            "DataMongoTest",
            "AutoConfigureMockMvc",
        )

        Konsist.scopeFromDirectory("src/test")
            .classes()
            .assertFalse { cls ->
                cls.annotations.any { it.name in springContextAnnotations }
            }
    }

    @Test
    fun `unit test package must match the package of the class under test`() {
        val mainClasses = Konsist.scopeFromProduction().classes()

        Konsist.scopeFromTest()
            .classes()
            .withNameEndingWith("Test")
            .assertTrue { testClass ->
                val mainClassName = testClass.name.removeSuffix("Test")
                val mainClass = mainClasses.firstOrNull { it.name == mainClassName }
                mainClass == null || testClass.packagee?.name == mainClass.packagee?.name
            }
    }
}
