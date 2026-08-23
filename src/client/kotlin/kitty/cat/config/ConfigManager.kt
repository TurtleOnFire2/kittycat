package kitty.cat.config

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kitty.cat.features.Feature
import net.fabricmc.loader.api.FabricLoader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.Executors

object ConfigManager {
    private const val SAVE_DELAY_TICKS = 10

    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val configPath: Path = FabricLoader.getInstance().configDir.resolve("kittycat.json")

    private var features: List<Feature> = emptyList()
    @Volatile private var dirty = false
    private val writerExecutor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "kittycat-config-writer").apply { isDaemon = true }
    }
    private var tickCounter = 0
    private var lastDirtyTick = 0
    private var suppressDirtyTracking = false

    fun initialize(features: List<Feature>) {
        this.features = features
        withDirtyTrackingSuppressed {
            load()
        }
        dirty = false
        tickCounter = 0
        lastDirtyTick = 0
    }

    fun onTick() {
        tickCounter++
        if (!dirty) return
        if (tickCounter - lastDirtyTick < SAVE_DELAY_TICKS) return
        saveNow(async = true)
    }

    fun markDirty() {
        if (suppressDirtyTracking) return
        dirty = true
        lastDirtyTick = tickCounter
    }

    fun saveNow(async: Boolean = false) {
        if (features.isEmpty()) return

        try {
            Files.createDirectories(configPath.parent)

            val root = JsonObject()
            val featuresObject = JsonObject()
            features.forEach { feature ->
                val featureObject = JsonObject()
                featureObject.addProperty("enabled", feature.enabled)

                val booleans = JsonObject()
                feature.booleanSettings.forEach { setting ->
                    booleans.addProperty(setting.name, setting.value)
                }
                featureObject.add("booleans", booleans)

                val numbers = JsonObject()
                feature.numberSettings.forEach { setting ->
                    numbers.addProperty(setting.name, setting.value)
                }
                featureObject.add("numbers", numbers)

                val ranges = JsonObject()
                feature.rangeSettings.forEach { setting ->
                    val values = JsonObject()
                    values.addProperty("lower", setting.lowerValue)
                    values.addProperty("upper", setting.upperValue)
                    ranges.add(setting.name, values)
                }
                featureObject.add("ranges", ranges)

                val selectors = JsonObject()
                feature.selectorSettings.forEach { setting ->
                    val selectedArray = JsonArray()
                    setting.selected.forEach { selectedArray.add(it) }
                    selectors.add(setting.name, selectedArray)
                }
                featureObject.add("selectors", selectors)

                val colors = JsonObject()
                feature.colorSettings.forEach { setting ->
                    val rgba = JsonObject()
                    rgba.addProperty("r", setting.red)
                    rgba.addProperty("g", setting.green)
                    rgba.addProperty("b", setting.blue)
                    rgba.addProperty("a", setting.alpha)
                    colors.add(setting.name, rgba)
                }
                featureObject.add("colors", colors)

                val keybinds = JsonObject()
                feature.keybindSettings.forEach { setting ->
                    keybinds.addProperty(setting.name, setting.keyCode)
                }
                featureObject.add("keybinds", keybinds)

                val strings = JsonObject()
                feature.stringSettings.forEach { setting ->
                    strings.addProperty(setting.name, setting.value)
                }
                featureObject.add("strings", strings)
                val orders = JsonObject()
                feature.orderSettings.forEach { setting ->
                    val values = JsonArray()
                    setting.order.forEach { values.add(it) }
                    orders.add(setting.name, values)
                }
                featureObject.add("orders", orders)

                featuresObject.add(featureId(feature), featureObject)
            }
            root.add("features", featuresObject)

            val json = gson.toJson(root)
            dirty = false
            val write = writerExecutor.submit {
                try {
                    Files.writeString(
                        configPath,
                        json,
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING,
                        StandardOpenOption.WRITE
                    )
                } catch (_: Exception) {
                    dirty = true
                }
            }
            if (!async) write.get()
        } catch (_: Exception) {
            // Keep dirty=true so the next tick can retry.
        }
    }

    private fun load() {
        if (!Files.exists(configPath)) return

        val root = try {
            Files.newBufferedReader(configPath, StandardCharsets.UTF_8).use { reader ->
                gson.fromJson(reader, JsonObject::class.java)
            }
        } catch (_: Exception) {
            null
        } ?: return

        val featuresObject = root.objectOrNull("features") ?: return

        features.forEach { feature ->
            val featureObject = featuresObject.objectOrNull(featureId(feature)) ?: return@forEach

            featureObject.booleanOrNull("enabled")?.let { feature.setEnabled(it) }

            val booleans = featureObject.objectOrNull("booleans")
            feature.booleanSettings.forEach { setting ->
                booleans?.booleanOrNull(setting.name)?.let { setting.value = it }
            }

            val numbers = featureObject.objectOrNull("numbers")
            feature.numberSettings.forEach { setting ->
                numbers?.doubleOrNull(setting.name)?.let { setting.setValue(it) }
            }

            val ranges = featureObject.objectOrNull("ranges")
            feature.rangeSettings.forEach { setting ->
                val values = ranges?.objectOrNull(setting.name)
                if (values != null) {
                    val lower = values.doubleOrNull("lower") ?: setting.lowerValue
                    val upper = values.doubleOrNull("upper") ?: setting.upperValue
                    setting.setValues(lower, upper)
                } else {
                    val lower = setting.legacyLowerName?.let { numbers?.doubleOrNull(it) }
                    val upper = setting.legacyUpperName?.let { numbers?.doubleOrNull(it) }
                    if (lower != null || upper != null) {
                        setting.setValues(lower ?: setting.lowerValue, upper ?: setting.upperValue)
                    }
                }
            }

            val selectors = featureObject.objectOrNull("selectors")
            feature.selectorSettings.forEach { setting ->
                val saved = selectors?.arrayOrNull(setting.name)
                    ?.asSequence()
                    ?.mapNotNull { it.stringOrNull() }
                    ?.toList()
                    ?: return@forEach
                setting.setSelected(saved)
            }

            val colors = featureObject.objectOrNull("colors")
            feature.colorSettings.forEach { setting ->
                val rgba = colors?.objectOrNull(setting.name) ?: return@forEach
                val r = rgba.intOrNull("r") ?: setting.red
                val g = rgba.intOrNull("g") ?: setting.green
                val b = rgba.intOrNull("b") ?: setting.blue
                val a = rgba.intOrNull("a") ?: setting.alpha
                setting.setRgba(r, g, b, a)
            }

            val keybinds = featureObject.objectOrNull("keybinds")
            feature.keybindSettings.forEach { setting ->
                keybinds?.intOrNull(setting.name)?.let { setting.setKeyCode(it) }
            }

            val strings = featureObject.objectOrNull("strings")
            feature.stringSettings.forEach { setting ->
                strings?.stringOrNull(setting.name)?.let { setting.setValue(it) }
            }
            val orders = featureObject.objectOrNull("orders")
            feature.orderSettings.forEach { setting ->
                val saved = orders?.arrayOrNull(setting.name)?.asSequence()?.mapNotNull { it.stringOrNull() }?.toList() ?: return@forEach
                setting.setOrder(saved)
            }
        }
    }

    private inline fun withDirtyTrackingSuppressed(block: () -> Unit) {
        val previous = suppressDirtyTracking
        suppressDirtyTracking = true
        try {
            block()
        } finally {
            suppressDirtyTracking = previous
        }
    }

    private fun featureId(feature: Feature): String = feature.javaClass.name

    private fun JsonObject.objectOrNull(key: String): JsonObject? {
        val element = this.get(key) ?: return null
        return if (element.isJsonObject) element.asJsonObject else null
    }

    private fun JsonObject.arrayOrNull(key: String): JsonArray? {
        val element = this.get(key) ?: return null
        return if (element.isJsonArray) element.asJsonArray else null
    }

    private fun JsonObject.booleanOrNull(key: String): Boolean? {
        val element = this.get(key) ?: return null
        if (!element.isJsonPrimitive) return null
        return runCatching { element.asBoolean }.getOrNull()
    }

    private fun JsonObject.doubleOrNull(key: String): Double? {
        val element = this.get(key) ?: return null
        if (!element.isJsonPrimitive) return null
        return runCatching { element.asDouble }.getOrNull()
    }

    private fun JsonObject.intOrNull(key: String): Int? {
        val element = this.get(key) ?: return null
        if (!element.isJsonPrimitive) return null
        return runCatching { element.asInt }.getOrNull()
    }

    private fun JsonObject.stringOrNull(key: String): String? {
        val element = this.get(key) ?: return null
        return element.stringOrNull()
    }

    private fun JsonElement.stringOrNull(): String? {
        if (!isJsonPrimitive) return null
        return runCatching { asString }.getOrNull()
    }
}
