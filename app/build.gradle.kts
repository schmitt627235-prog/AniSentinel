plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "de.anisentinel.app"
    compileSdk = 35
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "de.anisentinel.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 54
        versionName = "0.25.8"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    buildTypes {
        debug {
            resValue("bool", "anime_radar_enabled", "false")
            resValue("bool", "aniworld_enabled", "true")
            resValue("bool", "local_provider_diagnostic_enabled", "false")
            buildConfigField("boolean", "UNOFFICIAL_JUSTWATCH_DIAGNOSTIC_ENABLED", "true")
        }
        release {
            resValue("bool", "anime_radar_enabled", "false")
            resValue("bool", "aniworld_enabled", "false")
            resValue("bool", "local_provider_diagnostic_enabled", "false")
            buildConfigField("boolean", "UNOFFICIAL_JUSTWATCH_DIAGNOSTIC_ENABLED", "false")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging.resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"

    sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.08.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.3")
    implementation("androidx.lifecycle:lifecycle-livedata-core:2.9.3")
    implementation(files("libs/lifecycle-livedata-2.9.0.aar"))
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.9.3")
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    implementation(files("libs/work-runtime-2.10.0.aar"))
    implementation(files("libs/work-runtime-ktx-2.10.0.aar"))
    implementation(files("libs/jsoup-1.19.1.jar"))

    implementation("androidx.room:room-runtime:2.7.2")
    implementation("androidx.room:room-ktx:2.7.2")
    ksp("androidx.room:room-compiler:2.7.2")

    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.room:room-testing:2.7.2")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("org.robolectric:robolectric:4.14.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.room:room-testing:2.7.2")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

// All production and generated sources are Kotlin.
tasks.withType<JavaCompile>().configureEach {
    enabled = false
}
