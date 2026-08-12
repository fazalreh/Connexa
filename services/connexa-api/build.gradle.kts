plugins {
    java
    id("org.springframework.boot") version "4.1.0"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.connexa"
version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")

    // Durable persistence. The adapters activate only when connexa.persistence.mode=postgres;
    // the default in-memory mode leaves the pool untouched and never opens a connection.
    // Server-side identity verification. Tokens are validated against the provider here;
    // the Android client never asserts its own claims.
    implementation("com.google.firebase:firebase-admin:9.10.0")

    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    // Must be the starter, not bare flyway-core. Spring Boot 4 split autoconfiguration into
    // per-technology modules, so flyway-core alone carries no FlywayAutoConfiguration and
    // migrations would silently never run.
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
    testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    // Adapter tests apply the shipped migration to an embedded engine in PostgreSQL
    // compatibility mode, so the real DDL is exercised without needing a live database.
    testRuntimeOnly("com.h2database:h2")
    // Transaction boundaries cannot be verified against a compatibility mode: they need a
    // real server. This runs an actual PostgreSQL binary as the current user, so the
    // integration suite needs neither Docker nor root.
    testImplementation("io.zonky.test:embedded-postgres:2.2.2")
    testImplementation(platform("io.zonky.test.postgres:embedded-postgres-binaries-bom:18.4.0"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 17
    options.compilerArgs.add("-Xlint:all")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    // Tests must never inherit developer or deployment credentials. Pointing the local
    // config import at a path that cannot exist keeps every run on the fail-closed
    // defaults, so a suite can never read or mutate a real database.
    environment("CONNEXA_LOCAL_ENV", layout.buildDirectory.file("test-no-local-env.properties").get().asFile.path)
    testLogging {
        events("failed", "skipped")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
