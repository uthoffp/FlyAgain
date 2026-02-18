plugins {
    application
}

application {
    mainClass.set("com.flyagain.account.AccountServiceMainKt")
}

dependencies {
    implementation(project(":common"))
    implementation(libs.netty.all)
    implementation(libs.grpc.netty)
    implementation(libs.java.jwt)
    implementation(libs.lettuce)
}
