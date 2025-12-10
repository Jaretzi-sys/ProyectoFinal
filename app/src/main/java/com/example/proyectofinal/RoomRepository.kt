package com.example.proyectofinal

import com.parse.ParseCloud
import com.parse.ParseObject
import com.parse.ParseQuery
import com.parse.ParseUser
import com.parse.livequery.ParseLiveQueryClient
import com.parse.livequery.SubscriptionHandling
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import kotlin.coroutines.resume

// Repositorio para operaciones con salas de juego
class RoomRepository {

    // Obtener cliente de LiveQuery
    private val liveQueryClient: ParseLiveQueryClient
        get() = ParseInt.liveQueryClient

    // FUNCION PARA CREAR SALA llama a 'createOrJoinRoom' en Cloud Code
    suspend fun createRoom(code: String, player: ParseUser): Result<String> =
        suspendCancellableCoroutine { continuation ->
            // Preparar parametros para Cloud Function
            val params = hashMapOf<String, Any>("code" to code)

            // Llamar funcion Cloud de Parse
            ParseCloud.callFunctionInBackground<HashMap<String, String>>(
                "createOrJoinRoom", // Nombre de la funcion Cloud
                params
            ) { result, e ->
                if (e == null && result != null) {
                    val roomId = result["roomId"] ?: ""
                    println("createRoom exitoso - RoomID: $roomId")
                    continuation.resume(Result.success(roomId))
                } else {
                    println("Error en createRoom: ${e?.message}")
                    continuation.resume(Result.failure(e ?: Exception("Error al crear/unirse a sala")))
                }
            }
        }

    // FUNCION PARA UNIRSE A SALA EXISTENTE llama a 'joinExistingRoom' en Cloud Code
    suspend fun joinExistingRoom(code: String, player: ParseUser): Result<String> =
        suspendCancellableCoroutine { continuation ->
            // Preparar parametros para Cloud Function
            val params = hashMapOf<String, Any>("code" to code)

            // Llamar funcion Cloud de Parse
            ParseCloud.callFunctionInBackground<HashMap<String, String>>(
                "joinExistingRoom", // Nombre de la nueva funcion Cloud
                params
            ) { result, e ->
                if (e == null && result != null) {
                    val roomId = result["roomId"] ?: ""
                    println("joinExistingRoom exitoso - RoomID: $roomId")
                    continuation.resume(Result.success(roomId))
                } else {
                    // Si la sala no existe, el Cloud Code lanza un error
                    println("Error en joinExistingRoom: ${e?.message}")
                    continuation.resume(Result.failure(e ?: Exception("Error al unirse a sala")))
                }
            }
        }

    // Funcion para registrar golpe a objetivo
    suspend fun hitTarget(
        roomId: String,
        spawnId: String,
        playerId: String
    ): Result<Unit> = suspendCancellableCoroutine { continuation ->
        // Preparar parametros para Cloud Function
        val params = hashMapOf<String, Any>(
            "roomId" to roomId,
            "spawnId" to spawnId,
            "player" to playerId
        )

        // Llamar funcion Cloud de Parse
        ParseCloud.callFunctionInBackground<Any>("hitTarget", params) { _, e ->
            if (e == null) {
                continuation.resume(Result.success(Unit))
            } else {
                continuation.resume(Result.failure(e))
            }
        }
    }

    // Obtener estado actual de una sala
    suspend fun getRoomState(roomId: String): Result<JSONObject> =
        suspendCancellableCoroutine { continuation ->
            // Preparar parametros para Cloud Function
            val params = hashMapOf<String, Any>("roomId" to roomId)

            // Llamar funcion Cloud de Parse
            ParseCloud.callFunctionInBackground<HashMap<String, Any>>(
                "getRoomState",
                params
            ) { result, e ->
                if (e == null && result != null) {
                    val json = JSONObject(result) // Convertir a JSONObject
                    println("getRoomState exitoso: $json")
                    continuation.resume(Result.success(json))
                } else {
                    println("Error en getRoomState: ${e?.message}")
                    continuation.resume(Result.failure(e ?: Exception("Error al obtener estado")))
                }
            }
        }

