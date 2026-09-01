import com.android.build.gradle.LibraryExtension
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Convention for Android library modules. Min SDK 26 (Android 8.0) is required
// for AAudio via Oboe; target/compile SDK 35.
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

configure<LibraryExtension> {
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

tasks.withType<Test>().configureEach {
    useJUnit()
    testLogging {
        events("failed", "skipped")
    }
}

val libs = the<VersionCatalogsExtension>().named("libs")

dependencies {
    "testImplementation"(libs.findLibrary("junit").get())
}
