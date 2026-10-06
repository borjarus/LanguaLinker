package com.mila.langualinker

import android.app.Application
import com.mila.langualinker.di.androidModule
import com.mila.langualinker.di.sharedModule
import com.mila.langualinker.domain.usecase.EnsureBundledDecksUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.android.get
import org.koin.core.context.startKoin

class LanguaLinkerApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()

        startKoin {
            modules(sharedModule, androidModule(applicationContext))
        }

        // The bundled decks are imported off the main thread so a first launch cannot block
        // the UI. The importer is idempotent, so a launch interrupted here simply resumes
        // next time rather than leaving a half-imported collection.
        applicationScope.launch {
            get<EnsureBundledDecksUseCase>().invoke()
        }
    }
}
