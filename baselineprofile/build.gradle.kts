import com.android.build.api.dsl.ManagedVirtualDevice
import com.android.build.api.dsl.TestExtension

plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.androidx.baselineprofile)
}

private fun Provider<String>.getInt() = get().toInt()

configure<TestExtension> {
    namespace = "com.github.damontecres.wholphin.baselineprofile"
    compileSdk = libs.versions.compileSdk.getInt()

    defaultConfig {
        minSdk = 28
        targetSdk = libs.versions.targetSdk.getInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Only the default flavor is profiled, the profile is shared by all flavors
        missingDimensionStrategy("version", "default")
    }

    targetProjectPath = ":app"

    // Managed device so the profile can be generated on CI & locally without a physical device
    testOptions.managedDevices.allDevices {
        create<ManagedVirtualDevice>("pixel6Api34") {
            device = "Pixel 6"
            apiLevel = 34
            systemImageSource = "aosp"
        }
    }
}

baselineProfile {
    managedDevices += "pixel6Api34"
    // Use a connected device (eg `-Pandroid.testInstrumentationRunnerArguments...`) when false
    useConnectedDevices = false
}

dependencies {
    implementation(libs.androidx.junit)
    implementation(libs.androidx.espresso.core)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}

androidComponents {
    onVariants { v ->
        val artifactsLoader = v.artifacts.getBuiltArtifactsLoader()
        v.instrumentationRunnerArguments.put(
            "targetAppId",
            v.testedApks.map { artifactsLoader.load(it)?.applicationId },
        )
    }
}
