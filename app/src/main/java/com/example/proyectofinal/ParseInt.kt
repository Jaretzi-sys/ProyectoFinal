package com.example.proyectofinal

import android.app.Application
import com.parse.Parse
import com.parse.livequery.ParseLiveQueryClient
import java.net.URI

class ParseInt: Application() {

    companion object {
        lateinit var liveQueryClient: ParseLiveQueryClient
    }

    override fun onCreate() {
        super.onCreate()

        Parse.initialize(
            Parse.Configuration.Builder(this)
                .applicationId("idtOkH3tIlT7Ms8N5GCUNGnbOji4Xr9lgYlzWm2v")
                .clientKey("Gtt3pTbCkXtquhmupK9n8EfzTgTY7Vhan4W2tdnk")
                .server("https://parseapi.back4app.com/")
                .build()
        )

        liveQueryClient = ParseLiveQueryClient.Factory.getClient(
            URI.create("wss://proyectofinal.b4a.io/")
        )

    }
}
