package com.soundguard.app.data.db

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SoundGuardDatabase =
        Room.databaseBuilder(context, SoundGuardDatabase::class.java, "soundguard.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideAlertEventDao(db: SoundGuardDatabase): AlertEventDao = db.alertEventDao()
}
