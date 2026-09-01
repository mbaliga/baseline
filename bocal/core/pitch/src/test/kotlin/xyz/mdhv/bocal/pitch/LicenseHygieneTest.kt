package xyz.mdhv.bocal.pitch

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * The GPL landmine guard (brief §1): TarsosDSP (GPLv3) and the pYIN Vamp
 * plugin (GPL) must never appear in any build file — linking either would
 * GPL-encumber the whole app including the closed Baseline engine.
 */
class LicenseHygieneTest {

    @Test
    fun `no GPL pitch libraries anywhere in the build`() {
        val projectRoot = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
            .first { File(it, "settings.gradle.kts").exists() && File(it, "gradle").exists() }

        val buildFiles = projectRoot.walkTopDown()
            .filter { it.name.endsWith(".gradle.kts") || it.name == "libs.versions.toml" }
            .filter { !it.path.contains("${File.separator}build${File.separator}") }

        val banned = listOf("tarsos", "be.tarsos", "pyin", "vamp")
        for (file in buildFiles) {
            // Comments may (and do) mention the banned libraries as warnings;
            // only effective build code counts.
            val text = file.readLines()
                .map { it.substringBefore("//").substringBefore("#") }
                .joinToString("\n")
                .lowercase()
            for (needle in banned) {
                assertFalse(
                    "GPL-risk dependency '$needle' referenced in ${file.relativeTo(projectRoot)}",
                    text.contains(needle),
                )
            }
        }
    }
}
