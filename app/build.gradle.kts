import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// ---------------------------------------------------------------------------
// Versioning
//
// versionName is edited by hand for each meaningful release (see CHANGELOG.md).
//
// versionCode MUST increase monotonically forever, or Android refuses to install
// a build as an update. CI passes -PversionCode=<VERSION_CODE_BASE + run number>,
// which is monotonic by construction. Local builds fall back to LOCAL_VERSION_CODE,
// which is intentionally low so a hand-built local APK never "wins" over a CI build
// and then blocks the next CI build from installing.
//
// See README.md "Versioning strategy".
// ---------------------------------------------------------------------------
val versionNameValue = "2.0.0"
val localVersionCode = 1
val resolvedVersionCode =
    (providers.gradleProperty("versionCode").orNull)?.toIntOrNull() ?: localVersionCode

// ---------------------------------------------------------------------------
// Release signing
//
// The release signing identity is PERMANENT. It is never generated per-build.
// It is supplied either by:
//   * CI: ANDROID_KEYSTORE_PATH / ANDROID_KEYSTORE_PASSWORD / ANDROID_KEY_ALIAS /
//     ANDROID_KEY_PASSWORD environment variables, reconstructed from GitHub secrets.
//   * Locally: a keystore.properties file in the repo root (git-ignored).
//
// If no signing material is available the release APK is built UNSIGNED rather than
// silently falling back to the debug key — a debug-signed "release" would be a
// different signing identity and could not update the installed app.
// ---------------------------------------------------------------------------
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

fun signingValue(envName: String, propName: String): String? =
    providers.environmentVariable(envName).orNull?.takeIf { it.isNotBlank() }
        ?: keystoreProperties.getProperty(propName)?.takeIf { it.isNotBlank() }

val releaseStoreFile = signingValue("ANDROID_KEYSTORE_PATH", "storeFile")
val releaseStorePassword = signingValue("ANDROID_KEYSTORE_PASSWORD", "storePassword")
val releaseKeyAlias = signingValue("ANDROID_KEY_ALIAS", "keyAlias")
val releaseKeyPassword = signingValue("ANDROID_KEY_PASSWORD", "keyPassword")
val hasReleaseSigning = listOf(
    releaseStoreFile, releaseStorePassword, releaseKeyAlias, releaseKeyPassword,
).all { it != null } && file(releaseStoreFile!!).exists()

android {
    namespace = "com.personal.sportspk"
    compileSdk = 35

    defaultConfig {
        // PERMANENT. Changing this makes Android treat new builds as a different
        // application, breaking upgrade-in-place.
        applicationId = "com.personal.sportspk"
        minSdk = 26
        targetSdk = 35
        versionCode = resolvedVersionCode
        versionName = versionNameValue
    }

    if (hasReleaseSigning) {
        signingConfigs {
            create("release") {
                storeFile = file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        debug {
            // Suffixed so a debug build can coexist with the real app instead of
            // colliding with it under an incompatible signing identity.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (hasReleaseSigning) signingConfigs.getByName("release") else null
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    lint {
        abortOnError = true
        checkReleaseBuilds = true
        warningsAsErrors = false
    }
}

androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        if (!hasReleaseSigning) {
            logger.warn(
                "sportspk: no release signing material found. The release APK will be UNSIGNED " +
                    "and CANNOT be installed as an update over the existing app."
            )
        }
        variant.outputs.forEach { output ->
            (output as? com.android.build.api.variant.impl.VariantOutputImpl)?.outputFileName?.set(
                "sportspk-v$versionNameValue-$resolvedVersionCode.apk"
            )
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.json)
}
