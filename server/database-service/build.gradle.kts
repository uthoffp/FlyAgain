plugins {
    application
}

application {
    mainClass.set("com.flyagain.database.DatabaseServiceMainKt")
}

dependencies {
    implementation(project(":common"))
    implementation(libs.postgresql)
    implementation(libs.hikari)
    implementation(libs.flyway.core)
    implementation(libs.flyway.postgresql)
    implementation(libs.grpc.netty)
}
