package dev.baseline.contracts

import java.io.DataInputStream
import org.junit.Assert.assertEquals
import org.junit.Test

/** Pins the emitted bytecode level: JDK 21 builds must still emit Java 17 class files (major 61). */
class ClassFileVersionTest {
    @Test fun mainClassesTargetJava17() {
        val stream = checkNotNull(Resolution::class.java.getResourceAsStream("Resolution.class")) { "class file not found" }
        DataInputStream(stream).use {
            assertEquals("magic", 0xCAFEBABE.toInt(), it.readInt())
            it.readUnsignedShort()
            assertEquals("class-file major version", 61, it.readUnsignedShort())
        }
    }
}
