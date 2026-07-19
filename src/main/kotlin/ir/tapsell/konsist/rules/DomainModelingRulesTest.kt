package ir.tapsell.konsist.rules

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.ext.list.modifierprovider.withValueModifier
import com.lemonappdev.konsist.api.ext.list.withNameEndingWith
import com.lemonappdev.konsist.api.verify.assertFalse
import com.lemonappdev.konsist.api.verify.assertTrue
import ir.beigirad.junitbaselineextension.BaselineExtension
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * Enforces the shape of shared value types (inline value class wrapping UUID,
 * private constructor, canonical factories, KDoc) and forbids semantically
 * empty types (`Pair`, `Triple`) in function signatures.
 */
@ExtendWith(BaselineExtension::class)
@Tag("konsist-domain-modeling")
class DomainModelingRulesTest {

    /**
     * New shared value types MUST follow the canonical shape:
     * - Annotated with `@JvmInline`
     * - `value class` wrapping `java.util.UUID`
     * - Private constructor
     * - Factory methods: `from(String)`, `fromOrThrow(String)`, `from(UUID)`, `generate()`
     * - KDoc on the class
     *
     * This is the flagship Konsist rule — it verifies the entire structural
     * contract in a single multi-assertion test.
     *
     * High confidence — all conditions are directly inspectable from
     * declaration shape.
     *
     */
    @Test
    fun `shared value types must follow the canonical id shape`() {
        Konsist.scopeFromProduction()
            .classes()
            .withValueModifier()
            .withNameEndingWith("Id")
            .sortedBy { it.location }
            .assertTrue { cls ->
                // Must be a @JvmInline value class.
                val isJvmInline = cls.hasAnnotationOf(JvmInline::class)

                // Primary constructor must be private.
                val hasPrivateConstructor = cls.primaryConstructor?.hasPrivateModifier == true

                // Must wrap java.util.UUID (check the single constructor parameter).
                val wrapsUuid = cls.primaryConstructor?.parameters?.singleOrNull()?.type?.name == "UUID"

                // Must have the canonical factory methods.
                val hasFromString = cls.hasFunction { fn ->
                    fn.name == "from" &&
                            fn.parameters.size == 1 &&
                            fn.parameters.first().type.name == "String"
                }
                val hasFromOrThrow = cls.hasFunction { fn ->
                    fn.name == "fromOrThrow" &&
                            fn.parameters.size == 1 &&
                            fn.parameters.first().type.name == "String"
                }
                val hasFromUuid = cls.hasFunction { fn ->
                    fn.name == "from" &&
                            fn.parameters.size == 1 &&
                            fn.parameters.first().type.name == "UUID"
                }
                val hasGenerate = cls.hasFunction { fn ->
                    fn.name == "generate" && fn.parameters.isEmpty()
                }

                // Must have KDoc.
                val hasKDoc = cls.hasKDoc

                isJvmInline && hasPrivateConstructor && wrapsUuid &&
                        hasFromString && hasFromOrThrow && hasFromUuid &&
                        hasGenerate && hasKDoc
            }
    }

    /**
     * AVOID `Pair` and `Triple` as function parameters or return types.
     *
     * `Pair` and `Triple` are semantically empty — `first`/`second`/`third`
     * convey no domain meaning. Replace with a named data class or a
     * sealed type that carries intent.
     *
     */
    @Test
    fun `functions must not use Pair or Triple in parameters or return types`() {
        val forbiddenTypeNames = setOf("Pair", "Triple", "kotlin.Pair", "kotlin.Triple")

        Konsist.scopeFromProduction()
            .functions()
            .sortedBy { it.location }
            .assertFalse { fn ->
                val returnName = fn.returnType?.name
                returnName in forbiddenTypeNames ||
                        fn.parameters.any { it.type.name in forbiddenTypeNames }
            }
    }
}
