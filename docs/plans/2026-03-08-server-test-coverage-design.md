# Server Test Coverage Design

**Date:** 2026-03-08
**Scope:** Unit tests + gRPC service tests for all untested server code

## Overview

Add 15 new test files across 4 modules to close test coverage gaps. All tests use existing patterns: Mockk, `runTest`, backtick test names, `slot<Packet>()` capture for response verification.

## New Test Files

### common (4 tests)

1. **PacketTest** — equality (contentEquals), hashCode consistency, toString format
2. **PacketEncoderTest** — opcode encoding (0, max), empty/large payloads, ByteBuf position
3. **PacketDecoderTest** — undersized buffer (no emit), opcode extraction, payload handling, empty payload
4. **ConnectionLimiterTest** — total limit enforcement, per-IP limit, cleanup on disconnect, zero-count IP removal

### database-service (4 tests — gRPC services with mocked repositories)

5. **AccountGrpcServiceTest** — getByUsername/Id (found/not found), createAccount (success/duplicate exception), checkBan (found/NOT_FOUND exception), updateLastLogin
6. **CharacterGrpcServiceTest** — listByAccount, getCharacter with ownership check, create (success + 3-char limit), saveCharacter, soft-delete, getSkills
7. **GameDataGrpcServiceTest** — all 5 getAllX methods with empty and populated results
8. **InventoryGrpcServiceTest** — getInventory/Equipment, addItem (success + full inventory), moveItem (success + not found), removeItem, equipItem, unequipItem, NPC stubs return error

### login-service (3 tests)

9. **RegisterHandlerTest** — rate limit rejection, username validation (length, chars), email validation, password validation (length, bcrypt max), gRPC failure, gRPC error response, success path
10. **SessionManagerTest** — createSession, getSession, deleteSession, multi-login prevention (old session deleted), stale reverse-lookup cleanup, Redis failure returns false/null
11. **RateLimiterTest** — login limit (5/60s boundary), register limit (3/3600s boundary), separate IP counters, separate action counters, fail-open on Redis exception, getRemainingAttempts

### world-service (4 tests)

12. **EnterWorldHandlerTest** — invalid JWT rejected, missing Redis cache rejected, account ID mismatch rejected, missing session rejected, duplicate login rejected, zone fallback to Aerheim, success path (entity created + zone data sent + spawn broadcast)
13. **SelectTargetHandlerTest** — clear target (id=0), dead monster rejected, alive monster accepted, player target accepted, invalid zone/channel error, target not found error
14. **UseSkillHandlerTest** — dead player rejected, skill error forwarded, success (response + broadcast), null channel (no broadcast but still success)
15. **ToggleAutoAttackHandlerTest** — disable always succeeds, enable with no target fails, enable with dead monster disables, enable with alive monster succeeds, request sets targetEntityId before validation

## Testing Patterns (match existing codebase)

- **Mocking:** Mockk with `relaxed = true` for ChannelHandlerContext, explicit mocks for business deps
- **Coroutines:** `runTest { }` wrapper for suspend functions, `coEvery`/`coVerify` for suspend mocks
- **Response capture:** `slot<Packet>()` + `verify { ctx.writeAndFlush(capture(slot)) }` + `parseFrom(slot.captured.payload)`
- **Naming:** Backtick-enclosed descriptive phrases
- **Assertions:** `assertEquals`, `assertTrue`, `assertFalse` from `kotlin.test`
- **gRPC mocking:** Mock `CoroutineStub` directly, return protobuf builders
- **Redis mocking:** Mock `StatefulRedisConnection` → `RedisAsyncCommands` with `coEvery`
