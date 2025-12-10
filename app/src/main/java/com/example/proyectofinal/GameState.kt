package com.example.proyectofinal

import com.parse.ParseObject
import org.json.JSONObject
import java.util.Date

// Clase que representa el estado de un objetivo en el juego
data class ObjectiveState(
    val objectId: String, // ID unico del objetivo
    val normalizedX: Double, // Posición X
    val normalizedY: Double, // Posición Y
    val radius: Double, // Radioo del objetivo
    val creationTime: Date, // Fecha de creacion
    val isHit: Boolean = false // Indica si el objetivo fue golpeado
)

// Clase que representa el estado completo de una sala de juego
data class RoomState(
    val roomCode: String, // Codigo de la sala
    val status: String, // Estado actual: "lobby", "in_game", "finished"
    val Score1: Int, // Puntuacion del jugador 1
    val Score2: Int, // Puntuacion del jugador 2
    val PlayerID1: String, // ID del jugador 1
    val PlayerID2: String?, // ID del jugador 2 esta puede ser nulo
    val currentObjective: ObjectiveState? = null, // Objetivo actual es null si no hay
    val round: Int = 0, // Ronda actual
    val maxRounds: Int = 5, // Maximo de rondas
    val playerCount: Int = 0 // Numero de jugadores en sala
)

// Funcion de extension para convertir ParseObject a RoomState
fun ParseObject.toRoomState(currentUserId: String): RoomState {
    // Obtener objeto JSON de puntuaciones
    val scoreObject = this.getJSONObject("score") ?: JSONObject()
    // Obtener lista de jugadores
    val players = this.getList<String>("players") ?: emptyList()
    // Obtener IDs de jugadores
    val player1Id = players.getOrNull(0) ?: currentUserId
    val player2Id = players.getOrNull(1)
    // Obtener puntuaciones individuales
    val score1 = scoreObject.optInt(player1Id, 0)
    val score2 = if (player2Id != null) scoreObject.optInt(player2Id, 0) else 0

    // Crear y retornar RoomState
    return RoomState(
        roomCode = this.getString("code") ?: "",
        status = this.getString("state") ?: "lobby",
        Score1 = score1,
        Score2 = score2,
        PlayerID1 = player1Id,
        PlayerID2 = player2Id,
        currentObjective = null,
        round = this.getInt("round"),
        maxRounds = this.getInt("maxRounds"),
        playerCount = this.getInt("playerCount")
    )
}

// Funcion para convertir payload de spawn a ObjectiveState
fun spawnPayloadToObjective(payload: JSONObject, width: Int, height: Int): ObjectiveState {
    // Obtener coordenadas y radio del payload
    val cx = payload.getDouble("cx")
    val cy = payload.getDouble("cy")
    val r = payload.getDouble("r")
    // Calcular dimension
    val minDimension = width.coerceAtMost(height)
    // Crear y retornar ObjectiveState
    return ObjectiveState(
        objectId = payload.getString("spawnId"),
        normalizedX = cx / width,
        normalizedY = cy / height,
        radius = r / minDimension,
        creationTime = Date(),
        isHit = false
    )
}
