plugins {
    kotlin("jvm") version "2.3.0"
    `java-library`
}

group = "ir.tapsell"

repositories {
    mavenCentral()
}

dependencies {
    // Spring annotations are only needed to compile NamingConventionRules;
    // consuming projects supply their own Spring at runtime
    compileOnly(platform("org.springframework:spring-framework-bom:6.2.7"))
    compileOnly("org.springframework:spring-context")
    compileOnly("org.springframework:spring-web")

//    compileOnly("org.junit.jupiter:junit-jupiter-api:6.1.0")
//    testRuntimeOnly("org.junit.jupiter:junit-jupiter:6.1.0")

    // api = transitive to consumers; both are part of the published rule classes' contract
    api("com.lemonappdev:konsist:0.17.3")
    api(platform("org.junit:junit-bom:5.10.2"))
    api("org.junit.jupiter:junit-jupiter")
    // junit-jupiter-engine is runtime-only in the junit-jupiter aggregate; declare explicitly
    // so KonsistTestEngine (which implements TestEngine) can compile against the platform APIs
    api("org.junit.platform:junit-platform-engine")
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform {
        // Prevent the engine from running against the library's own sources.
        // Consumers' test tasks include it by default via ServiceLoader.
        excludeEngines("tapsell-konsist")
    }
}