import com.android.build.api.artifact.SingleArtifact

// Thin composition root: wires the seams together, owns the manifest.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "xyz.mdhv.bocal"
    compileSdk = 35

    defaultConfig {
        applicationId = "xyz.mdhv.bocal"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            // Shrinking comes with the licensing/Pro work; keep the MVP build simple.
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":feature:tuner"))
    implementation(project(":core:audio"))
    implementation(project(":core:pitch"))
    implementation(project(":core:haptics"))
    implementation(project(":core:hyle"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

// BACKENDLESS is a hard rule (brief §0): the merged manifest — including
// anything contributed by transitive dependencies — must never contain the
// INTERNET permission. "No data collected / cannot phone home" only stays a
// truthful Play listing claim while this task passes.
androidComponents {
    onVariants { variant ->
        val variantName = variant.name.replaceFirstChar { it.uppercase() }
        val mergedManifest = variant.artifacts.get(SingleArtifact.MERGED_MANIFEST)
        val guard = tasks.register("check${variantName}NoInternetPermission") {
            inputs.file(mergedManifest)
            doLast {
                // Match actual permission declarations, not XML comments.
                val declared = Regex("""<uses-permission[^>]*android\.permission\.INTERNET""")
                val manifestText = mergedManifest.get().asFile.readText()
                check(!declared.containsMatchIn(manifestText)) {
                    "BACKENDLESS rule violated: android.permission.INTERNET found in the " +
                        "merged $variantName manifest. Find the dependency that added it " +
                        "and remove it — Bocal must not be able to phone home."
                }
            }
        }
        tasks.matching { it.name == "assemble$variantName" }.configureEach { dependsOn(guard) }
    }
}
