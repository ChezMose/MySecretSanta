package io.github.chezmose.mysecretsanta

import android.app.Application
import io.github.chezmose.mysecretsanta.data.AppDatabase
import io.github.chezmose.mysecretsanta.util.AppSettings

class SantaApp : Application() {
    val database: AppDatabase by lazy { AppDatabase.build(this) }
    val settings: AppSettings by lazy { AppSettings(this) }
}
