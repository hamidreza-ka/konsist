package ir.tapsell.konsist.rules
import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.ext.list.modifierprovider.withPublicModifier
import com.lemonappdev.konsist.api.ext.list.properties
import com.lemonappdev.konsist.api.ext.list.withNameEndingWith
import com.lemonappdev.konsist.api.ext.list.withTopLevel
import com.lemonappdev.konsist.api.verify.assertFalse
import com.lemonappdev.konsist.api.verify.assertTrue
import ir.beigirad.junitbaselineextension.BaselineExtension
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * Immutability guarantees for production code.
 *
 * All class properties must be `val` (no `var`, no `lateinit`, no mutable
 * collection types in public API). This applies to EVERY production class —
 * services, configs, components, the lot. That is deliberately strict; if a
 * project legitimately needs mutable state somewhere (e.g. an `@Value var`
 * config field), narrow the applicable rule's scope rather than loosening it
 * for everyone.
 *
 * `.classes()` does NOT include `object`/`companion object` declarations, so the
 * second rule below covers singleton state separately via
 * `.objects(includeNested = true)` (which also pulls in companion objects).
 * A `var` on a process-wide singleton is shared mutable state and a
 * thread-safety hazard.
 */
@Tag("konsist-immutability")
@ExtendWith(BaselineExtension::class)
class ImmutabilityRulesTest {

    /**
     * No var, no lateinit, no mutable collections
     */
    @Test
    fun `all classes properties must be immutable vals and not lateinit`() {
        Konsist.scopeFromProduction()
            .classes()
            .properties()
            .sortedBy { it.location }
            .assertTrue { prop ->
                !prop.isVar
                        && !prop.hasLateinitModifier
                        && !(prop.type?.isMutableType ?: false && !prop.hasPrivateModifier)

            }
    }

    /**
     * Same immutability guarantee for singletons. `objects(includeNested = true)`
     * reaches `companion object`s (nested inside their class), which `.classes()`
     * above does not.
     */
    @Test
    fun `top level and object and companion object properties must be immutable`() {
        Konsist.scopeFromProduction()
            .properties()
            .withTopLevel()
            .sortedBy { it.location }
            .assertTrue { prop ->
                !prop.isVar &&
                        !prop.hasLateinitModifier &&
                        !(prop.type?.isMutableType ?: false)
            }

        Konsist.scopeFromProduction()
            .objects(includeNested = true)
            .properties()
            .sortedBy { it.location }
            .assertTrue { prop ->
                !prop.isVar &&
                    !prop.hasLateinitModifier &&
                    !(prop.type?.isMutableType ?: false)
            }
    }

    /**
     * Public API MUST NOT expose mutable collection types.
     *
     * Checks public function return types.
     */
    @Test
    fun `public function must not expose mutable collection types`() {
        Konsist.scopeFromProduction()
            .functions()
            .withPublicModifier()
            .sortedBy { it.location }
            .assertFalse { fn ->
                val returnTypeName = fn.returnType ?: return@assertFalse false
                returnTypeName.isMutableType
            }
    }

}