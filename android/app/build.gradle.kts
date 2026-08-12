import java.net.URI

plugins {
    id("com.android.application")
}

// google-services.json carries deployment-specific configuration and is never committed.
// Applying the plugin only when it is present keeps a fresh checkout buildable; the app
// then runs with the fail-closed token provider and cannot reach protected endpoints.
val googleServicesConfig = file("google-services.json")
val firebaseConfigured = googleServicesConfig.exists()
if (firebaseConfigured) {
    apply(plugin = "com.google.gms.google-services")
} else {
    logger.lifecycle("google-services.json not found: building without a configured identity provider.")
}

val debugApiBaseUrl = providers.gradleProperty("CONNEXA_API_BASE_URL")
    .orElse("http://10.0.2.2:8080/")
    .get()
val releaseApiBaseUrl = providers.gradleProperty("CONNEXA_RELEASE_API_BASE_URL")
    .orElse("https://api.connexa.invalid/")
    .get()

fun validateApiBaseUrl(name: String, value: String, httpsOnly: Boolean) {
    val uri = try {
        URI(value)
    } catch (exception: Exception) {
        throw GradleException("$name must be an absolute HTTP(S) URL.", exception)
    }
    val allowed = if (httpsOnly) uri.scheme == "https" else uri.scheme == "http" || uri.scheme == "https"
    check(uri.host != null && allowed) {
        if (httpsOnly) {
            "$name must be an absolute HTTPS URL."
        } else {
            "$name must be an absolute HTTP(S) URL."
        }
    }
}

fun buildConfigString(value: String): String {
    return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}

validateApiBaseUrl("CONNEXA_API_BASE_URL", debugApiBaseUrl, false)
validateApiBaseUrl("CONNEXA_RELEASE_API_BASE_URL", releaseApiBaseUrl, true)

android {
    namespace = "com.connexa.mobile"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.connexa.mobile"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    buildTypes {
        debug {
            buildConfigField("String", "API_BASE_URL", buildConfigString(debugApiBaseUrl))
        }
        release {
            isMinifyEnabled = true
            buildConfigField("String", "API_BASE_URL", buildConfigString(releaseApiBaseUrl))
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        animationsDisabled = true
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("com.google.android.material:material:1.14.0")

    // Draws the launch screen before any of our code runs, so the opening frame
    // costs nothing in startup time. Backports the platform behaviour below API 31.
    implementation("androidx.core:core-splashscreen:1.0.1")

    // Door check-in. The core library is plain Java and does the encoding, so the code that
    // turns a pass into a matrix is unit tested; the embedded scanner supplies the camera
    // preview and its permission handling.
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")

    // Cover images. Loading pictures into a recycling list is deceptively hard - the
    // cancellation, downsampling and cache behaviour are the whole problem, and getting
    // them wrong shows up as the wrong image on the wrong row while scrolling.
    implementation("com.github.bumptech.glide:glide:4.16.0")

    // Identity only. The app obtains short-lived tokens at runtime and never holds a
    // privileged credential; every authorization decision stays on the server.
    implementation(platform("com.google.firebase:firebase-bom:34.11.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-messaging")

    testImplementation("junit:junit:4.13.2")

    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
