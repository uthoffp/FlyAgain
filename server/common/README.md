# common

Shared library consumed by all four microservices.

## Responsibilities

- **Protocol Buffers / gRPC stubs** — Generates Java and Kotlin stubs from the shared `.proto` definitions in `shared/proto/`. This includes all client-facing messages (`flyagain.proto`) and inter-service gRPC definitions (`internal.proto`).
- **ConfigHelper** — Utility for loading Typesafe Config with safe default-value accessors (`getStringOrDefault`, `getIntOrDefault`).
- **RedisClientFactory** — Factory for creating Lettuce Redis clients and connections used by login-service, account-service, and world-service.

## Key Packages

| Package | Purpose |
|---|---|
| `com.flyagain.common.config` | Configuration loading utilities |
| `com.flyagain.common.redis` | Redis client factory |
| `com.flyagain.common.proto` | Generated protobuf message classes |
| `com.flyagain.common.grpc` | Generated gRPC service stubs |

## Build

```bash
cd server && ./gradlew :common:build
```

This module has no `main` class — it is a library dependency only.
