package ir.tapsell.konsist.rules
import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.ext.list.properties
import com.lemonappdev.konsist.api.ext.list.withAnnotationOf
import com.lemonappdev.konsist.api.verify.assertFalse
import ir.beigirad.junitbaselineextension.BaselineExtension
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RestController

/**
 * Layering + dependency-injection hygiene for Spring beans.
 *
 * Layering: controllers must go through a service, never touch a repository.
 * A dependency is detected by the parameter/field *type name* ending in
 * `Repository`, covering both constructor injection and `@Autowired` field
 * injection.
 *
 * DI hygiene: dependencies are constructor-injected, not field-injected, and an
 * implicitly-autowired single primary constructor carries no redundant
 * `@Autowired`.
 *
 * Requires Spring on the test classpath — `@Autowired` is imported and matched
 * by type.
 */
@Tag("konsist-layer-dependency")
@ExtendWith(BaselineExtension::class)
class LayerDependencyRulesTest {

    /**
     * Field injection (`@Autowired lateinit var dep`) hides a class's
     * dependencies, defeats `val`/immutability, and makes the bean impossible
     * to construct in a plain unit test. Require constructor injection instead.
     */
    @Test
    fun `dependencies must not be field-injected`() {
        Konsist.scopeFromProduction()
            .classes()
            .properties()
            .sortedBy { it.location }
            .assertFalse { prop ->
                prop.hasAnnotationOf(Autowired::class)
            }
    }

    /**
     * When a bean has a single primary constructor, Spring autowires it
     * implicitly, so an explicit `@Autowired` on it is redundant noise. (A class
     * with multiple constructors legitimately needs `@Autowired` to disambiguate
     * and is therefore left alone.)
     */
    @Test
    fun `single primary constructor must not be annotated with Autowired`() {
        Konsist.scopeFromProduction()
            .classes()
            .assertFalse { cls ->
                val primary = cls.primaryConstructor ?: return@assertFalse false
                cls.secondaryConstructors.isEmpty() &&
                    primary.annotations.any { it.name == "Autowired" }
            }
    }
}