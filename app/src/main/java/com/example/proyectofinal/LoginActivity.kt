package com.example.proyectofinal

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.proyectofina3.databinding.ActivityLoginBinding
import com.parse.ParseException
import com.parse.ParseObject
import com.parse.ParseQuery
import com.parse.ParseUser
import kotlinx.coroutines.launch

// Actividad de inicio de sesion y registro
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding // ViewBinding

    // Objeto companion para metodo estatico de inicio
    companion object {
        fun start(context: Context) {
            // Crear Intent con flags para limpiar el back stack
            val intent = Intent(context, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            context.startActivity(intent)
        }
    }

    // Metodo llamado al crear la actividad
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Inflar layout usando ViewBinding
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root) // Establecer vista principal

        // Realizar test de conexion con Parse
        testParseConnection()

        // Verificar si ya hay usuario logueado
        if (ParseUser.getCurrentUser() != null) {
            goToMenu() // Ir al menu principal
            return
        }

        setupListeners() // Configurar listeners de botones
    }

    // Test exhaustivo de conexion con Parse
    private fun testParseConnection() {
        lifecycleScope.launch {
            try {
                Toast.makeText(this@LoginActivity, "Probando conexion a Parse...", Toast.LENGTH_SHORT).show()

                // Intento 1: Query simple a tabla de usuarios
                val query = ParseQuery.getQuery<ParseObject>("_User")
                query.limit = 1 // Limitar a 1 resultado
                query.find() // Ejecutar query

                Toast.makeText(this@LoginActivity, "Conexion exitosa!", Toast.LENGTH_LONG).show()

            } catch (e: ParseException) {
                // Manejar diferentes tipos de errores de Parse
                val errorMsg = when (e.code) {
                    ParseException.CONNECTION_FAILED -> "Sin conexion a internet"
                    ParseException.INVALID_SESSION_TOKEN -> "Sesion invalida"
                    ParseException.TIMEOUT -> "Timeout - servidor muy lento"
                    else -> "Error Parse: ${e.code} - ${e.message}"
                }
                Toast.makeText(this@LoginActivity, errorMsg, Toast.LENGTH_LONG).show()

            } catch (e: Exception) {
                // Manejar errores generales
                Toast.makeText(this@LoginActivity, "Error general: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Configurar listeners de los botones
    private fun setupListeners() {
        binding.btnLogin.setOnClickListener { login() } // Login
        binding.btnSignUp.setOnClickListener { register() } // Registro
    }

    // Funcion para iniciar sesion
    private fun login() {
        val username = binding.etUsername.text.toString().trim()
        val password = binding.etPassword.text.toString()

        // Validar campos no vacios
        if (username.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Por favor completa todos los campos", Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnLogin.isEnabled = false // Deshabilitar boton durante login

        // Login asincrono con Parse
        ParseUser.logInInBackground(username, password) { user, e ->
            binding.btnLogin.isEnabled = true // Rehabilitar boton

            if (e == null && user != null) {
                Toast.makeText(this, "¡Bienvenido $username!", Toast.LENGTH_SHORT).show()
                goToMenu() // Ir al menu principal
            } else {
                // Manejar errores de login
                val errorMsg = when (e?.code) {
                    ParseException.OBJECT_NOT_FOUND -> "Usuario o contraseña incorrectos"
                    ParseException.CONNECTION_FAILED -> "Sin conexion a internet"
                    else -> "Error: ${e?.message}"
                }
                Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show()
            }
        }
    }

    // Funcion para registrar nuevo usuario
    private fun register() {
        val username = binding.etUsername.text.toString().trim()
        val password = binding.etPassword.text.toString()

        // Validar campos no vacios
        if (username.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Por favor completa todos los campos", Toast.LENGTH_SHORT).show()
            return
        }

        // Validar longitud minima de contraseña
        if (password.length < 6) {
            Toast.makeText(this, "La contraseña debe tener al menos 6 caracteres", Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnSignUp.isEnabled = false // Deshabilitar boton durante registro

        // Crear nuevo objeto ParseUser
        val user = ParseUser().apply {
            setUsername(username)
            setPassword(password)
        }

        // Registro asincrono
        user.signUpInBackground { e ->
            binding.btnSignUp.isEnabled = true // Rehabilitar boton

            if (e == null) {
                Toast.makeText(this, "¡Registro exitoso! Bienvenido $username", Toast.LENGTH_SHORT).show()
                goToMenu() // Ir al menu principal
            } else {
                // Manejar errores de registro
                val errorMsg = when (e.code) {
                    ParseException.USERNAME_TAKEN -> "El usuario ya existe"
                    ParseException.CONNECTION_FAILED -> "Sin conexion a internet"
                    else -> "Error: ${e.message}"
                }
                Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show()
            }
        }
    }

    // Función para navegar al menu principal
    private fun goToMenu() {
        MainActivity.start(this) // Iniciar MainActivity
        finish() // Finalizar LoginActivity
    }
}
