import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

/**
 * L'indirizzo del backend non sta nel repository: arriva da `local.properties`
 * (chiave `API_BASE_URL`) oppure dalla variabile d'ambiente
 * `MENCARE_API_BASE_URL`. È un dato di macchina, non di progetto.
 */
val localProperties = Properties().apply {
    val text = providers
        .fileContents(rootProject.layout.projectDirectory.file("local.properties"))
        .asText
        .orNull
    if (text != null) text.reader().use { load(it) }
}

val configuredBaseUrl: String =
    (localProperties.getProperty("API_BASE_URL") ?: System.getenv("MENCARE_API_BASE_URL")).orEmpty().trim()

/**
 * `10.0.2.2` è il loopback dell'host visto dall'emulatore: è il default di
 * debug perché il backend gira sul portatile di chi sviluppa, non nel telefono.
 */
val debugBaseUrl: String = configuredBaseUrl.ifEmpty { "http://10.0.2.2:3000/v1" }

/**
 * In release l'indirizzo è obbligatorio. Il controllo scatta solo quando si sta
 * davvero costruendo una release, altrimenti bloccherebbe anche `assembleDebug`
 * di chi non ha (e non deve avere) un `local.properties` con l'URL di
 * produzione.
 */
val buildingRelease = gradle.startParameter.taskNames.any { it.contains("release", ignoreCase = true) }
if (buildingRelease) {
    check(configuredBaseUrl.startsWith("https://")) {
        "API_BASE_URL mancante o non HTTPS: impostalo in local.properties (o in MENCARE_API_BASE_URL) prima di una build release."
    }
}

android {
    namespace = "com.devora.mencare.core.network"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }

    buildTypes {
        debug {
            buildConfigField("String", "API_BASE_URL", "\"$debugBaseUrl\"")
        }
        release {
            buildConfigField("String", "API_BASE_URL", "\"$configuredBaseUrl\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        buildConfig = true
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(project(":core:common"))

    api(libs.retrofit)
    api(libs.okhttp)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.kotlinx.serialization.json)
    // Nessun interceptor di logging: il traffico porta token e dati personali,
    // e un log dimenticato acceso è esattamente il modo in cui finiscono fuori.

    implementation(libs.androidx.security.crypto)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
