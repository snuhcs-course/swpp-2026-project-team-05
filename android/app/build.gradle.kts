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

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val backendBaseUrl = providers.gradleProperty("framelessBackendUrl").orNull
    ?: localProperties.getProperty("framelessBackendUrl")
    ?: "https://frameless-team05-api.vercel.app"

android {
    namespace = "com.swpp.team5.frameless"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.swpp.team5.frameless"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "BACKEND_BASE_URL", "\"${backendBaseUrl.replace("\\", "\\\\").replace("\"", "\\\"")}\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        javaCompileOptions {
            annotationProcessorOptions {
                argument("room.schemaLocation", "$projectDir/schemas")
                argument("room.incremental", "true")
            }
        }
    }

    buildTypes {
        release {
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
    }
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
    annotationProcessor("androidx.room:room-compiler:2.8.5")
    testImplementation("androidx.room:room-testing:2.8.5")
    testImplementation("androidx.test:core:1.7.0")
    testImplementation(libs.junit)
    // AI-assisted tests parse real JSON on the host JVM instead of Android's placeholder org.json classes.
    testImplementation("org.json:json:20240303")
    testImplementation("org.robolectric:robolectric:4.17")
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

tasks.withType<Test>().configureEach {
    // Robolectric resolves its Android runtime at test execution time. Use Maven
    // Central's canonical endpoint instead of the repo1 CDN default so CI does
    // not fail before any test method runs when that CDN is unavailable.
    systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2")
    systemProperty("robolectric.dependency.repo.id", "mavenCentral")

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
