package kitty.cat

import com.mojang.blaze3d.platform.InputConstants
import kitty.cat.config.ConfigManager
import kitty.cat.features.dungeons.AutoLB
import kitty.cat.features.dungeons.Storm
import kitty.cat.features.huds.BestiaryHud
import kitty.cat.features.misc.ChatMacros
import kitty.cat.features.misc.Pests
import kitty.cat.features.visual.ArrowTracers
import kitty.cat.features.visual.ClickGui as ClickGuiFeature
import kitty.cat.gui.Hud
import kitty.cat.gui.clickgui.ClickGui
import kitty.cat.gui.features.Feature
import kitty.cat.gui.features.settings.KeybindSetting
import kitty.cat.render.nanovg.NVGPIPRenderer
import kitty.cat.utils.Schedule
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.fabricmc.fabric.api.client.rendering.v1.SpecialGuiElementRegistry
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.resources.Identifier
import org.lwjgl.glfw.GLFW
import org.reflections.Reflections

object KittycatClient : ClientModInitializer {
	private val keybindPressedState = mutableMapOf<KeybindSetting, Boolean>()

	private val featureList: List<Feature> =
		Reflections("kitty.cat.features")
			.getSubTypesOf(Feature::class.java)
			.mapNotNull { clazz ->
				try {
					clazz.getField("INSTANCE").get(null) as Feature
				} catch (_: Exception) {
					null
				}
			}

	val mc get() = Minecraft.getInstance()
	var openGui = false
	var openHud = false

	var keybindShowHud: KeyMapping? = null

	override fun onInitializeClient() {
		SpecialGuiElementRegistry.register { ctx -> NVGPIPRenderer(ctx.vertexConsumers()) }

		ConfigManager.initialize(featureList)
		ClientLifecycleEvents.CLIENT_STOPPING.register {
			ConfigManager.saveNow()
		}

		val keybindCategory = KeyMapping.Category(Identifier.fromNamespaceAndPath("kittycat", "general"))
		keybindShowHud = KeyBindingHelper.registerKeyBinding(
			KeyMapping(
				"key.kittycat.hud_insight",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_LEFT_ALT,
				keybindCategory
			)
		)

		ClientTickEvents.END_CLIENT_TICK.register { client ->
			ConfigManager.onTick()

			if (openGui) {
				openGui = false
				ClickGuiFeature.openGui()
			}

			if (openHud) {
				openHud = false
				Hud.open()
			}

			if (client.screen is ClickGui) return@register

			val window = client.window
			featureList.forEach { feature ->
				feature.keybindSettings.forEach { setting ->
					if (setting.keyCode == KeybindSetting.UNBOUND) {
						keybindPressedState[setting] = false
						return@forEach
					}

					val pressedNow = InputConstants.isKeyDown(window, setting.keyCode)
					val pressedBefore = keybindPressedState[setting] ?: false
					keybindPressedState[setting] = pressedNow

					if (pressedNow && !pressedBefore) {
						feature.onKeybindPressed(setting)
					}
				}
			}
		}

		ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
			dispatcher.register(
				literal("kc")
					.then(
						literal("gui").executes { context ->
							openGui = true
							1
						}
					)
					.then(
						literal("hud").executes {
							openHud = true
							1
						}
					)
					.then(
						literal("resetsession").executes {
							BestiaryHud.resetSession()
							1
						}
					)
					.then(
						literal("macros").executes {
							ChatMacros.openGui = true
							1
						}
					)
			)
		}

		ArrowTracers.register()
		AutoLB.register()
		Pests.register()
		Hud.register()
		BestiaryHud.register()
		ChatMacros.register()
		Schedule.register()
		Storm.register()
	}
}
