package com.example.proyectofinal

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.proyectofinal.databinding.ActivityHistoryBinding
import com.parse.ParseUser
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

// Activity que muestra el historial de partidas jugadas por el usuario.
class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding
    private val repository = RoomRepository() // Repositorio para operaciones con Parse
    private val dateFormat = SimpleDateFormat("dd/MM/yy HH:mm", Locale.getDefault()) // Formato de fecha

    companion object {
        fun start(context: Context) { // Factory method para iniciar esta Activity
            context.startActivity(Intent(context, HistoryActivity::class.java))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) { // Se llama al crear la Activity
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater) // Inicializa ViewBinding
        setContentView(binding.root) // Establece la vista

        loadHistory() // Carga el historial del usuario
    }

    // Carga el historial de partidas del usuario actual desde Parse.
    private fun loadHistory() {
        val currentUser = ParseUser.getCurrentUser() ?: return

        lifecycleScope.launch {
            repository.getGameHistory(currentUser).fold(
                onSuccess = { historyList ->
                    val displayList = historyList.map { obj ->
                        val currentUserId = currentUser.objectId
                        val winnerId = obj.getParseUser("winnerID")?.objectId

                        val won = winnerId == currentUserId

                        val score = obj.getInt("finalScore1")
                        val opponentScore = obj.getInt("finalScore2")
                        val date = obj.getDate("matchDate")?.let { dateFormat.format(it) } ?: "N/A"

                        val status = if (won) "VICTORIA" else "DERROTA"
                        "$status: $score - $opponentScore (Fecha: $date)"
                    }

                    val adapter = ArrayAdapter(
                        this@HistoryActivity,
                        android.R.layout.simple_list_item_1,
                        displayList
                    )
                    binding.listViewHistory.adapter = adapter
                },
                onFailure = { error ->
                    Toast.makeText(
                        this@HistoryActivity,
                        "Error al cargar historial: ${error.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            )
        }
    }
}
