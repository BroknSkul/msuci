package com.musicplayer.app.di

import com.musicplayer.app.data.repository.*
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    // If using interfaces for repositories, bind them here.
    // Since we used classes directly in this simple implementation, 
    // we don't strictly need Binds unless we refactor to interfaces.
}