    // Suscribirse a eventos de una sala específica
    fun subscribeToRoomEvents(roomId: String): Flow<ParseObject> = callbackFlow {
        println("SUSCRIBIENDOSE A EVENTOS: $roomId")

        // Crear query para eventos de la sala
        val query = ParseQuery.getQuery<ParseObject>("Event")
            .whereEqualTo("room", ParseObject.createWithoutData("Room", roomId))
            .orderByDescending("createdAt") // Ordenar por mas reciente

        // Suscribir INMEDIATAMENTE sin delay
        val subscription: SubscriptionHandling<ParseObject> = liveQueryClient.subscribe(query)

        // Manejar evento CREATE
        subscription.handleEvent(SubscriptionHandling.Event.CREATE) { _, event ->
            val eventType = event.getString("type") ?: "UNKNOWN"
            println("EVENTO CREATE CAPTURADO: $eventType")
            println(" - Room: $roomId")
            println(" - Event ID: ${event.objectId}")
            println(" - Timestamp: ${event.createdAt}")

            // Enviar evento al Flow
            val sent = trySend(event)
            println("   - Enviado al Flow: ${sent.isSuccess}")
        }

        // capturar eventos UPDATE
        subscription.handleEvent(SubscriptionHandling.Event.UPDATE) { _, event ->
            val eventType = event.getString("type") ?: "UNKNOWN"
            println("Evento UPDATE recibido: $eventType")
            trySend(event)
        }

        println("Suscripcion a eventos ACTIVA para sala: $roomId")

        awaitClose {
            // Cerrar suscripcion cuando Flow se cancela
            println("Cerrando suscripcion de eventos para sala: $roomId")
            liveQueryClient.unsubscribe(query)
        }
    }

    // Suscribirse a actualizaciones de una sala especifica
    fun subscribeToRoomUpdates(roomId: String): Flow<ParseObject> = callbackFlow {
        println("Suscribiéndose a actualizaciones de Room: $roomId")

        // Crear query para la sala especifica
        val query = ParseQuery.getQuery<ParseObject>("Room")
            .whereEqualTo("objectId", roomId)

        // Suscribir antes de hacer fetch
        val subscription: SubscriptionHandling<ParseObject> = liveQueryClient.subscribe(query)

        // Manejar evento UPDATE de la sala
        subscription.handleEvent(SubscriptionHandling.Event.UPDATE) { _, room ->
            println("Room UPDATE recibido - Players: ${room.getList<String>("players")?.size}, State: ${room.getString("state")}")
            trySend(room)
        }

        // Manejar evento ENTER nuevo jugador
        subscription.handleEvent(SubscriptionHandling.Event.ENTER) { _, room ->
            println("Room ENTER recibido - Players: ${room.getList<String>("players")?.size}")
            trySend(room)
        }

        // FETCH INICIAL CON RETRY despues de pequeño delay
        kotlinx.coroutines.delay(100) // Pequeño delay para que la suscripcion este activa

        var retries = 0
        while (retries < 3) {
            try {
                println("Obteniendo estado inicial de Room (intento ${retries + 1})...")
                // Obtener estado inicial de la sala
                val initialRoom = ParseQuery.getQuery<ParseObject>("Room")
                    .whereEqualTo("objectId", roomId)
                    .first

                if (initialRoom != null) {
                    println("Estado inicial obtenido - Players: ${initialRoom.getList<String>("players")?.size}, State: ${initialRoom.getString("state")}")
                    trySend(initialRoom)
                    break
                }
            } catch (e: Exception) {
                println("Error en fetch inicial (intento ${retries + 1}): ${e.message}")
                retries++
                if (retries < 3) {
                    kotlinx.coroutines.delay(500) // Esperar antes de reintentar
                }
            }
        }

        awaitClose {
            // Cerrar suscripcion cuando Flow se cancela
            println("Cerrando suscripcion de Room")
            liveQueryClient.unsubscribe(query)
        }
    }

    // Obtener historial de partidas de un jugador
    suspend fun getGameHistory(player: ParseUser): Result<List<ParseObject>> =
        suspendCancellableCoroutine { continuation ->
            // Crear query para historial de partidas
            val query = ParseQuery.getQuery<ParseObject>("MatchHistory")
                .whereEqualTo("PlayerID1", player) // Buscar partidas del jugador
                .orderByDescending("matchDate") // Ordenar por fecha descendente
                .setLimit(50) // Limitar a 50 resultados

            // Ejecutar query asincrona
            query.findInBackground { results, e ->
                if (e == null) {
                    continuation.resume(Result.success(results ?: emptyList()))
                } else {
                    continuation.resume(Result.failure(e))
                }
            }
        }
}
