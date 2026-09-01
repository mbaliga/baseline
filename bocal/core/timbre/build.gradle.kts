// OPEN (Apache-2.0). Tone-quality DSP: spectral centroid, harmonic amplitudes,
// tristimulus, odd/even ratio, spectral flux. Framed to the learner as "tone
// brightness" and "tone steadiness", never raw physics (brief §2.4).
plugins {
    id("bocal.kotlin.jvm")
}

dependencies {
    testImplementation(project(":test-fixtures"))
}
