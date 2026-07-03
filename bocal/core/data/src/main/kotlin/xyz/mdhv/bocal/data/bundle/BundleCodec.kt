package xyz.mdhv.bocal.data.bundle

import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.serialization.json.Json

/**
 * Reads/writes `.bocalbundle` files: a zip containing `bundle.json` plus any
 * audio entries under `audio/`. Pure JVM; Android hands it content-resolver
 * streams.
 */
object BundleCodec {

    const val JSON_ENTRY = "bundle.json"
    const val AUDIO_DIR = "audio/"

    /** Newest schema this build understands. */
    private val json = Json {
        ignoreUnknownKeys = true   // older app + newer bundle: open, drop extras
        encodeDefaults = true      // schemaVersion always materializes
        prettyPrint = false
    }

    class UnsupportedSchemaException(val version: Int) :
        RuntimeException("Bundle schemaVersion $version is newer than supported ${SessionBundle.SCHEMA_VERSION}")

    fun encodeJson(bundle: SessionBundle): String = json.encodeToString(SessionBundle.serializer(), bundle)

    fun decodeJson(text: String): SessionBundle {
        val bundle = json.decodeFromString(SessionBundle.serializer(), text)
        // Reject bundles from the future; same-or-older versions migrate here
        // as the schema evolves (none needed at v1).
        if (bundle.schemaVersion > SessionBundle.SCHEMA_VERSION) {
            throw UnsupportedSchemaException(bundle.schemaVersion)
        }
        return bundle
    }

    /** Write a bundle zip; [audio] maps entry name (e.g. "attempt-3.wav") to bytes. */
    fun write(bundle: SessionBundle, audio: Map<String, ByteArray>, out: OutputStream) {
        val stamped = bundle.copy(audioEntries = audio.keys.sorted())
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(JSON_ENTRY))
            zip.write(encodeJson(stamped).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            for ((name, bytes) in audio.toSortedMap()) {
                zip.putNextEntry(ZipEntry(AUDIO_DIR + name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }

    data class Loaded(val bundle: SessionBundle, val audio: Map<String, ByteArray>)

    fun read(input: InputStream): Loaded {
        var bundle: SessionBundle? = null
        val audio = LinkedHashMap<String, ByteArray>()
        ZipInputStream(input).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                when {
                    entry.name == JSON_ENTRY ->
                        bundle = decodeJson(zip.readBytes().toString(Charsets.UTF_8))
                    entry.name.startsWith(AUDIO_DIR) && !entry.isDirectory ->
                        audio[entry.name.removePrefix(AUDIO_DIR)] = zip.readBytes()
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return Loaded(
            bundle = requireNotNull(bundle) { "No $JSON_ENTRY entry in bundle" },
            audio = audio,
        )
    }
}
