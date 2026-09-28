plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.gasuschedule"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.example.gasuschedule"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.all {
            // Живой тест эндпоинта запускается только явно: ./gradlew test -Plive
            it.systemProperty("live", project.hasProperty("live"))
        }
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.jsoup)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}
