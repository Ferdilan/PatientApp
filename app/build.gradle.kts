import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.secrets.gradle.plugin)
}

// Kode untuk membaca file local.properties untuk keperluan MQTT
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use { localProperties.load(it) }
}

android {
    namespace = "com.example.patientapp"
    compileSdk {
        version = release(36)
    }

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.example.patientapp"
        minSdk = 27
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        manifestPlaceholders["MAPS_API_KEY"] = localProperties.getProperty("MAPS_API_KEY", "")

        buildConfigField(
            "String",
            "MQTT_HOST",
            "\"${localProperties.getProperty("MQTT_HOST")}\""
        )
        buildConfigField(
            "String",
            "MQTT_USERNAME",
            "\"${localProperties.getProperty("MQTT_USERNAME")}\""
        )
        buildConfigField(
            "String",
            "MQTT_PASSWORD",
            "\"${localProperties.getProperty("MQTT_PASSWORD")}\""
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    packaging {
        resources {
            excludes += "META-INF/INDEX.LIST"
            excludes += "META-INF/io.netty.versions.properties"
        }
    }
}

dependencies {

    implementation ("com.amazon.ion:ion-java:1.10.4")
//    implementation("org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5")
//    implementation("org.eclipse.paho:org.eclipse.paho.android.service:1.1.1")
    implementation("com.hivemq:hivemq-mqtt-client:1.3.3")
    implementation("androidx.localbroadcastmanager:localbroadcastmanager:1.1.0")

    // Google Maps
    implementation("com.google.android.gms:play-services-maps:18.2.0")
    implementation("com.google.android.gms:play-services-location:21.3.0")

    implementation("androidx.annotation:annotation:1.7.1")

    // Networking
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.9.0") // Untuk debug API


    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

}

