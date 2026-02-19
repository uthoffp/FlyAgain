plugins {
    application
}

application {
    mainClass.set("com.flyagain.account.AccountServiceMainKt")
}

dependencies {
    implementation(project(":common"))
    implementation(libs.grpc.netty)
    implementation(libs.java.jwt)
    implementation(libs.lettuce)
}
