import com.google.protobuf.gradle.id
import com.mikepenz.aboutlibraries.plugin.DuplicateMode
import com.mikepenz.aboutlibraries.plugin.DuplicateRule
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
    alias(libs.plugins.protobuf)
    alias(libs.plugins.kotlin.plugin.serialization)
    alias(libs.plugins.aboutLibraries)
}

val isCI = if (System.getenv("CI") != null) System.getenv("CI").toBoolean() else false
val shouldSign = isCI && System.getenv("KEY_ALIAS") != null

fun getVersionCode(): Int {
    val stdout = ByteArrayOutputStream()
    exec {
        commandLine = listOf("git", "tag", "--list", "v*")
        standardOutput = stdout
    }
    return stdout
        .toString()
        .trim()
        .lines()
        .size
}

fun getAppVersion(): String {
    val stdout = ByteArrayOutputStream()
    exec {
        commandLine = listOf("git", "describe", "--tags", "--long", "--match=v*")
        standardOutput = stdout
    }
    return stdout
        .toString()
        .trim()
        .removePrefix("v")
        .ifBlank { "0.0.0" }
}

android {
    namespace = "com.github.damontecres.wholphin"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.github.damontecres.wholphin"
        minSdk = 25
        targetSdk = 36
        versionCode = getVersionCode()
        versionName = getAppVersion()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        isCoreLibraryDesugaringEnabled = true
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
    signingConfigs {
        if (shouldSign) {
            create("ci") {
                file("ci.keystore").writeBytes(
                    Base64.getDecoder().decode(System.getenv("SIGNING_KEY")),
                )
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
                storePassword = System.getenv("KEY_STORE_PASSWORD")
                storeFile = file("ci.keystore")
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = true
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (shouldSign) {
                signingConfig = signingConfigs.getByName("ci")
            } else {
                val localPropertiesFile = project.rootProject.file("local.properties")
                if (localPropertiesFile.exists()) {
                    val properties = Properties()
                    properties.load(localPropertiesFile.inputStream())
                    val signingConfigName = properties["release.signing.config"]?.toString()
                    if (signingConfigName != null) {
                        signingConfig = signingConfigs.getByName(signingConfigName)
                    }
                }
            }
        }
        debug {
            if (shouldSign) {
                signingConfig = signingConfigs.getByName("ci")
            }
        }

        applicationVariants.all {
            val variant = this
            variant.outputs
                .map { it as com.android.build.gradle.internal.api.BaseVariantOutputImpl }
                .forEach { output ->
                    val outputFileName =
                        "Wholphin-${variant.baseName}-${variant.versionName}-${variant.versionCode}.apk"
                    output.outputFileName = outputFileName
                }
        }
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
aboutLibraries {
    collect {
        configPath = file("config")
    }
    library {
        duplicationMode = DuplicateMode.MERGE
        duplicationRule = DuplicateRule.SIMPLE
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
    implementation(libs.androidx.preference.ktx)
    implementation(libs.androidx.room.testing)
    ksp(libs.androidx.room.compiler)
    ksp(libs.hilt.android.compiler)

    implementation(libs.timber)
    implementation(libs.aboutlibraries.core)
    implementation(libs.aboutlibraries.compose.m3)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
}
