// OPEN (Apache-2.0). Session-bundle interchange format (.bocalbundle) —
// versioned JSON + optional attached audio in a zip. Designed from day one as
// the file-based coach-exchange contract (brief §3, §4 v3). Room/DAOs join
// this module when the journal features land; the bundle codec stays pure JVM.
plugins {
    id("bocal.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
}

dependencies {
    api(project(":core:model"))
    implementation(libs.kotlinx.serialization.json)
}
