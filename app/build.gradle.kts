plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

val robolectricAndroidAll: Configuration = configurations.create("robolectricAndroidAll")
// Вне каталога проекта: нативная библиотека Robolectric не грузится из пути с пробелами.
val robolectricJarsDir: File = gradle.gradleUserHomeDir.resolve("robolectric-jars")
val robolectricJars = tasks.register<Sync>("robolectricJars") {
    from(robolectricAndroidAll)
    into(robolectricJarsDir)
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

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            // Живой тест эндпоинта запускается только явно: ./gradlew test -Plive
            it.systemProperty("live", project.hasProperty("live"))
            // Образ Android для Robolectric качает Gradle (встроенный загрузчик Robolectric
            // падает на SSL), сам Robolectric работает offline.
            it.dependsOn(robolectricJars)
            it.systemProperty("robolectric.offline", "true")
            it.systemProperty("robolectric.dependency.dir", robolectricJarsDir.absolutePath)
            // Robolectric лезет во внутренности JDK (FileDescriptor и т.п.) — на JDK 17+ нужен явный доступ.
            it.jvmArgs(
                "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
                "--add-opens=java.base/java.io=ALL-UNNAMED",
                "--add-opens=java.base/java.lang=ALL-UNNAMED",
            )
        }
    }
}


room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.jsoup)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.datastore.preferences)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.splashscreen)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.navigation.compose)
    implementation(libs.hilt.lifecycle.viewmodel.compose)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.room.testing)
    // Должен соответствовать @Config(sdk = [36]) в тестах.
    robolectricAndroidAll(libs.robolectric.android.all)
}
