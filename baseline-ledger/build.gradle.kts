plugins { alias(libs.plugins.kotlin.jvm) }

dependencies {
    implementation(project(":baseline-contracts"))
    testImplementation(libs.junit)
}
tasks.test { useJUnit() }
