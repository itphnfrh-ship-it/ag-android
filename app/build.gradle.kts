import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.cruisepass.verify"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.cruisepass.verify"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"

        buildConfigField("String", "API_BASE_URL", "\"http://ag-srv-1c-01.astoriagrande.local/uopo_portal/hs/portal/api/v1\"")
        buildConfigField("String", "API_TOKEN", "\"6457559c7390072876163e9337c08c16342113d4\"")

        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.zxing.core)
    implementation(libs.mlkit.objectdetection)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

val sdkDir = Properties().apply {
    rootProject.file("local.properties").inputStream().use { load(it) }
}.getProperty("sdk.dir")

val signDebugCompatible = tasks.register<Exec>("signDebugCompatible") {
    dependsOn("packageDebug")
    val apk = layout.buildDirectory.file("outputs/apk/debug/app-debug.apk").get().asFile
    val keystore = file("${System.getProperty("user.home")}/.android/debug.keystore")
    commandLine(
        "cmd",
        "/c",
        file(sdkDir).resolve("build-tools/34.0.0/apksigner.bat").absolutePath,
        "sign",
        "--ks",
        keystore.absolutePath,
        "--ks-pass",
        "pass:android",
        "--key-pass",
        "pass:android",
        "--ks-key-alias",
        "androiddebugkey",
        "--v1-signing-enabled",
        "true",
        "--v2-signing-enabled",
        "true",
        "--v3-signing-enabled",
        "false",
        "--min-sdk-version",
        "21",
        apk.absolutePath,
    )
}

tasks.configureEach {
    if (name == "assembleDebug") {
        dependsOn(signDebugCompatible)
    }
}
