plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.protobuf) apply false
}

allprojects {
    group = "com.flyagain"
    version = "0.1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")

    dependencies {
        "implementation"(rootProject.libs.coroutines.core)
        "implementation"(rootProject.libs.typesafe.config)
        "implementation"(rootProject.libs.slf4j.api)
        "implementation"(rootProject.libs.logback)

        "testImplementation"(kotlin("test"))
        "testImplementation"(rootProject.libs.coroutines.test)
    }

    configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> {
        jvmToolchain(21)
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}
