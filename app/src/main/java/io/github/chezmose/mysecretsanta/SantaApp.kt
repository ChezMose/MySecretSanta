package io.github.chezmose.mysecretsanta

import android.app.Application
import io.github.chezmose.mysecretsanta.data.AppDatabase

class SantaApp : Application() {
    val database: AppDatabase by lazy { AppDatabase.build(this) }
}
