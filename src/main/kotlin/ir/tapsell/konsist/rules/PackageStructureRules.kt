package ir.tapsell.konsist.rules
import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.ext.list.withAnnotationOf
import com.lemonappdev.konsist.api.ext.list.withNameEndingWith
import com.lemonappdev.konsist.api.verify.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.stereotype.Repository
import org.springframework.web.bind.annotation.RestController

/**
 * Where each layer's classes are allowed to live.
 *
 * `packagee?.name` is nullable (a class in the default package yields null),
 * so each predicate collapses that null to an explicit `false` (via `== true`
 * or an early `return@assertTrue false`). Returning a bare `Boolean?` would let
 * a null surface as an ambiguous violation instead of a clean "class is in the
 * default package → fails this rule".
 */
@Tag("konsist-package-structure")
class PackageStructureRules {

    @Test
    fun `controllers must be inside controller or presentation package`() {
        Konsist.scopeFromProduction()
            .classes()
            .withAnnotationOf(RestController::class)
            .assertTrue { cls ->
                val pkg = cls.packagee?.name ?: return@assertTrue false
                pkg.contains("controller") || pkg.contains("presentation")
            }
    }

    @Test
    fun `repository classes must be inside domain package`() {
        Konsist.scopeFromProduction()
            .classes()
            .withAnnotationOf(Repository::class)
            .assertTrue { it.packagee?.name?.contains("domain") == true }
    }

    @Test
    fun `entity classes must be inside domain package`() {
        Konsist.scopeFromProduction()
            .classes()
            .withNameEndingWith("Entity")
            .assertTrue { it.packagee?.name?.contains("domain") == true }
    }
}
