plugins {
    id("com.android.application")
}

android {
    namespace = "com.fightingjoey.oneurl"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.fightingjoey.oneurl"
        minSdk = 21
        targetSdk = 34
        versionCode = 2
        versionName = "1.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
