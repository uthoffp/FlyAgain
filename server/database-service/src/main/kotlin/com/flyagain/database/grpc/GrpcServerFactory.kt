package com.flyagain.database.grpc

import io.grpc.Server
import io.grpc.ServerBuilder
import org.slf4j.LoggerFactory

object GrpcServerFactory {

    private val logger = LoggerFactory.getLogger(GrpcServerFactory::class.java)

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
