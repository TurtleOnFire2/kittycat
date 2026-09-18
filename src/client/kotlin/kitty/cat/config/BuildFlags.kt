package kitty.cat.config

import java.util.Properties

object BuildFlags {
    private const val RESOURCE_PATH = "kittycat-build.properties"
    private const val SYSTEM_PROPERTY = "kittycat.cheats"

    // -Dkittycat.cheats=false lets a dev run launch as the legit build without a separate jar.
    val CHEATS_ENABLED: Boolean = System.getProperty(SYSTEM_PROPERTY)?.toBoolean()
        ?: BuildFlags::class.java.classLoader
            .getResourceAsStream(RESOURCE_PATH)
            ?.use { stream ->
                Properties().apply { load(stream) }.getProperty("cheats", "true").toBoolean()
            } ?: true
}
