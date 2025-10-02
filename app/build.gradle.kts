import com.google.protobuf.gradle.id

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
    alias(libs.plugins.protobuf)
    alias(libs.plugins.kotlin.plugin.serialization)
}

android {
    namespace = "com.github.damontecres.dolphin"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.github.damontecres.dolphin"
        minSdk = 25
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        debug {
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }
    room {
        schemaDirectory("$projectDir/schemas")
    }
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:${libs.protobuf.kotlin.lite.get().version}"
    }
    generateProtoTasks {
        all().forEach {
            it.plugins {
                id("java") {
                    option("lite")
                }
            }
            it.builtins {
                id("kotlin") {
                    option("lite")
                }
            }
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.runtime.livedata)
    implementation(libs.androidx.tv.foundation)
    implementation(libs.androidx.tv.material)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.datastore)
    implementation(libs.protobuf.kotlin.lite)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.datasource.okhttp)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.ui.compose)

    implementation(libs.coil.core)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.cachecontrol)
    implementation(libs.coil.network.okhttp)
    implementation(libs.coil.gif)
    implementation(libs.coil.svg)

    implementation(libs.jellyfin.core)
    implementation(libs.jellyfin.api)
    implementation(libs.jellyfin.api.okhttp)

    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.material3.adaptive.navigation3)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.hilt.android)
    implementation(libs.androidx.room.common.jvm)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.hilt.navigation.compose)
    ksp(libs.androidx.room.compiler)
    ksp(libs.hilt.android.compiler)

    implementation(libs.timber)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
