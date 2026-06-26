package ir.tapsell.konsist.rules
import com.lemonappdev.konsist.api.KoModifier
import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.ext.list.modifierprovider.withoutModifier
import com.lemonappdev.konsist.api.ext.list.properties
import com.lemonappdev.konsist.api.ext.list.withNameEndingWith
import com.lemonappdev.konsist.api.verify.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * Immutability guarantees for production code.
 *
 * Scope note: `entity classes must be data class` is name-scoped (`*Entity`),
 * but `all classes properties must be immutable` applies to EVERY production
 * class — services, configs, components, the lot. That is deliberately strict;
 * if a project legitimately needs mutable state somewhere (e.g. an `@Value var`
 * config field), narrow the second rule's scope rather than loosening it for
 * everyone, e.g. `.withNameEndingWith("Entity")` or `.withDataModifier()`.
 *
 * `.classes()` does NOT include `object`/`companion object` declarations, so the
 * third rule below covers singleton state separately via
 * `.objects(includeNested = true)` (nested objects pulls in companion objects).
 * A `var` on a process-wide singleton is shared mutable state and a
 * thread-safety hazard.
 */
@Tag("konsist-immutability")
class ImmutabilityRules {

    @Test
    fun `entity classes must be data class`() {
        Konsist.scopeFromProduction()
            .classes()
            .withoutModifier(KoModifier.ABSTRACT)
            .withNameEndingWith("Entity")
            .sortedBy { it.location }
            .assertTrue { cls ->
                cls.hasDataModifier
            }
    }

    /**
     * No var, no lateinit, no mutable collections
     */
    @Test
    fun `all classes properties must be immutable vals and not lateinit`() {
        Konsist.scopeFromProduction()
            .classes()
            .withoutModifier(KoModifier.ABSTRACT)
            .properties()
            .sortedBy { it.location }
            .assertTrue { prop ->
                !prop.isVar
                        && !prop.hasLateinitModifier
                        && !(prop.type?.isMutableType ?: false)

            }
    }

    /**
     * Same immutability guarantee for singletons. `objects(includeNested = true)`
     * reaches `companion object`s (nested inside their class), which `.classes()`
     * above does not.
     */
    @Test
    fun `object and companion object properties must be immutable`() {
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

}