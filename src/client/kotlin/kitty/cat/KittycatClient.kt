package kitty.cat

import com.mojang.blaze3d.platform.InputConstants
import kitty.cat.config.ConfigManager
import kitty.cat.features.dungeons.AutoLB
import kitty.cat.features.visual.ArrowTracers
import kitty.cat.features.visual.ClickGui as ClickGuiFeature
import kitty.cat.gui.clickgui.ClickGui
import kitty.cat.gui.features.Feature
import kitty.cat.gui.features.settings.KeybindSetting
import kitty.cat.render.nanovg.NVGPIPRenderer
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.rendering.v1.SpecialGuiElementRegistry
import net.minecraft.client.Minecraft
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

	override fun onInitializeClient() {
		SpecialGuiElementRegistry.register { ctx -> NVGPIPRenderer(ctx.vertexConsumers()) }

		ConfigManager.initialize(featureList)
		ClientLifecycleEvents.CLIENT_STOPPING.register {
			ConfigManager.saveNow()
		}

		ClientTickEvents.END_CLIENT_TICK.register { client ->
			ConfigManager.onTick()

			if (openGui) {
				openGui = false

				ClickGuiFeature.openGui()
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
			)
		}

		ArrowTracers.register()
		AutoLB.register()
	}
}
