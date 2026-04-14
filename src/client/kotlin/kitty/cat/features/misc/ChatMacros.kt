package kitty.cat.features.misc

import com.google.gson.GsonBuilder
import kitty.cat.KittycatClient.mc
import kitty.cat.gui.chatmacros.ChatMacrosScreen
import kitty.cat.gui.categories.Categories
import kitty.cat.gui.features.Feature
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.gui.screens.Screen
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

object ChatMacros : Feature("Chat Macros", "", Categories.Category.MISC) {
    data class MacroView(
        val trigger: String,
        val command: String,
        val active: Boolean
    )

    private data class MacroEntry(
        var trigger: String = "",
        var command: String = "",
        var active: Boolean = true
    )

    private data class MacroFile(
        val macros: List<MacroEntry> = emptyList()
    )

    private const val MACRO_MAX_LENGTH = 160

    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val macroConfigPath: Path = FabricLoader.getInstance().configDir.resolve("kittycat-macros.json")
    private val macros = mutableListOf<MacroEntry>()

    var openGui = false

    val openMacroManager = actionSetting("Open Macro Manager") {
        openGui = true
    }

    init {
        loadMacros()
    }

    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            if (openGui) {
                openManagerScreen(mc.screen)
                openGui = false
            }
        }
    }

    fun openManagerScreen(parent: Screen? = mc.screen) {
        mc.setScreen(ChatMacrosScreen(parent))
    }

    fun listMacros(): List<MacroView> {
        return macros.map { MacroView(trigger = it.trigger, command = it.command, active = it.active) }
    }

    fun addMacro(): Int {
        macros += MacroEntry()
        saveMacros()
        return macros.lastIndex
    }

    fun deleteMacro(index: Int): Boolean {
        if (index !in macros.indices) return false
        macros.removeAt(index)
        saveMacros()
        return true
    }

    fun toggleMacroActive(index: Int): Boolean {
        val macro = macros.getOrNull(index) ?: return false
        macro.active = !macro.active
        saveMacros()
        return true
    }

    fun setTrigger(index: Int, value: String): Boolean {
        val macro = macros.getOrNull(index) ?: return false
        val sanitized = sanitize(value)
        if (macro.trigger == sanitized) return true
        macro.trigger = sanitized
        saveMacros()
        return true
    }

    fun setCommand(index: Int, value: String): Boolean {
        val macro = macros.getOrNull(index) ?: return false
        val sanitized = sanitize(value)
        if (macro.command == sanitized) return true
        macro.command = sanitized
        saveMacros()
        return true
    }

    fun handleChat(unformatted: String) {
        if (!enabled) return

        for (macro in macros) {
            if (!macro.active) continue

            val trigger = macro.trigger.trim()
            val command = macro.command.trim().removePrefix("/")
            if (trigger.isEmpty() || command.isEmpty()) continue

            val matched = runCatching {
                Regex(trigger).containsMatchIn(unformatted)
            }.getOrDefault(false)
            if (!matched) continue

            mc.connection?.sendCommand(command)
        }
    }

    private fun sanitize(raw: String): String {
        return raw
            .replace('\n', ' ')
            .replace('\r', ' ')
            .take(MACRO_MAX_LENGTH)
    }

    private fun loadMacros() {
        if (!Files.exists(macroConfigPath)) return

        val file = runCatching {
            Files.newBufferedReader(macroConfigPath, StandardCharsets.UTF_8).use { reader ->
                gson.fromJson(reader, MacroFile::class.java)
            }
        }.getOrNull() ?: return

        macros.clear()
        file.macros.forEach { macro ->
            macros += MacroEntry(
                trigger = sanitize(macro.trigger),
                command = sanitize(macro.command),
                active = macro.active
            )
        }
    }

    private fun saveMacros() {
        runCatching {
            Files.createDirectories(macroConfigPath.parent)
            Files.newBufferedWriter(
                macroConfigPath,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
            ).use { writer ->
                gson.toJson(MacroFile(macros = macros), writer)
            }
        }
    }
}
