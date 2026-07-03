import com.android.build.gradle.LibraryExtension
import org.gradle.api.artifacts.VersionCatalogsExtension

// Convention for Android library modules with Compose UI (feature modules).
plugins {
    id("bocal.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
}

configure<LibraryExtension> {
    buildFeatures {
        compose = true
    }
}

val libs = the<VersionCatalogsExtension>().named("libs")

dependencies {
    val bom = libs.findLibrary("androidx-compose-bom").get()
    "implementation"(platform(bom))
    "androidTestImplementation"(platform(bom))
    "implementation"(libs.findLibrary("androidx-compose-ui").get())
    "implementation"(libs.findLibrary("androidx-compose-ui-graphics").get())
    "implementation"(libs.findLibrary("androidx-compose-material3").get())
    "implementation"(libs.findLibrary("androidx-compose-ui-tooling-preview").get())
    "debugImplementation"(libs.findLibrary("androidx-compose-ui-tooling").get())
}
