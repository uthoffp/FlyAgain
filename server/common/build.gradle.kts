plugins {
    alias(libs.plugins.protobuf)
}

dependencies {
    api(libs.protobuf.kotlin)
    api(libs.protobuf.java)
    api(libs.grpc.protobuf)
    api(libs.grpc.stub)
    api(libs.grpc.kotlin.stub)
    api(libs.netty.all)
    api(libs.lettuce)
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:4.29.3"
    }
    plugins {
        create("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:1.62.2"
        }
        create("grpckt") {
            artifact = "io.grpc:protoc-gen-grpc-kotlin:1.4.1:jdk8@jar"
        }
    }
    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                named("java")
                create("kotlin")
            }
            task.plugins {
                create("grpc")
                create("grpckt")
            }
        }
    }
}

// Disable build cache for proto generation — the protobuf-gradle-plugin
// does not correctly restore the grpc output directory from cache, causing
// "Unresolved reference 'AccountDataServiceGrpc'" errors.
tasks.withType<com.google.protobuf.gradle.GenerateProtoTask> {
    outputs.cacheIf { false }
}

sourceSets {
    main {
        proto {
            srcDir("../../shared/proto")
        }
    }
}
