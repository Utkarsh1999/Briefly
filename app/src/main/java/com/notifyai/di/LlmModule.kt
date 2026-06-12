package com.notifyai.di

import com.notifyai.ai.engine.LocalLlmEngine
import com.notifyai.ai.engine.LlamaCppEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class LlmModule {

    @Binds
    @Singleton
    abstract fun bindLocalLlmEngine(
        impl: LlamaCppEngine
    ): LocalLlmEngine
}
