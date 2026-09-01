// OPEN (Apache-2.0). Capture behind the AudioEngine seam. Today: AudioRecord
// (UNPROCESSED source, float PCM, native rate) — works everywhere, ~20–40 ms.
// Next: an Oboe/AAudio NDK engine (MMAP EXCLUSIVE, 2-burst buffers) drops in
// behind the SAME interface when the NDK toolchain enters the loop; nothing
// above this module changes (brief §2.1–2.2).
plugins {
    id("bocal.android.library")
}

android {
    namespace = "xyz.mdhv.bocal.audio"
}

dependencies {
    implementation(libs.kotlinx.coroutines.android)
}
