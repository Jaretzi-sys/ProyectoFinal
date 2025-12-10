package com.example.proyectofinal

import com.example.proyectofinal.databinding.ActivityGameBinding
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.parse.ParseUser
import kotlinx.coroutines.launch

// Actividad principal del juego
class GameActivity : AppCompatActivity() {

    // Binding para vistas usando ViewBinding
    private lateinit var binding: ActivityGameBinding
    // Repositorio para acceso a datos
    private val repository = RoomRepository()

    // ViewModel usando delegacion de viewModels
    private val viewModel: GameViewModel by viewModels {
        GameViewModelFactory(repository, ParseUser.getCurrentUser())
    }

    // ID de la sala actual que bien puede ser nula
    private var roomId: String? = null

    // Vibrator lazy initialization para diferentes versiones de Android
    private val vibrator by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    // Objeto companion para constantes y metodos estáticos
    companion object {
        const val EXTRA_ROOM_ID = "ROOM_ID" // Clave para extra del Intent

        // Metodo estatico para iniciar la actividad
        fun start(context: Context, roomId: String, isHost: Boolean = false) {
            val intent = Intent(context, GameActivity::class.java).apply {
                putExtra(EXTRA_ROOM_ID, roomId) // Pasar ID de sala como extra
            }
            context.startActivity(intent) // Iniciar actividad
        }
    }

    // Metodo llamado al crear la actividad
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Inflar layout usando ViewBinding
        binding = ActivityGameBinding.inflate(layoutInflater)
        setContentView(binding.root) // Establecer vista principal

        // Obtener ID de sala del Intent
        roomId = intent.getStringExtra(EXTRA_ROOM_ID)

        // Validar que el ID de sala no sea nulo
        if (roomId == null) {
            Toast.makeText(this, "Error: ID de sala invalido", Toast.LENGTH_SHORT).show()
            finish() // Finalizar actividad si no hay ID valido
            return
        }

        // Configurar componentes de UI
        setupUI()
        // Configurar observadores de datos
        setupObservers()
        // Configurar manejo de boton de retroceso
        setupBackPressHandler()

