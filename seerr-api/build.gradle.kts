plugins {
    id("java-library")
    alias(libs.plugins.kotlin.jvm)
    id("org.openapi.generator") version "7.17.0"
}
java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}
kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11
    }

    sourceSets {
        main {
            kotlin.srcDirs("$buildDir/generated/seerr_api/src/main/kotlin")
        }
    }
}

openApiGenerate {
    generatorName.set("kotlin")
    inputSpec.set("$projectDir/src/main/seerr-api.yml")
    outputDir.set("$buildDir/generated/seerr_api")
    apiPackage.set("com.github.damontecres.api.seerr")
    modelPackage.set("com.github.damontecres.api.seerr.model")
    groupId.set("com.github.damontecres.api.seerr")
    id.set("seerr-api")
    packageName.set("com.github.damontecres.api.seerr")
    additionalProperties.apply {
        put("serializationLibrary", "kotlinx_serialization")
        put("sortModelPropertiesByRequiredFlag", true)
        put("sortParamsByRequiredFlag", true)
        put("useCoroutines", true)
        put("enumPropertyNaming", "UPPERCASE")
        put("modelMutable", false)
    }
}

tasks.named("compileKotlin") {
    dependsOn.add(tasks.named("openApiGenerate"))
}

dependencies {
    implementation(platform(libs.okhttp.bom))
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
}
