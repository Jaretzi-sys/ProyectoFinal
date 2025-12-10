package com.example.proyectofinal
import android.app.Application
import com.parse.Parse
import com.parse.livequery.ParseLiveQueryClient
import java.net.URI

//Clase de aplicacion para inicializar Parse y LiveQuery.
class ParseInt : Application() {

    companion object {
        // Cliente de LiveQuery accesible globalmente para suscripciones en tiempo real
        lateinit var liveQueryClient: ParseLiveQueryClient
    }

    override fun onCreate() {
        super.onCreate()

        // Inicializa el SDK de Parse con las credenciales de Back4App
        Parse.initialize(
            Parse.Configuration.Builder(this)
                // ID unico de la aplicacion en Back4App
                .applicationId("qPzvrr818BMeZRdSJrEFPufJidu1akLvkWWwnApU")
                // Clave del cliente para autenticar las peticiones
                .clientKey("VFcL9RItCJstslObLYncoeuzpCuievKaSFdCOpj8")
                // URL del servidor de Parse en Back4App
                .server("https://parseapi.back4app.com/")
                .build()
        )

        /*Inicializa el cliente de LiveQuery para recibir actualizaciones en tiempo real
         wss:// indica WebSocket seguro para comunicacion bidireccional*/
        liveQueryClient = ParseLiveQueryClient.Factory.getClient(
            URI.create("wss://juego.b4a.io/")
        )
    }
}
