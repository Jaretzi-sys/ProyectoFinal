package com.example.proyectofinal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parse.ParseUser
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

// ViewModel principal del juego
class GameViewModel(
    private val repository: RoomRepository, // Repositorio para datos
    private val currentUser: ParseUser // Usuario actual
) : ViewModel() {

    // StateFlow para estado de la sala es observable
    private val _roomState = MutableStateFlow<RoomState?>(null)
    val roomState: StateFlow<RoomState?> = _roomState

    // StateFlow para eventos del juego es un observable
    private val _gameEvents = MutableStateFlow<GameEvent?>(null)
    val gameEvents: StateFlow<GameEvent?> = _gameEvents

    // Variables de control
    private var roomId: String? = null
    private var eventSubscriptionJob: Job? = null
    private var roomSubscriptionJob: Job? = null

    private var lobbyPollingJob: Job? = null // Job para polling en lobby

    // Inicializar juego con ID de sala
    fun initGame(roomId: String) {
        this.roomId = roomId
        println("initGame: Inicializando sala $roomId")
        println("Usuario actual: ${currentUser.objectId}")

        // Suscripcion a eventos (Eventos rapidos: SCORE, SPAWN, END)
        eventSubscriptionJob = viewModelScope.launch {
            println("[1/3] Suscribiendose a eventos (PRIMERO)...")
            // Suscribirse a eventos de sala sin delay
            repository.subscribeToRoomEvents(roomId).collect { event ->
                val eventType = event.getString("type")
                println("Evento recibido: $eventType")
                handleEvent(event) // Manejar evento recibido
            }
        }

        // Suscripcion a actualizaciones de sala (Estado completo: Ronda, Status, Scores)
        roomSubscriptionJob = viewModelScope.launch {
            println("[2/3] Suscribiendose a Room updates...")
            repository.subscribeToRoomUpdates(roomId).collect { room ->
                // Obtener datos de la sala
                val state = room.getString("state")

                // **LOG DE VERIFICACIÓN DE RONDA**
                println("Room UPDATE recibido:")
                println(" - State: $state")
                println(" - Round: ${room.getNumber("round")?.toInt()}") // Verificar si llega la ronda

                // Detener polling si el juego ya comenzo
                if (state == "in_game" || state == "finished") {
                    stopLobbyPolling()
                }

                // Convertir a RoomState y actualizar.
                // El RoomState generado aquí contendrá la ronda actualizada del servidor (la fuente de verdad).
                val roomState = room.toRoomState(currentUser.objectId)
                _roomState.value = roomState

                println("RoomState actualizado en ViewModel (incluyendo scores, ronda, etc.)")
            }
        }

        // Obtener estado inicial
        viewModelScope.launch {
            // Delay para asegurar que las suscripciones esten activas
            kotlinx.coroutines.delay(500)

            println("[3/3] Obteniendo estado inicial (despues de suscripciones)...")
            repository.getRoomState(roomId).onSuccess { stateJson ->
                // imprimir estado inicial
                println("Estado inicial recibido:")
                println(" - Status: ${stateJson.optString("status")}")

                // Actualizar estado desde JSON
                updateStateFromJson(stateJson)

                // Iniciar polling si estamos en lobby
                val status = stateJson.optString("status", "lobby")
                if (status == "lobby") {
                    println("Estado = lobby, iniciando polling")
                    startLobbyPolling()
                }
            }.onFailure { error ->
                println("Error obteniendo estado: ${error.message}")
            }
        }
    }

    // Iniciar polling periodico para estado de lobby
    private fun startLobbyPolling() {
        println("INICIANDO POLLING EN LOBBY")
        stopLobbyPolling() // Detener polling anterior si existe

        lobbyPollingJob = viewModelScope.launch {
            var pollCount = 0
            while (true) {
                kotlinx.coroutines.delay(1000) // Esperar 1 segundo
                pollCount++

                val currentState = _roomState.value
                // Salir si ya no estamos en lobby
                if (currentState?.status != "lobby") {
                    println("Polling detenido - Estado: ${currentState?.status}")
                    break
                }

                println("Poll #$pollCount - Verificando estado de sala...")

                roomId?.let { id ->
                    repository.getRoomState(id).onSuccess { stateJson ->
                        // Obtener datos actualizados
                        val playerCount = stateJson.optInt("playerCount", 0)
                        val status = stateJson.optString("status", "lobby")

                        // Actualizar si hay cambios
                        if (currentState.playerCount != playerCount || currentState.status != status) {
                            println("CAMBIO DETECTADO! Actualizando UI")
                            updateStateFromJson(stateJson)
                        }

                        // Detener polling si el juego inicia
                        if (status == "in_game" || status == "finished") {
                            println("Juego iniciado, deteniendo polling")
                            stopLobbyPolling()
                        }
                    }.onFailure { error ->
                        println("Error en poll #$pollCount: ${error.message}")
                    }
                }
            }
        }
    }

    // Detener polling de lobby
    private fun stopLobbyPolling() {
        lobbyPollingJob?.cancel()
        lobbyPollingJob = null
        println("Polling detenido")
    }

    // Manejar eventos recibidos de Parse
    private fun handleEvent(eventObject: com.parse.ParseObject) {
        val type = eventObject.getString("type") ?: return
        val payload = eventObject.getJSONObject("payload") ?: JSONObject()

        println("Procesando evento: $type")
        println("Payload: $payload")

        when (type) {
            "START" -> {
                println("¡Juego iniciado!")
                // Actualizar estado a "in_game"
                val currentState = _roomState.value
                if (currentState != null) {
                    _roomState.value = currentState.copy(status = "in_game")
                }
            }

            "SPAWN" -> {
                val currentState = _roomState.value ?: return

                // Crear nuevo objetivo desde payload
                val objective = spawnPayloadToObjective(
                    payload,
                    1080, // Ancho de referencia
                    1920  // Alto de referencia
                )

                // Actualizar estado con nuevo objetivo
                _roomState.value = currentState.copy(currentObjective = objective)
                println("Nuevo objetivo: ${objective.objectId}")
            }

            "SCORE" -> {
                // El servidor indica que una ronda termino
                val winner = payload.optString("winner", "")
                val scoreObj = payload.optJSONObject("score") ?: JSONObject()

                // La ronda enviada en el evento SCORE representa la ronda que acaba de finalizar
                val roundFromEvent = payload.optInt("round", 0)

                val currentState = _roomState.value ?: return

                // Obtener los IDs de los jugadores desde el estado actual
                val player1Id = currentState.PlayerID1
                val player2Id = currentState.PlayerID2

                // Obtener los nuevos puntajes enviados por el servidor para cada jugador
                val newScore1 = scoreObj.optInt(player1Id, currentState.Score1)
                val newScore2 = if (player2Id != null) {
                    scoreObj.optInt(player2Id, currentState.Score2)
                } else {
                    currentState.Score2
                }

                println("Actualizando scores tras SCORE event:")
                println("   - Ronda terminada (Server): $roundFromEvent")

                //Actualizacion de los puntajes
                _roomState.value = currentState.copy(
                    Score1 = newScore1,
                    Score2 = newScore2
                )

                println("Objetivo golpeado por: $winner. Scores actualizados. Objetivo se mantiene hasta Room Update.")

                // Room Update es el encargado de actualizar la ronda y asignar un nuevo objetivo
            }

            "END" -> {
                println("EVENTO END RECIBIDO EN JUGADOR: ${currentUser.objectId}")

                // Obtener datos del evento
                val champion = payload.optString("champion", "")
                val scoreObj = payload.optJSONObject("score") ?: JSONObject()

                // Obtener todos los scores directamente del servidor
                val allScores = mutableMapOf<String, Int>()
                scoreObj.keys().forEach { playerId ->
                    allScores[playerId] = scoreObj.optInt(playerId, 0)
                }

                // Determinar el ganador usando TODOS los scores del servidor
                val actualWinnerId = champion.takeIf { it.isNotEmpty() } ?: run {
                    // Fallback: calcular ganador si el servidor no lo proporciona
                    allScores.maxByOrNull { it.value }?.key ?: currentUser.objectId
                }

                // Obtener scores del ganador y perdedor
                val winnerScore = allScores[actualWinnerId] ?: 0
                val loserScore = allScores.filterKeys { it != actualWinnerId }
                    .values.maxOrNull() ?: 0

                println("Ganador Final: $actualWinnerId")

                // Emitir evento de fin de juego
                _gameEvents.value = GameEvent.GameEnded(
                    winnerId = actualWinnerId,
                    winnerScore = winnerScore,
                    loserScore = loserScore
                )

                // Actualizar estado a "finished"
                val currentState = _roomState.value
                currentState?.let {
                    _roomState.value = it.copy(
                        status = "finished",
                        Score1 = allScores[it.PlayerID1] ?: it.Score1,
                        Score2 = if (it.PlayerID2 != null) allScores[it.PlayerID2] ?: it.Score2 else it.Score2
                    )
                }
            }

            "PLAYER_JOINED" -> {
                println("PLAYER_JOINED recibido")
                val playerCount = payload.optInt("playerCount", 0)
                val playersArray = payload.optJSONArray("players")

                // Actualizar estado inmediatamente con nuevos jugadores
                val currentState = _roomState.value
                if (currentState != null && playersArray != null) {
                    val player1 = playersArray.optString(0)
                    val player2 = if (playersArray.length() > 1) playersArray.optString(1) else null

                    _roomState.value = currentState.copy(
                        PlayerID1 = player1,
                        PlayerID2 = player2,
                        playerCount = playerCount
                    )

                    // Si sala esta llena pero aun en lobby, esperar START
                    if (playerCount == 2 && currentState.status == "lobby") {
                        println("Sala llena, esperando evento START...")
                    }
                }
            }
        }
    }

    // Actualizar estado desde JSON
    private fun updateStateFromJson(json: JSONObject) {
        val scoreObj = json.optJSONObject("score") ?: JSONObject()
        val players = json.optJSONArray("players")

        // Determinar IDs de jugadores
        val player1Id = players?.optString(0) ?: currentUser.objectId
        val player2Id = if (players != null && players.length() > 1) {
            players.optString(1)
        } else null

        // Crear nuevo RoomState
        val state = RoomState(
            roomCode = json.optString("code", ""),
            status = json.optString("status", "lobby"),
            Score1 = scoreObj.optInt(player1Id, 0),
            Score2 = if (player2Id != null) scoreObj.optInt(player2Id, 0) else 0,
            PlayerID1 = player1Id,
            PlayerID2 = player2Id,
            currentObjective = json.optJSONObject("lastSpawn")?.let { spawn ->
                spawnPayloadToObjective(spawn, 1080, 1920)
            },
            round = json.optInt("round", 0),
            maxRounds = json.optInt("maxRounds", 5),
            playerCount = json.optInt("playerCount", 0)
        )

        // Actualizar StateFlow
        _roomState.value = state
        println("RoomState inicial establecido")
    }

    // Manejar toque en objetivo
    fun onObjectiveTapped(objectiveId: String) {
        viewModelScope.launch {
            val roomId = this@GameViewModel.roomId ?: return@launch

            // Registrar golpe en repositorio
            repository.hitTarget(
                roomId = roomId,
                spawnId = objectiveId,
                playerId = currentUser.objectId
            ).onFailure { error ->
                println("Error al registrar hit: ${error.message}")
                _gameEvents.value = GameEvent.Error("Error al golpear objetivo")
            }
        }
    }

    // Salir de la sala
    fun leaveRoom() {
        println("Saliendo de la sala...")
        eventSubscriptionJob?.cancel()
        roomSubscriptionJob?.cancel()
    }

    // Limpiar recursos cuando ViewModel es destruido
    override fun onCleared() {
        super.onCleared()
        stopLobbyPolling()
        leaveRoom()
    }
}

// Sellado de clases para eventos del juego
sealed class GameEvent {
    // Evento de fin de juego con resultados
    data class GameEnded(
        val winnerId: String,
        val winnerScore: Int,
        val loserScore: Int
    ) : GameEvent()

    // Evento de error
    data class Error(val message: String) : GameEvent()
}