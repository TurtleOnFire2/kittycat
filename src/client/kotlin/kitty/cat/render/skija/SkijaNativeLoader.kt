package kitty.cat.render.skija

import org.slf4j.LoggerFactory
import io.github.humbleui.skija.impl.Library
import net.fabricmc.loader.api.FabricLoader
import net.fabricmc.loader.impl.launch.FabricLauncherBase
import java.io.OutputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.DigestInputStream
import java.security.MessageDigest
import java.time.Duration
import java.util.*

object SkijaNativeLoader {
    private val logger = LoggerFactory.getLogger("Kittycat/Skija")
    private const val MAVEN_CENTRAL = "https://repo.maven.apache.org/maven2"
    private const val GROUP_PATH = "io/github/humbleui"

    fun load() {
        val platform = currentPlatform()
        val version = "0.143.17"
        val artifact = "skija-${platform.id}"
        val fileName = "$artifact-$version.jar"
        val directory = FabricLoader.getInstance().gameDir.resolve(".kittycat/native")
        val jar = directory.resolve(fileName)
        val checksumFile = directory.resolve("$fileName.sha1")
        val uri = URI.create("$MAVEN_CENTRAL/$GROUP_PATH/$artifact/$version/$fileName")

        Files.createDirectories(directory)
        val client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()
        val expectedSha1 = readSha1(checksumFile) ?: resolveExpectedSha1(client, uri, checksumFile)
        val cachedSha1 = if (Files.isRegularFile(jar)) sha1(jar) else null
        if (cachedSha1 != expectedSha1) {
            if (cachedSha1 != null) {
                logger.warn("Cached Skija native jar {} has checksum {}; expected {}. Redownloading it.", jar, cachedSha1, expectedSha1)
            }
            downloadNativeJar(client, uri, jar, expectedSha1)
        } else {
            logger.info("Using cached Skija native jar {}", jar)
        }

        val launcher = FabricLauncherBase.getLauncher()
        launcher.addToClassPath(jar)

        val versionResource = "io/github/humbleui/skija/${platform.resourcePath}/skija.version"
        checkNotNull(launcher.targetClassLoader.getResource(versionResource)) {
            "Skija native jar $jar was added to the classpath but $versionResource is unavailable"
        }

        Library.load()
        logger.info("Loaded Skija native jar {}", jar)
    }

    private fun downloadNativeJar(client: HttpClient, uri: URI, target: Path, expectedSha1: String) {
        val temporary = Files.createTempFile(target.parent, "${target.fileName}-", ".tmp")

        logger.info("Downloading Skija native jar from {}", uri)
        try {
            val request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMinutes(2))
                .header("User-Agent", "Kittycat")
                .GET()
                .build()
            val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())

            response.body().use { body ->
                check(response.statusCode() == 200) {
                    "Maven Central returned HTTP ${response.statusCode()} for $uri"
                }

                val digest = MessageDigest.getInstance("SHA-1")
                DigestInputStream(body, digest).use { input ->
                    Files.copy(input, temporary, StandardCopyOption.REPLACE_EXISTING)
                }

                val actualSha1 = digest.digest().toHexString()
                check(actualSha1 == expectedSha1) {
                    "Skija native jar checksum mismatch for $uri: expected $expectedSha1, got $actualSha1"
                }
            }

            moveIntoPlace(temporary, target)
        } catch (exception: Exception) {
            throw IllegalStateException("Failed to download Skija native jar from $uri", exception)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private fun resolveExpectedSha1(client: HttpClient, jarUri: URI, checksumFile: Path): String {
        return try {
            downloadSha1(client, jarUri).also { checksum ->
                val temporary = Files.createTempFile(checksumFile.parent, "${checksumFile.fileName}-", ".tmp")
                try {
                    Files.writeString(temporary, "$checksum\n")
                    moveIntoPlace(temporary, checksumFile)
                } finally {
                    Files.deleteIfExists(temporary)
                }
            }
        } catch (exception: Exception) {
            val cached = readSha1(checksumFile)
            if (cached == null) throw exception

            logger.warn("Could not refresh Skija checksum from Maven Central; using cached checksum from {}", checksumFile, exception)
            cached
        }
    }

    private fun downloadSha1(client: HttpClient, jarUri: URI): String {
        val checksumUri = URI.create("$jarUri.sha1")
        val request = HttpRequest.newBuilder(checksumUri)
            .timeout(Duration.ofSeconds(30))
            .header("User-Agent", "Kittycat")
            .GET()
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        check(response.statusCode() == 200) {
            "Maven Central returned HTTP ${response.statusCode()} for $checksumUri"
        }

        val checksum = response.body().trim().substringBefore(' ').lowercase(Locale.ROOT)
        check(checksum.matches(Regex("[0-9a-f]{40}"))) {
            "Maven Central returned an invalid SHA-1 checksum for $jarUri"
        }
        return checksum
    }

    private fun readSha1(path: Path): String? {
        if (!Files.isRegularFile(path)) return null
        val checksum = Files.readString(path).trim().lowercase(Locale.ROOT)
        return checksum.takeIf { it.matches(Regex("[0-9a-f]{40}")) }
    }

    private fun sha1(path: Path): String {
        val digest = MessageDigest.getInstance("SHA-1")
        DigestInputStream(Files.newInputStream(path), digest).use { input ->
            input.transferTo(OutputStream.nullOutputStream())
        }
        return digest.digest().toHexString()
    }

    private fun moveIntoPlace(source: Path, target: Path) {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }


    private fun currentPlatform(): Platform {
        val os = System.getProperty("os.name").lowercase(Locale.ROOT)
        val architecture = System.getProperty("os.arch").lowercase(Locale.ROOT)
        val arch = when (architecture) {
            "amd64", "x86_64", "x64" -> "x64"
            "aarch64", "arm64" -> "arm64"
            else -> throw IllegalStateException("Skija does not provide an Kittycat native for architecture $architecture")
        }
        val platformId = when {
            os.startsWith("windows") -> "windows-$arch"
            os.contains("mac") || os.contains("darwin") -> "macos-$arch"
            os.contains("linux") -> "linux-$arch"
            else -> throw IllegalStateException("Skija does not provide an Kittycat native for operating system $os")
        }

        return checkNotNull(PLATFORMS[platformId]) {
            "Skija does not provide an Kittycat native for platform $platformId"
        }
    }

    private fun ByteArray.toHexString(): String = joinToString("") { byte -> "%02x".format(byte) }

    private data class Platform(
        val id: String,
        val resourcePath: String
    )

    private val PLATFORMS = mapOf(
        "linux-x64" to Platform(
            id = "linux-x64",
            resourcePath = "linux/x64"
        ),
        "windows-x64" to Platform(
            id = "windows-x64",
            resourcePath = "windows/x64"
        ),
        "macos-x64" to Platform(
            id = "macos-x64",
            resourcePath = "macos/x64"
        ),
        "macos-arm64" to Platform(
            id = "macos-arm64",
            resourcePath = "macos/arm64"
        )
    )
}
