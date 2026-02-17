plugins {
    kotlin("jvm") version "2.1.20"
    id("com.google.protobuf") version "0.9.6"
    application
}

group = "com.flyagain"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

application {
    mainClass.set("com.flyagain.server.MainKt")
}

dependencies {
    // Networking
    implementation("io.netty:netty-all:4.1.131.Final")

    // Protobuf
    implementation("com.google.protobuf:protobuf-kotlin:4.29.3")
    implementation("com.google.protobuf:protobuf-java:4.29.3")

    // Database
    implementation("org.postgresql:postgresql:42.7.10")
    implementation("com.zaxxer:HikariCP:6.2.1")

    // Redis
    implementation("io.lettuce:lettuce-core:6.5.2.RELEASE")

    // Auth
    implementation("at.favre.lib:bcrypt:0.10.2")
    implementation("com.auth0:java-jwt:4.5.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")

    // Config
    implementation("com.typesafe:config:1.4.3")

    // Migrations
    implementation("org.flywaydb:flyway-core:11.1.0")
    implementation("org.flywaydb:flyway-database-postgresql:11.1.0")

    // Logging
    implementation("org.slf4j:slf4j-api:2.0.16")
    implementation("ch.qos.logback:logback-classic:1.5.16")

    // Test
    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
}

kotlin {
    jvmToolchain(21)
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:4.29.3"
    }
    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                named("java")
                create("kotlin")
            }
        }
    }
}

sourceSets {
    main {
        proto {
            srcDir("../shared/proto")
        }
    }
}

tasks.test {
    useJUnitPlatform()
}
