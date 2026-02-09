import com.google.protobuf.gradle.id
import com.mikepenz.aboutlibraries.plugin.DuplicateMode
import com.mikepenz.aboutlibraries.plugin.DuplicateRule
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
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
    alias(libs.plugins.openapi.generator)
}

val isCI = if (System.getenv("CI") != null) System.getenv("CI").toBoolean() else false
val shouldSign = isCI && System.getenv("KEY_ALIAS") != null
val ffmpegModuleExists = project.file("libs/lib-decoder-ffmpeg-release.aar").exists()
val av1ModuleExists = project.file("libs/lib-decoder-av1-release.aar").exists()

val gitTags =
    providers
        .exec { commandLine("git", "tag", "--list", "v*", "p*") }
        .standardOutput.asText
        .get()

val gitDescribe =
    providers
        .exec { commandLine("git", "describe", "--tags", "--long", "--match=v*") }
        .standardOutput.asText
        .getOrElse("v0.0.0")

android {
    namespace = "com.github.damontecres.wholphin"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.github.damontecres.wholphin"
        minSdk = 23
        targetSdk = 36
        versionCode = gitTags.trim().lines().size
        versionName = gitDescribe.trim().removePrefix("v").ifBlank { "0.0.0" }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            isDebuggable = false
        }
        debug {
            isMinifyEnabled = false
            isDebuggable = true
            applicationIdSuffix = ".debug"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true
    }
    kotlin {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
            javaParameters = true
        }
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
                    val abi = output.getFilter("ABI").let { if (it != null) "-$it" else "" }
                    val outputFileName =
                        "Wholphin-${variant.baseName}-${variant.versionName}-${variant.versionCode}$abi.apk"
                    output.outputFileName = outputFileName
                }
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a")
            isUniversalApk = true
        }
    }

    sourceSets {
        getByName("main") {
            kotlin.srcDirs("$buildDir/generated/seerr_api/src/main/kotlin")
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

openApiGenerate {
    generatorName.set("kotlin")
    inputSpec.set("$projectDir/src/main/seerr/seerr-api.yml")
    templateDir.set("$projectDir/src/main/seerr/templates")
    outputDir.set("$buildDir/generated/seerr_api")
    apiPackage.set("com.github.damontecres.wholphin.api.seerr")
    modelPackage.set("com.github.damontecres.wholphin.api.seerr.model")
    groupId.set("com.github.damontecres.wholphin.api.seerr")
    id.set("seerr-api")
    packageName.set("com.github.damontecres.wholphin.api.seerr")
    additionalProperties.apply {
        put("serializationLibrary", "kotlinx_serialization")
        put("sortModelPropertiesByRequiredFlag", true)
        put("sortParamsByRequiredFlag", true)
        put("useCoroutines", true)
        put("enumPropertyNaming", "UPPERCASE")
        put("modelMutable", false)

        // Note: this is only for downloading files, so it's not necessary to enable
        put("supportAndroidApiLevel25AndBelow", false)
    }
}

tasks.named("preBuild") {
    dependsOn.add(tasks.named("openApiGenerate"))
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
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.datastore)
    implementation(libs.protobuf.kotlin.lite)
    implementation(libs.androidx.tvprovider)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.datasource.okhttp)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.exoplayer.dash)
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
    implementation(libs.androidx.palette.ktx)
    implementation(libs.androidx.media3.effect)
    ksp(libs.androidx.room.compiler)
    ksp(libs.hilt.android.compiler)
    ksp(libs.androidx.hilt.compiler)

    implementation(libs.timber)
    implementation(libs.slf4j2.timber)
    implementation(libs.aboutlibraries.core)
    implementation(libs.aboutlibraries.compose.m3)
    implementation(libs.multiplatform.markdown.renderer)
    implementation(libs.multiplatform.markdown.renderer.m3)
    implementation(libs.programguide)
    implementation(libs.acra.http)
    implementation(libs.acra.dialog)
    implementation(libs.acra.limiter)
    compileOnly(libs.auto.service.annotations)
    ksp(libs.auto.service.ksp)
    implementation(platform(libs.okhttp.bom))
    implementation(libs.okhttp)
    implementation(libs.kache)
    implementation(libs.kache.file)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    if (ffmpegModuleExists || isCI) {
        implementation(files("libs/lib-decoder-ffmpeg-release.aar"))
    }
    if (av1ModuleExists || isCI) {
        implementation(files("libs/lib-decoder-av1-release.aar"))
    }

    testImplementation(libs.mockk.android)
    testImplementation(libs.mockk.agent)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.core.testing)
    testImplementation(libs.robolectric)
}
