package com.universalremote.core.di

import javax.inject.Qualifier

/** `CoroutineScope` de vida de proceso (SupervisorJob + Dispatchers.Default). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

/** OkHttpClient con timeouts cortos pensados para equipos en la LAN. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class LanHttpClient
