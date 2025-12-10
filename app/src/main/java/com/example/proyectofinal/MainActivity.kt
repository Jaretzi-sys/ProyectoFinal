package com.example.proyectofinal

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.proyectofina3.databinding.ActivityMainBinding
import com.parse.ParseUser
import kotlinx.coroutines.launch

// Actividad principal del menu
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding // ViewBinding
    private val repository = RoomRepository() // Repositorio para operaciones de sala

    // Objeto companion para metodo estatico de inicio
    companion object {
        fun start(context: Context) {
            // Crear Intent con flags para limpiar el back stack
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            context.startActivity(intent)
        }
    }

    // Metodo llamado al crear la actividad
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Inflar layout usando ViewBinding
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root) // Establecer vista principal

        // Verificar si hay usuario logueado, si no, ir a Login
        if (ParseUser.getCurrentUser() == null) {
            LoginActivity.start(this)
            finish()
            return
        }

        setupListeners() // listeners de botones
    }

    // Configurar listeners de los botones
    private fun setupListeners() {
        binding.btnCreateRoom.setOnClickListener { createRoom() } // Crear sala
        binding.btnJoinRoom.setOnClickListener { showJoinRoomDialog() } // Unirse a sala
        binding.btnHistory.setOnClickListener { HistoryActivity.start(this) } // Ver historial
        binding.btnLogout.setOnClickListener { logout() } // Cerrar sesion
    }

    // Funcion para crear una nueva sala
    private fun createRoom() {
        // Generar codigo de sala aleatorio de 4 digitos
        val roomCode = (1000..9999).random().toString()
        val currentUser = ParseUser.getCurrentUser() ?: return

        println("Creando sala con codigo: $roomCode")

        lifecycleScope.launch {
            // Llamar al repositorio para crear sala
            repository.createRoom(roomCode, currentUser).fold(
                onSuccess = { roomId ->
                    Toast.makeText(this@MainActivity, "Sala $roomCode creada", Toast.LENGTH_SHORT).show()
                    GameActivity.start(this@MainActivity, roomId) // Iniciar GameActivity
                },
                onFailure = { error ->
                    Toast.makeText(this@MainActivity, "Error: ${error.message}", Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    // Mostrar dialogo para unirse a sala existente
    private fun showJoinRoomDialog() {
        // Crear EditText para entrada de código
        val input = EditText(this).apply {
            hint = "Codigo de Sala (ej: 1234)"
        }

        // Crear AlertDialog
        AlertDialog.Builder(this)
            .setTitle("Unirse a Sala")
            .setView(input)
            .setPositiveButton("Unirse") { _, _ ->
                val code = input.text.toString().trim()
                if (code.length == 4) {
                    joinRoom(code) // Intentar unirse a sala
                } else {
                    Toast.makeText(this, "Codigo invalido (debe ser 4 digitos)", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // Funcion para unirse a sala existente
    private fun joinRoom(roomCode: String) {
        val currentUser = ParseUser.getCurrentUser() ?: return

        lifecycleScope.launch {
            // Llamar al repositorio para unirse a sala
            repository.joinExistingRoom(roomCode, currentUser).fold(
                onSuccess = { roomId ->
                    Toast.makeText(this@MainActivity, "Unido a sala: $roomCode", Toast.LENGTH_SHORT).show()
                    GameActivity.start(this@MainActivity, roomId) // Iniciar GameActivity
                },
                onFailure = { error ->
                    // Mostrar error
                    Toast.makeText(this@MainActivity, "Error: ${error.message}", Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    // Funcion para cerrar sesion
    private fun logout() {
        ParseUser.logOut() // Cerrar sesion en Parse
        LoginActivity.start(this) // Ir a LoginActivity
        finish() // Finalizar MainActivity
    }
}
