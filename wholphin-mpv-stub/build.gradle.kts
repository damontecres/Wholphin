plugins {
    alias(libs.plugins.android.library)
}

private fun Provider<String>.getInt() = get().toInt()

android {
    namespace = "com.github.damontecres.wholphin.mpv"
    compileSdk {
        version = release(libs.versions.compileSdk.getInt())
    }

    defaultConfig {
        minSdk = libs.versions.minSdk.getInt()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.media3.exoplayer)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
