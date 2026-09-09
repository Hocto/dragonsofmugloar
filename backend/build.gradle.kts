plugins {
    java
    id("org.springframework.boot") version "3.3.4"
    id("io.spring.dependency-management") version "1.1.6"
}

group = "com.mugloar"
version = "1.0.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Web starter gives us RestClient for the Mugloar calls, MVC for our own API,
    // and SseEmitter for the per-turn stream. All three, one dependency.
    implementation("org.springframework.boot:spring-boot-starter-web")
    // Bean Validation on the request bodies the browser sends us.
    implementation("org.springframework.boot:spring-boot-starter-validation")
    // Only for /actuator/health, which docker-compose uses as the healthcheck
    // so the frontend container waits for a backend that can actually serve.
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    // WireMock stands in for the Mugloar API so no test touches the network.
    testImplementation("org.wiremock:wiremock-standalone:3.9.1")
}

tasks.withType<JavaCompile> {
    options.compilerArgs.add("-parameters")
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

// ./gradlew :backend:benchmark -Pgames=100 -Pstrategy=expected-value
tasks.register<JavaExec>("benchmark") {
    group = "verification"
    description = "Plays N games headless and prints score statistics."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "com.mugloar.MugloarApplication"
    args = listOf("--spring.profiles.active=benchmark")
    val games = project.findProperty("games")?.toString()
    val strategy = project.findProperty("strategy")?.toString()
    val concurrency = project.findProperty("concurrency")?.toString()
    if (games != null) args("--mugloar.benchmark.games=$games")
    if (strategy != null) args("--mugloar.strategy.name=$strategy")
    if (concurrency != null) args("--mugloar.benchmark.concurrency=$concurrency")
    // -Pturnlog=true prints one line per turn, which is how the risk-scale priors were measured.
    if (project.findProperty("turnlog") == "true") {
        args("--logging.level.com.mugloar.application=INFO")
    }
}
