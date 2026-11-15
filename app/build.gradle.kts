plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val vName = providers.gradleProperty("VERSION_NAME").get()
val vCode = vName.split(".").map { it.toInt() }.let { it[0] * 100 + it[1] * 10 + it[2] }

android {
    namespace = "dev.beadbelt.game"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.beadbelt.game"
        minSdk = 24
        targetSdk = 35
        versionCode = vCode
        versionName = vName
    }

    // Общий публичный ключ разработки (см. signing/README.md): debug и release подписаны одинаково.
    signingConfigs {
        create("dev") {
            storeFile = rootProject.file("signing/dev.keystore")
            storePassword = "beadbelt"
            keyAlias = "beadbelt"
            keyPassword = "beadbelt"
        }
    }
    buildTypes {
        debug { signingConfig = signingConfigs.getByName("dev") }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("dev")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
