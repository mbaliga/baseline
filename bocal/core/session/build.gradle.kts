// OPEN (Apache-2.0). The analysis layer that turns live pitch into
// longitudinal progress: held-note detection, per-session metrics + a score
// for the trend engine, intonation tendency maps, humane streaks, and the
// offline session summarizer. Pure JVM — headless-testable (brief §6 Tier 1).
plugins {
    id("bocal.kotlin.jvm")
}

dependencies {
    api(project(":core:model"))
    api(project(":core:data"))
    testImplementation(project(":test-fixtures"))
    // End-to-end wiring test only: prove synthetic tone → detector → tracker →
    // analyzer → trend engine actually connects across the seams.
    testImplementation(project(":core:pitch"))
    testImplementation(project(":core:baseline"))
}
