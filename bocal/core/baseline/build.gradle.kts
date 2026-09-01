// CLOSED (proprietary) — see LICENSE in this module. The longitudinal trend
// engine shared across domains (music here; archery is the other consumer).
// Only the TrendAnalyzer interface in :core:model is public contract; keep
// that seam stable and versioned.
plugins {
    id("bocal.kotlin.jvm")
}

dependencies {
    api(project(":core:model"))
}
