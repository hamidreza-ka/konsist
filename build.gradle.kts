plugins {
    kotlin("jvm") version "2.3.0"
    `java-library`
}

group = "ir.tapsell"

repositories {
    mavenCentral()
}

dependencies {
    // api = transitive to consumers; both are part of the published rule classes' contract
    api("com.lemonappdev:konsist:0.17.3")

    // Spring annotations are only needed to compile NamingConventionRules;
    // consuming projects supply their own Spring at runtime
    compileOnly(platform("org.springframework:spring-framework-bom:6.2.7"))
    compileOnly("org.springframework:spring-context")
    compileOnly("org.springframework:spring-web")

    compileOnly("org.junit.jupiter:junit-jupiter-api:6.1.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter:6.1.0")
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}