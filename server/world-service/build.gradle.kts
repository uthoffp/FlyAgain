plugins {
    application
}

application {
    mainClass.set("com.flyagain.world.WorldServiceMainKt")
}

dependencies {
    implementation(project(":common"))
    implementation(libs.grpc.netty)
    implementation(libs.java.jwt)
}