        // Inicializar juego con ID de sala
        viewModel.initGame(roomId!!)
    }

    // Configurar interfaz de usuario y listeners
    private fun setupUI() {
        // Configurar callback cuando se toca un objetivo en el canvas
        binding.gameCanvasView.onObjectiveTapped = { objectiveId ->
            viewModel.onObjectiveTapped(objectiveId) // Notificar al ViewModel
            vibrateShort() // Activar vibracion corta
        }

        // Listener para boton de copiar codigo
        binding.btnCopyCode.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Room Code", binding.tvRoomCode.text)
            clipboard.setPrimaryClip(clip) // Copiar texto al portapapeles
            Toast.makeText(this, "Codigo copiado", Toast.LENGTH_SHORT).show()
        }

        // Listener para boton de abandonar sala
        binding.btnLeaveRoom.setOnClickListener {
            showLeaveConfirmationDialog() // Mostrar dialogo de confirmación
        }
    }

    // Configurar observadores de datos del ViewModel
    private fun setupObservers() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Observar cambios en el estado de la sala
                launch {
                    viewModel.roomState.collect { roomState ->
                        roomState?.let {
                            updateUI(it) // Actualizar UI cuando cambia el estado
                        }
                    }
                }

                // Observar eventos del juego
                launch {
                    viewModel.gameEvents.collect { event ->
                        event?.let { handleGameEvent(it) } // Manejar eventos del juego
                    }
                }
            }
        }
    }

    // Configurar manejo personalizado del boton de retroceso
    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this) {
            showLeaveConfirmationDialog() // Mostrar dialogo en lugar de salir directamente
        }
    }

    // Actualizar interfaz de usuario segun el estado actual
    // Actualizar interfaz de usuario segun el estado actual
    private fun updateUI(state: RoomState) {
        val currentUserId = ParseUser.getCurrentUser().objectId

        // Debug: imprimir estado actual
        println(" ACTUALIZANDO UI")
        println("   Status: ${state.status}")
        // ... (otros logs) ...
        println("   Current User: $currentUserId")

        // Determinar rol del jugador actual
        val isPlayer1 = state.PlayerID1 == currentUserId
        val hasPlayer2 = state.PlayerID2 != null
        val isPlayer2 = state.PlayerID2 == currentUserId

        // ... (Lógica de actualización de nombres y puntuaciones) ...
        if (isPlayer1) {
            binding.tvPlayer1Name.text = "Tu"
            binding.tvPlayer1Score.text = state.Score1.toString()
            // ... (rest of player 1 logic) ...
            if (hasPlayer2) {
                binding.tvPlayer2Name.text = "Oponente"
                binding.tvPlayer2Score.text = state.Score2.toString()
                binding.tvPlayer2Score.isVisible = true
                binding.tvPlayer2Name.isVisible = true
            } else {
                binding.tvPlayer2Name.text = "Esperando..."
                binding.tvPlayer2Score.text = "0"
                binding.tvPlayer2Score.isVisible = false
            }
        } else if (isPlayer2) {
            binding.tvPlayer1Name.text = "Oponente"
            binding.tvPlayer1Score.text = state.Score1.toString()
            binding.tvPlayer2Name.text = "Tu"
            binding.tvPlayer2Score.text = state.Score2.toString()
            binding.tvPlayer2Score.isVisible = true
            binding.tvPlayer2Name.isVisible = true
        } else {
            binding.tvPlayer1Name.text = "Jugador 1"
            binding.tvPlayer1Score.text = state.Score1.toString()

            if (hasPlayer2) {
                binding.tvPlayer2Name.text = "Jugador 2"
                binding.tvPlayer2Score.text = state.Score2.toString()
                binding.tvPlayer2Score.isVisible = true
                binding.tvPlayer2Name.isVisible = true
            } else {
                binding.tvPlayer2Name.text = "Esperando..."
                binding.tvPlayer2Score.text = "0"
                binding.tvPlayer2Score.isVisible = false
            }
        }

        // Mostrar codigo de sala
        binding.tvRoomCode.text = state.roomCode

        // Manejar diferentes estados del juego
        when (state.status) {
            "lobby" -> {
                val playerCountMsg = "Jugadores: ${state.playerCount}/2"
                println("   Lobby - PlayerCount: ${state.playerCount}")

                // Mostrar overlay de carga
                binding.loadingOverlay.isVisible = true
                binding.progressBar.isVisible = true
                binding.gameCanvasView.isVisible = false

                if (state.playerCount < 2) {
                    binding.tvGameStatus.text = "Esperando jugadores... ($playerCountMsg)"
                    println("Esperando mas jugadores...")
                } else {
                    binding.tvGameStatus.text = "Sala llena, iniciando juego..."
                    println("Sala llena, esperando evento START.")
                }

                binding.gameCanvasView.setObjective(null) // Limpiar objetivo actual

                if (!hasPlayer2) {
                    binding.tvPlayer2Score.isVisible = false
                }
            }

            "in_game" -> {
                //Mostrar estado de la partida
                binding.tvGameStatus.text = "¡En partida!"

                binding.loadingOverlay.isVisible = false
                binding.progressBar.isVisible = false
                binding.gameCanvasView.isVisible = true

                binding.tvPlayer1Score.isVisible = true
                binding.tvPlayer2Score.isVisible = hasPlayer2

                binding.gameCanvasView.setObjective(state.currentObjective) // Establecer objetivo

                println("En juego - Ronda: ${state.round}/${state.maxRounds}") // Log mantiene info
            }

            "finished" -> {
                binding.tvGameStatus.text = "Partida finalizada"
                binding.loadingOverlay.isVisible = false
                binding.progressBar.isVisible = false
                binding.gameCanvasView.isVisible = false
                binding.gameCanvasView.setObjective(null)

                println("Partida finalizada")
            }

            else -> {
                binding.tvGameStatus.text = "Estado: ${state.status}"
                println("Estado desconocido: ${state.status}")
            }
        }

        // Controlar visibilidad de elementos del jugador 2
        binding.tvPlayer2Name.isVisible = hasPlayer2 || state.status == "lobby"
        binding.tvPlayer2Score.isVisible = hasPlayer2 && state.status != "lobby"
    }

    // Manejar eventos del juego
    private fun handleGameEvent(event: GameEvent) {
        when (event) {
            is GameEvent.GameEnded -> {
                showGameEndDialog(event) // Mostrar dialogo de fin de juego
            }
            is GameEvent.Error -> {
                Toast.makeText(this, event.message, Toast.LENGTH_SHORT).show() // Mostrar error
            }
        }
    }

    // Mostrar dialogo de fin de juego con resultados
    private fun showGameEndDialog(event: GameEvent.GameEnded) {
        val currentUserId = ParseUser.getCurrentUser().objectId
        val isWinner = event.winnerId == currentUserId

        // imprimir informacion del fin de juego
        println("MOSTRANDO DIALOGO DE FIN DE JUEGO")
        println("Current User: $currentUserId")
        println("Winner ID: ${event.winnerId}")
        println("¿Soy ganador?: $isWinner")
        println("Winner Score: ${event.winnerScore}")
        println("Loser Score: ${event.loserScore}")

        // Calcular puntuaciones
        val myScore = if (isWinner) event.winnerScore else event.loserScore
        val opponentScore = if (isWinner) event.loserScore else event.winnerScore

        // Crear y mostrar dialogo de victoria o derrota
        AlertDialog.Builder(this)
            .setTitle(if (isWinner) "VICTORIA!" else "DERROTA")
            .setMessage("""Puntuacion final:
            |Tu: $myScore puntos
            |Oponente: $opponentScore puntos
        """.trimMargin())
            .setCancelable(false)
            .setPositiveButton("Volver al menu") { _, _ ->
                finish() // Finalizar actividad
            }
            .setNeutralButton("Revancha") { _, _ ->
                requestRematch() // Solicitar revancha
            }
            .show()
        vibrateMedium() // Vibrar al mostrar diqlogo
    }

    // Solicitar una revancha creando nueva sala
    private fun requestRematch() {
        val roomCode = (1000..9999).random().toString() // Generar codigo aleatorio
        val currentUser = ParseUser.getCurrentUser() ?: return

        lifecycleScope.launch {
            repository.createRoom(roomCode, currentUser).fold(
                onSuccess = { newRoomId ->
                    // Mostrar dialogo con nuevo codigo de sala
                    AlertDialog.Builder(this@GameActivity)
                        .setTitle("Revancha!")
                        .setMessage("Sala creada: $roomCode\n\nComparte este codigo con tu oponente.")
                        .setPositiveButton("Copiar codigo") { _, _ ->
                            // Copiar codigo al portapapeles
                            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Room Code", roomCode)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(this@GameActivity, "Codigo copiado!", Toast.LENGTH_SHORT).show()

                            finish() // Finalizar actividad actual
                            start(this@GameActivity, newRoomId) // Iniciar nueva sala
                        }
                        .setNegativeButton("Cancelar") { _, _ -> finish() }
                        .show()
                },
                onFailure = { error ->
                    Toast.makeText(this@GameActivity, "Error al crear revancha: ${error.message}", Toast.LENGTH_LONG).show()
                    finish()
                }
            )
        }
    }

    // Mostrar dialogo de confirmacion para abandonar sala
    private fun showLeaveConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Salir de la sala")
            .setMessage("¿Estas seguro de que quieres abandonar la partida?")
            .setPositiveButton("Si") { _, _ ->
                viewModel.leaveRoom() // Notificar al ViewModel
                finish() // Finalizar actividad
            }
            .setNegativeButton("No", null)
            .show()
    }

    // Funcion para vibrar cortamente
    private fun vibrateShort() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (vibrator.hasVibrator()) {
                vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        } else {
            @Suppress("DEPRECATION")
            if (vibrator.hasVibrator()) {
                vibrator.vibrate(50) // Metodo deprecado para versiones anteriores
            }
        }
    }

    // Funcion para vibrar medianamente
    private fun vibrateMedium() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (vibrator.hasVibrator()) {
                vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        } else {
            @Suppress("DEPRECATION")
            if (vibrator.hasVibrator()) {
                vibrator.vibrate(100) // Metodo deprecado para versiones anteriores
            }
        }
    }
}