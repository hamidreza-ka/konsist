package ir.tapsell.konsist.rules

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.ext.list.withAnnotationOf
import com.lemonappdev.konsist.api.verify.assertTrue
import ir.beigirad.junitbaselineextension.BaselineExtension
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.RestController

/**
 * Naming conventions for Spring stereotypes.
 *
 * NOTE: requires Spring on the test classpath — `@Service`, `@Repository`,
 * and `@RestController` are matched by *annotation*, so an unannotated class
 * named `FooController` is intentionally NOT checked here (see
 * [PackageStructureRulesTest] / [ImmutabilityRulesTest] for name-based rules).
 */
@Tag("konsist-naming")
@ExtendWith(BaselineExtension::class)
class NamingConventionRulesTest {

    @Test
    fun `services must contains Service`() {
        Konsist.scopeFromProduction()
            .classes()
            .withAnnotationOf(Service::class)
            .assertTrue { it.name.contains("Service") }
    }

    @Test
    fun `controllers must end with Controller`() {
        Konsist.scopeFromProduction()
            .classes()
            .withAnnotationOf(RestController::class)
            .assertTrue { it.name.endsWith("Controller") }
    }

    @Test
    fun `repositories must end with Repository`() {
        Konsist.scopeFromProduction()
            .classes()
            .withAnnotationOf(Repository::class)
            .assertTrue { it.name.endsWith("Repository") }
    }
}