// OPEN (Apache-2.0). Real-time note visualization + accuracy (MVP feature #1).
// Colorblind-safe by construction: every visual state comes from a
// :core:hyle StateEncoding (hue + shape + icon + label), never hue alone.
plugins {
    id("bocal.android.library.compose")
}

android {
    namespace = "xyz.mdhv.bocal.feature.tuner"
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:pitch"))
    implementation(project(":core:audio"))
    implementation(project(":core:hyle"))
    implementation(project(":core:haptics"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
}
