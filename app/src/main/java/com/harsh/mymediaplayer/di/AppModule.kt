package com.harsh.mymediaplayer.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    fun providesGlobalScope(): CoroutineScope {
        return GlobalScope
    }
}