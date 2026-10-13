plugins {
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.serialization") version "2.0.21"
    id("io.ktor.plugin") version "3.0.3"
}

group = "com.applycopilot"
version = "0.1.0"

application {
    mainClass.set("com.applycopilot.ApplicationKt")
}

kotlin {
    jvmToolchain(21)
}

repositories {
    mavenCentral()
}

val ktorVersion = "3.0.3"

dependencies {
    // HTTP server
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("io.ktor:ktor-server-auth:$ktorVersion")
    implementation("io.ktor:ktor-server-auth-jwt:$ktorVersion")
    implementation("com.auth0:java-jwt:4.4.0")
    implementation("io.ktor:ktor-server-status-pages:$ktorVersion")
    implementation("io.ktor:ktor-server-call-logging:$ktorVersion")

    // HTTP client (Claude API, email provider)
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")

    // Database
    implementation("org.postgresql:postgresql:42.7.4")
    implementation("com.zaxxer:HikariCP:6.2.1")
    implementation("org.flywaydb:flyway-core:10.21.0")
    implementation("org.flywaydb:flyway-database-postgresql:10.21.0")

    // Resume text extraction
    implementation("org.apache.pdfbox:pdfbox:3.0.3")

    // Resume files in Cloudflare R2 (S3-compatible). 2.28.x: later SDKs send checksum headers R2 rejects by default.
    implementation("software.amazon.awssdk:s3:2.28.29") {
        exclude(group = "software.amazon.awssdk", module = "netty-nio-client")
        exclude(group = "software.amazon.awssdk", module = "apache-client")
    }
    implementation("software.amazon.awssdk:url-connection-client:2.28.29")
    implementation("org.apache.poi:poi-ooxml:5.3.0")

    implementation("ch.qos.logback:logback-classic:1.5.12")

    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation("io.ktor:ktor-client-mock:$ktorVersion")
    testImplementation("org.testcontainers:postgresql:1.20.4")
}

tasks.test {
    useJUnitPlatform()
}

// The fat jar (buildFatJar) must merge META-INF/services files: Flyway 10 registers its migration scanners and
// its Postgres support as service plugins in separate jars, and without merging one overwrites the other,
// so the packaged app finds no migrations.
tasks.named("shadowJar") {
    withGroovyBuilder { "mergeServiceFiles"() }
}
