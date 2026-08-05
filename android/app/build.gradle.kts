import java.net.URI

plugins {
    id("com.android.application")
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
    implementation("androidx.constraintlayout:constraintlayout:2.2.2")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("com.google.android.material:material:1.13.0")

    testImplementation("junit:junit:4.13.2")

    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
