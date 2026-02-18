plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

rootProject.name = "flyagain-server"

include("common")
include("database-service")
include("login-service")
include("account-service")
include("world-service")
