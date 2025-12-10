package com.example.proyectofinal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.parse.ParseUser

class GameViewModelFactory (
    private val repository: RoomRepository, // Inyecta el repositorio de la sala
    private val currentUser: ParseUser // Inyecta el usuario actual de Parse
) : ViewModelProvider.Factory { // Implementa la interfaz de Factory

    @Suppress("UNCHECKED_CAST") // Suprime la advertencia de casting inseguro
    override fun <T : ViewModel> create(modelClass: Class<T>): T { // Metodo para crear una instancia de ViewModel
        if (modelClass.isAssignableFrom(GameViewModel::class.java)) { // Comprueba si se esta solicitando el GameViewModel
            return GameViewModel(repository, currentUser) as T // Crea y devuelve una instancia del GameViewModel con las dependencias inyectadas
        }
        throw IllegalArgumentException("Unknown ViewModel class") // Lanza una excepcion si se solicita una clase desconocida
    }
}