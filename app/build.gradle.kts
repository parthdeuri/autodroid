plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.autodroid"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.autodroid"
        minSdk = 26
        targetSdk = 34
        versionCode = 4
        versionName = "2.2"
    }

    // One fixed key for every build, so a new APK always updates the installed app.
    signingConfigs {
        create("autodroid") {
            storeFile = file("autodroid.p12")
            storeType = "pkcs12"
            storePassword = "autodroid"
            keyAlias = "autodroid"
            keyPassword = "autodroid"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("autodroid")
        }
        release {
            signingConfig = signingConfigs.getByName("autodroid")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.core:core:1.13.1")
}
