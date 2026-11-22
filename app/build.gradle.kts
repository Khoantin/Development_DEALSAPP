plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
    id("com.google.android.libraries.mapsplatform.secrets-gradle-plugin")
}

android {
    namespace = "com.example.androidapp"

    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.androidapp"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner =
            "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }

    compileOptions {
        sourceCompatibility =
            JavaVersion.VERSION_11

        targetCompatibility =
            JavaVersion.VERSION_11

        isCoreLibraryDesugaringEnabled =
            true
    }

    buildFeatures {
        compose = true
    }
}
secrets {
    propertiesFileName = "secrets.properties"
    defaultPropertiesFileName = "local.defaults.properties"
}

dependencies {

    coreLibraryDesugaring(
        "com.android.tools:desugar_jdk_libs:2.1.5"
    )


    // ---------------------------------------------------------
    // FIREBASE
    // ---------------------------------------------------------

    implementation(
        platform(libs.firebase.bom)
    )

    implementation(
        libs.firebase.auth
    )

    implementation(
        libs.firebase.firestore
    )

    implementation(
        libs.firebase.storage
    )
    implementation(
        libs.firebase.functions
    )


    // ---------------------------------------------------------
    // LOCATION
    // ---------------------------------------------------------

    implementation(
        "com.google.android.gms:play-services-location:21.4.0"
    )


    // ---------------------------------------------------------
    // GOOGLE MAPS COMPOSE
    // ---------------------------------------------------------

    implementation(
        "com.google.maps.android:maps-compose:6.12.0"
    )


    // ---------------------------------------------------------
    // JETPACK COMPOSE
    // ---------------------------------------------------------

    implementation(
        platform(libs.androidx.compose.bom)
    )

    implementation(
        libs.androidx.activity.compose
    )

    implementation(
        libs.androidx.compose.material3
    )

    implementation(
        libs.material.icons.core
    )

    implementation(
        libs.material.icons.extended
    )

    implementation(
        libs.androidx.compose.ui
    )

    implementation(
        libs.androidx.compose.ui.graphics
    )

    implementation(
        libs.androidx.compose.ui.tooling.preview
    )

    implementation(
        libs.androidx.core.ktx
    )

    implementation(
        libs.androidx.lifecycle.runtime.ktx
    )


    // ---------------------------------------------------------
    // NAVIGATION
    // ---------------------------------------------------------

    implementation(
        libs.androidx.navigation.compose
    )

    implementation(
        libs.androidx.navigation.fragment
    )

    implementation(
        libs.androidx.navigation.ui
    )

    implementation(
        libs.androidx.navigation.dynamic.features.fragment
    )


    // ---------------------------------------------------------
    // JSON
    // ---------------------------------------------------------

    implementation(
        libs.kotlinx.serialization.json
    )

    // ---------------------------------------------------------
    // COIL IMAGE LOADING
    // ---------------------------------------------------------
    implementation("io.coil-kt:coil-compose:2.7.0")


    // ---------------------------------------------------------
    // TESTING
    // ---------------------------------------------------------

    testImplementation(
        libs.junit
    )

    androidTestImplementation(
        platform(libs.androidx.compose.bom)
    )

    androidTestImplementation(
        libs.androidx.compose.ui.test.junit4
    )

    androidTestImplementation(
        libs.androidx.espresso.core
    )

    androidTestImplementation(
        libs.androidx.junit
    )

    androidTestImplementation(
        libs.androidx.navigation.testing
    )

    debugImplementation(
        libs.androidx.compose.ui.test.manifest
    )

    debugImplementation(
        libs.androidx.compose.ui.tooling
    )
}