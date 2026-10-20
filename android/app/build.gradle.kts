plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.universalremote"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.universalremote"
        // BluetoothHidDevice exige API 28.
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    jvmToolchain(17)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    // Core / ciclo de vida
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.2")
    implementation("androidx.lifecycle:lifecycle-process:2.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.2")

    // UI existente (Compose); aquí sólo se declaran las dependencias de enlace con el ViewModel.
    implementation(platform("androidx.compose:compose-bom:2025.08.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    // PowerSettingsNew, FastRewind, FastForward, Pause (R8 elimina los iconos no usados en release).
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // Concurrencia
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // Inyección de dependencias
    implementation("com.google.dagger:hilt-android:2.57.1")
    ksp("com.google.dagger:hilt-compiler:2.57.1")

    // Red
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // MQTT para Hisense VIDAA (control remoto por broker en el TV, puerto 36669).
    implementation("org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5")

    // Persistencia
    implementation("androidx.room:room-runtime:2.7.2")
    implementation("androidx.room:room-ktx:2.7.2")
    ksp("androidx.room:room-compiler:2.7.2")
    implementation("androidx.datastore:datastore-preferences:1.1.7")

    // Tests JVM
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    // Certificados autofirmados para probar adaptadores HTTPS con TOFU.
    testImplementation("com.squareup.okhttp3:okhttp-tls:4.12.0")
    // org.json real en la JVM: el de android.jar es un stub que devuelve valores por defecto.
    testImplementation("org.json:json:20240303")
}
