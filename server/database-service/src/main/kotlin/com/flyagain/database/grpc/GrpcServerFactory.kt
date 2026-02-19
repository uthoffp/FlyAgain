package com.flyagain.database.grpc

import io.grpc.Server
import io.grpc.ServerBuilder
import org.slf4j.LoggerFactory

/**
 * Factory for assembling and configuring the gRPC server.
 *
 * Registers all four domain gRPC services (account, character, inventory,
 * game data) onto a single [Server] instance listening on the configured port.
 */
object GrpcServerFactory {

    private val logger = LoggerFactory.getLogger(GrpcServerFactory::class.java)

    /**
     * Builds a gRPC [Server] with all database service endpoints registered.
     *
     * The returned server is **not started** — call [Server.start] after creation.
     *
     * @param port the TCP port to listen on (configured via `flyagain.grpc.port`)
     * @param accountService handles account CRUD and ban checks
     * @param characterService handles character CRUD, save, and skills
     * @param inventoryService handles inventory, equipment, and gold operations
     * @param gameDataService serves static game definition data
     * @return a configured but not-yet-started gRPC [Server]
     */
    fun create(
        port: Int,
        accountService: AccountGrpcService,
        characterService: CharacterGrpcService,
        inventoryService: InventoryGrpcService,
        gameDataService: GameDataGrpcService
    ): Server {
        logger.info("Creating gRPC server on port {}", port)

        return ServerBuilder.forPort(port)
            .addService(accountService)
            .addService(characterService)
            .addService(inventoryService)
            .addService(gameDataService)
            .build()
    }
}
