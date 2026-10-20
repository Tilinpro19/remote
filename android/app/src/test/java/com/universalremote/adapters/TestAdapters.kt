package com.universalremote.adapters

import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

/** Utilidades compartidas por las pruebas de adaptadores. */
object TestAdapters {
    /** Timeouts cortos para que un fallo no cuelgue la suite. */
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(500, TimeUnit.MILLISECONDS)
        .readTimeout(1, TimeUnit.SECONDS)
        .callTimeout(2, TimeUnit.SECONDS)
        .build()
}
