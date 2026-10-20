package com.universalremote.core.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.universalremote.adapters.DefaultRemoteAdapterFactory
import com.universalremote.data.local.AppDatabase
import com.universalremote.data.local.DeviceDao
import com.universalremote.data.local.FavoriteDao
import com.universalremote.data.local.IrCodeDao
import com.universalremote.data.local.MacroDao
import com.universalremote.data.preferences.DataStoreSessionPreferences
import com.universalremote.data.repository.DeviceRepositoryImpl
import com.universalremote.data.repository.FavoriteRepositoryImpl
import com.universalremote.data.repository.IrCodeRepositoryImpl
import com.universalremote.data.repository.MacroRepositoryImpl
import com.universalremote.domain.adapter.RemoteAdapterFactory
import com.universalremote.domain.repository.DeviceRepository
import com.universalremote.domain.repository.FavoriteRepository
import com.universalremote.domain.repository.IrCodeRepository
import com.universalremote.domain.repository.MacroRepository
import com.universalremote.domain.repository.SessionPreferences
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Timeouts cortos: un TV en la LAN responde en < 200 ms; esperar 10 s (valor por defecto)
     * congelaría la cola de comandos. El pool mantiene keep-alive entre pulsaciones.
     */
    @Provides
    @Singleton
    @LanHttpClient
    fun provideLanHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .writeTimeout(4, TimeUnit.SECONDS)
        .callTimeout(6, TimeUnit.SECONDS)
        .connectionPool(ConnectionPool(4, 5, TimeUnit.MINUTES))
        .retryOnConnectionFailure(true)
        .build()

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
            .build()

    @Provides
    fun provideDeviceDao(db: AppDatabase): DeviceDao = db.deviceDao()

    @Provides
    fun provideIrCodeDao(db: AppDatabase): IrCodeDao = db.irCodeDao()

    @Provides
    fun provideMacroDao(db: AppDatabase): MacroDao = db.macroDao()

    @Provides
    fun provideFavoriteDao(db: AppDatabase): FavoriteDao = db.favoriteDao()

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("session") }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {
    @Binds
    abstract fun bindDeviceRepository(impl: DeviceRepositoryImpl): DeviceRepository

    @Binds
    abstract fun bindIrCodeRepository(impl: IrCodeRepositoryImpl): IrCodeRepository

    @Binds
    abstract fun bindFavoriteRepository(impl: FavoriteRepositoryImpl): FavoriteRepository

    @Binds
    abstract fun bindMacroRepository(impl: MacroRepositoryImpl): MacroRepository

    @Binds
    abstract fun bindSessionPreferences(impl: DataStoreSessionPreferences): SessionPreferences

    @Binds
    abstract fun bindAdapterFactory(impl: DefaultRemoteAdapterFactory): RemoteAdapterFactory
}
