plugins { alias(libs.plugins.kotlin.jvm) }

dependencies { testImplementation(libs.junit) }
tasks.test { useJUnit() }

// Built with JDK 21 (what CI runs), emitting Java 17 bytecode (class-file major 61).
kotlin {
    jvmToolchain(21)
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
tasks.withType<JavaCompile>().configureEach { options.release.set(17) }
