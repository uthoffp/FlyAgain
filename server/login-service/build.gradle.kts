plugins {
    application
}

application {
    mainClass.set("com.flyagain.login.LoginServiceMainKt")
}

dependencies {
    implementation(project(":common"))
    implementation(libs.bcrypt)
    implementation(libs.java.jwt)
    implementation(libs.grpc.netty)
}
