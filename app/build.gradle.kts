plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Signature de l'APK release : fournie par GitHub Actions via des variables d'environnement.
// Sans ces variables (compilation locale), l'APK release n'est pas signé.
val signingStoreFile: String? = System.getenv("SIGNING_STORE_FILE")
val signingPassword: String? = System.getenv("SIGNING_PASSWORD")

android {
    namespace = "io.github.chezmose.mysecretsanta"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.chezmose.mysecretsanta"
        minSdk = 26
        targetSdk = 35
        // Numéro de build GitHub Actions : chaque compilation est une mise à jour de la précédente.
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "0.1.${System.getenv("GITHUB_RUN_NUMBER") ?: "0"}"
    }

    signingConfigs {
        if (signingStoreFile != null && signingPassword != null) {
            create("release") {
                storeFile = file(signingStoreFile)
                storeType = "PKCS12"
                storePassword = signingPassword
                keyAlias = "mysecretsanta"
                keyPassword = signingPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

// Robolectric (tests de la base) a besoin d'une image d'Android. Elle est téléchargée par Gradle,
// qui la met en cache entre deux builds GitHub Actions, puis Robolectric tourne hors ligne.
// Ces noms dépendent de la version de Robolectric : en cas de mise à jour, reprendre ceux affichés
// dans l'erreur « Path is not a file: …/robolectric-jars/android-all-instrumented-….jar ».
val robolectricAndroidAll: Configuration by configurations.creating { isTransitive = false }
dependencies {
    robolectricAndroidAll("org.robolectric:android-all-instrumented:14-robolectric-10818077-i7")
    robolectricAndroidAll("org.robolectric:android-all-instrumented:15-robolectric-12650502-i7")
}
val robolectricJarsDir = layout.buildDirectory.dir("robolectric-jars")
val copyRobolectricJars by tasks.registering(Copy::class) {
    from(robolectricAndroidAll)
    into(robolectricJarsDir)
}

// Chaque test apparaît dans le journal de GitHub Actions, réussi ou non.
tasks.withType<Test>().configureEach {
    dependsOn(copyRobolectricJars)
    systemProperty("robolectric.offline", "true")
    systemProperty("robolectric.dependency.dir", robolectricJarsDir.get().asFile.absolutePath)
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

// Schémas de la base exportés à chaque compilation (récupérables dans les artefacts GitHub Actions).
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.zxing.core)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
