// OPEN (Apache-2.0). The haptics IR — breakpoint envelopes of intensity +
// sharpness plus scalable primitives — is the cross-app data contract; Bocal
// is its first production consumer (strategic analysis §2). This module stays
// pure JVM: the Android VibrationEffect renderer lives in :app, behind the
// HapticRenderer interface, with capability checks and fallbacks.
plugins {
    id("bocal.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
}
