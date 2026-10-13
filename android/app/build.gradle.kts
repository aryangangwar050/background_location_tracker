import java.util.Properties

plugins {
    id("com.android.application")
    id("kotlin-android")
    // The Flutter Gradle Plugin must be applied after the Android and Kotlin Gradle plugins.
    id("dev.flutter.flutter-gradle-plugin")
}

val releaseProperties = Properties()
val releasePropertiesFile = rootProject.file("key.properties")
if (releasePropertiesFile.exists()) {
    releasePropertiesFile.inputStream().use { releaseProperties.load(it) }
}

android {
    namespace = "com.example.background_location_tracker"
    compileSdk = flutter.compileSdkVersion
    ndkVersion = flutter.ndkVersion

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    defaultConfig {
        applicationId = "com.example.background_location_tracker"
        minSdk = 24
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        versionCode = flutter.versionCode
        versionName = flutter.versionName
    }

    signingConfigs {
        if (releasePropertiesFile.exists()) {
            create("release") {
                keyAlias = releaseProperties.getProperty("keyAlias")
                keyPassword = releaseProperties.getProperty("keyPassword")
                storeFile = file(requireNotNull(releaseProperties.getProperty("storeFile")))
                storePassword = releaseProperties.getProperty("storePassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            // Local testing only. Configure a private release key before distribution.
            signingConfig = signingConfigs.getByName(if (releasePropertiesFile.exists()) "release" else "debug")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

flutter {
    source = "../.."
}

dependencies {
    implementation("com.google.android.gms:play-services-location:21.3.0")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
