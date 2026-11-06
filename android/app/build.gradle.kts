import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Signing: keystore.properties (lokal, git'ga chiqmaydi) yoki muhit o'zgaruvchilari (CI).
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun signingValue(key: String, env: String): String? =
    keystoreProps.getProperty(key) ?: System.getenv(env)

android {
    namespace = "uz.imtihonai.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "uz.imtihonai.app"
        minSdk = 26
        targetSdk = 36
        // Keyingi update: -PversionCode=2 yoki shu raqamni oshiring.
        versionCode = (project.findProperty("versionCode") as String?)?.toIntOrNull() ?: 1
        versionName = (project.findProperty("versionName") as String?) ?: "1.0.0"
    }

    val storeFilePath = signingValue("storeFile", "IMTIHON_KEYSTORE_FILE")
    signingConfigs {
        if (storeFilePath != null && file(storeFilePath).exists()) {
            create("release") {
                storeFile = file(storeFilePath)
                storePassword = signingValue("storePassword", "IMTIHON_KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "IMTIHON_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "IMTIHON_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    bundle {
        language { enableSplit = false }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    testImplementation("junit:junit:4.13.2")
}

// assembleRelease imzosiz APK chiqarib qo'ymasligi uchun: keystore sozlanmagan bo'lsa build to'xtaydi.
// (keystore.properties yoki IMTIHON_KEYSTORE_* muhit o'zgaruvchilari kerak, README/RELEASE_GUIDE ga qarang.)
gradle.taskGraph.whenReady {
    val wantsSignedApk = allTasks.any { it.project == project && (it.name == "assembleRelease" || it.name == "packageRelease") }
    if (wantsSignedApk && android.signingConfigs.findByName("release") == null) {
        throw GradleException(
            "Release imzosi sozlanmagan: keystore.properties (storeFile, storePassword, keyAlias, keyPassword) " +
                "yoki IMTIHON_KEYSTORE_FILE/PASSWORD/KEY_ALIAS/KEY_PASSWORD o'zgaruvchilarini bering."
        )
    }
}
