// OPEN (Apache-2.0). Clean-room DSP pitch detectors implemented from the
// published papers only:
//   YIN — de Cheveigné & Kawahara, "YIN, a fundamental frequency estimator
//         for speech and music", JASA 2002.
//   MPM — McLeod & Wyvill, "A Smarter Way To Find Pitch", ICMC 2005.
// LICENSING LANDMINE: do NOT port code from TarsosDSP (GPLv3) or the pYIN
// Vamp plugin (GPL) into this module — see the repo README.
plugins {
    id("bocal.kotlin.jvm")
}

dependencies {
    api(project(":core:model"))
    testImplementation(project(":test-fixtures"))
}
