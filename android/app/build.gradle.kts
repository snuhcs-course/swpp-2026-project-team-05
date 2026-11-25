import java.util.Properties
import org.gradle.api.tasks.testing.Test
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension
import org.gradle.testing.jacoco.tasks.JacocoReport

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    jacoco
}

jacoco {
    toolVersion = "0.8.13"
}

// Firebase Analytics (specs/features/telemetry.md) starts only when a local app/google-services.json exists. The file
// stays out of git, so CI and other builds run without telemetry.
if (file("google-services.json").isFile) {
    apply(plugin = "com.google.gms.google-services")
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val backendBaseUrl = providers.gradleProperty("framelessBackendUrl").orNull
    ?: localProperties.getProperty("framelessBackendUrl")
    ?: "https://frameless-team05-api.vercel.app"

// Release signing for store uploads. The keystore and its passwords stay outside the repository; pass the path of a
// keystore.properties file (storeFile, storePassword, keyAlias, keyPassword) as -PframelessSigning=<path> or put
// framelessSigning=<path> in local.properties. Without it, release builds stay unsigned.
val signingProperties = (providers.gradleProperty("framelessSigning").orNull
    ?: localProperties.getProperty("framelessSigning"))
    ?.let { file(it) }
    ?.takeIf { it.isFile }
    ?.let { source -> Properties().apply { source.inputStream().use { load(it) } } }

android {
    namespace = "com.swpp.team5.frameless"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.swpp.team5.frameless"
        minSdk = 26
        targetSdk = 37
        versionCode = 10
        versionName = "1.0.0"

        buildConfigField("String", "BACKEND_BASE_URL", "\"${backendBaseUrl.replace("\\", "\\\\").replace("\"", "\\\"")}\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        javaCompileOptions {
            annotationProcessorOptions {
                argument("room.schemaLocation", "$projectDir/schemas")
                argument("room.incremental", "true")
            }
        }
    }

    signingConfigs {
        if (signingProperties != null) {
            create("release") {
                storeFile = file(signingProperties.getProperty("storeFile"))
                storePassword = signingProperties.getProperty("storePassword")
                keyAlias = signingProperties.getProperty("keyAlias")
                keyPassword = signingProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            if (signingProperties != null) {
                signingConfig = signingConfigs.getByName("release")
            }
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
    }
}

val robolectricRuntime = configurations.create("robolectricRuntime") {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation("androidx.compose.material:material-icons-core")
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation("androidx.security:security-crypto:1.1.0")
    implementation("androidx.room:room-runtime:2.8.5")
    implementation(platform("com.google.firebase:firebase-bom:34.10.0"))
    implementation("com.google.firebase:firebase-analytics")
    annotationProcessor("androidx.room:room-compiler:2.8.5")
    testImplementation("androidx.room:room-testing:2.8.5")
    testImplementation("androidx.test:core:1.7.0")
    testImplementation(libs.junit)
    // AI-assisted tests parse real JSON on the host JVM instead of Android's placeholder org.json classes.
    testImplementation("org.json:json:20240303")
    testImplementation("org.robolectric:robolectric:4.17")
    add(robolectricRuntime.name, "org.robolectric:android-all-instrumented:15-robolectric-13954326-i7")
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

val robolectricRuntimeDirectory = layout.buildDirectory.dir("robolectric-runtime")
val prepareRobolectricRuntime by tasks.registering(Copy::class) {
    from(robolectricRuntime)
    into(robolectricRuntimeDirectory)
}

tasks.withType<Test>().configureEach {
    // Resolve the Android runtime through Gradle before the tests start. This
    // keeps Robolectric's separate HTTP fetcher out of the CI critical path.
    dependsOn(prepareRobolectricRuntime)
    systemProperty(
        "robolectric.dependency.dir",
        robolectricRuntimeDirectory.get().asFile.absolutePath,
    )

    // Robolectric loads Android production classes through a sandbox classloader.
    extensions.configure<JacocoTaskExtension> {
        isIncludeNoLocationClasses = true
        excludes = listOf("jdk.internal.*")
    }
}

// AI-assisted coverage task: report JVM unit-test coverage for Android production classes.
tasks.register<JacocoReport>("jacocoDebugUnitTestReport") {
    dependsOn("testDebugUnitTest")
    classDirectories.setFrom(fileTree(layout.buildDirectory.dir(
        "intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes"
    )) {
        exclude("**/BuildConfig.class", "**/R.class", "**/R$*.class")
    })
    sourceDirectories.setFrom(files("src/main/java"))
    executionData.setFrom(layout.buildDirectory.file("jacoco/testDebugUnitTest.exec"))
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}
