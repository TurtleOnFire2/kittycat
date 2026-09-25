package kitty.cat

import com.mojang.blaze3d.platform.InputConstants
import com.mojang.brigadier.arguments.FloatArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import kitty.cat.config.ConfigManager
import kitty.cat.features.dungeons.LeverTriggerbot
import kitty.cat.features.dungeons.Relics
import kitty.cat.features.dungeons.Storm
import kitty.cat.features.dungeons.Terminals
import kitty.cat.features.huds.BestiaryHud
import kitty.cat.utils.BoneUtils
import kitty.cat.features.kuudra.RendMacro
import kitty.cat.features.misc.ChatMacros
import kitty.cat.features.misc.Pests
import kitty.cat.features.debug.PearlLandingDebug
import kitty.cat.features.visual.BestiaryESP
import kitty.cat.features.visual.CatEars
import kitty.cat.features.visual.ClickGui as ClickGuiFeature
import kitty.cat.gui.Hud
import kitty.cat.gui.clickgui.ClickGui
import kitty.cat.features.Feature
import kitty.cat.features.huds.AlertHud
import kitty.cat.features.visible
import kitty.cat.features.huds.BackboneHud
import kitty.cat.features.huds.BuildHud
import kitty.cat.features.huds.KuudraHpHud
import kitty.cat.features.kuudra.Build
import kitty.cat.features.huds.SupplyHud
import kitty.cat.features.huds.SupplyAlertHud
import kitty.cat.features.huds.GiantAlertHud
import kitty.cat.features.kuudra.AutoGFS
import kitty.cat.features.kuudra.AutoWarp
import kitty.cat.features.kuudra.BackboneAlert
import kitty.cat.features.kuudra.CratePriority
import kitty.cat.features.kuudra.KuudraDisplay
import kitty.cat.features.kuudra.EtherwarpWaypoints
import kitty.cat.features.kuudra.Fireball
import kitty.cat.features.kuudra.PearlWaypoints
import kitty.cat.features.kuudra.RendDamage
import kitty.cat.features.kuudra.SafeSpots
import kitty.cat.features.kuudra.Stun
import kitty.cat.features.kuudra.Supplies
import kitty.cat.features.kuudra.SupplyCheats
import kitty.cat.features.misc.EtherPath
import kitty.cat.features.misc.FarmHelper
import kitty.cat.features.settings.KeybindSetting
import kitty.cat.render.nanovg.NVGPIPRenderer
import kitty.cat.utils.Chat
import kitty.cat.utils.LocationUtils
import kitty.cat.utils.NameChanger
import kitty.cat.utils.LocationManager
import kitty.cat.render.world.RenderLayers
import kitty.cat.utils.ClickUtils
import kitty.cat.utils.KuudraUtils
import kitty.cat.utils.RotationUtils
import kitty.cat.utils.Schedule
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument
import net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.fabricmc.fabric.api.client.rendering.v1.PictureInPictureRendererRegistry
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.ClickEvent
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
			.visible()

	val mc get() = Minecraft.getInstance()
	var openGui = false
	var openHud = false

	var keybindShowHud: KeyMapping? = null

	override fun onInitializeClient() {
		// Force class-init so the custom render pipelines are registered before the
		// renderer precompiles static pipelines, not lazily mid-frame on first draw.
		RenderLayers.LINES_THROUGH_WALLS

		PictureInPictureRendererRegistry.register { ctx -> NVGPIPRenderer(ctx.bufferSource()) }

		ConfigManager.initialize(featureList)
		ClientLifecycleEvents.CLIENT_STOPPING.register {
			ConfigManager.saveNow()
			kitty.cat.render.skija.SkijaRenderer.cleanup()
		}

		val keybindCategory = KeyMapping.Category(Identifier.fromNamespaceAndPath("kittycat", "general"))
		keybindShowHud = KeyMappingHelper.registerKeyMapping(
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
						literal("config")
							.then(literal("list").executes {
								val names = ConfigManager.profileNames()
								Chat.send(if (names.isEmpty()) "No saved configs. Use /kc config save <name>." else "Configs: ${names.joinToString(", ")}")
								1
							})
							.then(literal("save").then(argument("name", StringArgumentType.word()).executes { ctx ->
								val name = StringArgumentType.getString(ctx, "name")
								Chat.send(if (ConfigManager.saveProfile(name)) "Saved config '$name'." else "Couldn't save config. Names may use letters, numbers, _ and - (max 32 characters).")
								1
							}))
							.then(literal("load").then(argument("name", StringArgumentType.word()).executes { ctx ->
								val name = StringArgumentType.getString(ctx, "name")
								Chat.send(if (ConfigManager.loadProfile(name)) "Loaded config '$name'." else "Config '$name' wasn't found or couldn't be loaded.")
								1
							}))
							.then(literal("delete").then(argument("name", StringArgumentType.word()).executes { ctx ->
								val name = StringArgumentType.getString(ctx, "name")
								Chat.send(if (ConfigManager.deleteProfile(name)) "Deleted config '$name'." else "Config '$name' wasn't found.")
								1
							}))
					)
					.then(
						literal("be").executes {
							BestiaryESP.openGui = true
							1
						}
					)
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
						literal("macros").executes {
							ChatMacros.openGui = true
							1
						}
					)
					.executes {
						openGui = true
						1
					}
			)
			dispatcher.register(
				literal("rotate")
					.then(
						argument("yaw", FloatArgumentType.floatArg())
							.then(
								argument("pitch", FloatArgumentType.floatArg())
									.executes { ctx ->
										val yaw = FloatArgumentType.getFloat(ctx, "yaw")
										val pitch = FloatArgumentType.getFloat(ctx, "pitch")
										RotationUtils.rotate(yaw, pitch)
										1
									}
							)
					)
			)
		}

		CratePriority.register()
		LocationManager.register()
		KuudraUtils.register()
		PearlWaypoints.register()
		Supplies.register()
		Build.register()
		RendMacro.register()
		PearlLandingDebug.register()
		kitty.cat.render.world.Render3D.register()
		EtherwarpWaypoints.register()
		EtherPath.register()
		CatEars.register()
		Pests.register()
		Hud.register()
		BestiaryHud.register()
		BestiaryESP.register()
		ChatMacros.register()
		Schedule.register()
		Storm.register()
		Relics.register()
		Terminals.register()
		LeverTriggerbot.register()
		LocationUtils.register()
		BoneUtils.register()
		Stun.register()
		RendDamage.register()
		SupplyCheats.register()
		SafeSpots.register()
		FarmHelper.register()
		KuudraDisplay.register()
		BackboneAlert.register()
		AutoGFS.register()
		AutoWarp.register()
		Fireball.register()
		ClickUtils.register()

		BackboneHud
		BuildHud
		KuudraHpHud
		SupplyHud
		SupplyAlertHud
		GiantAlertHud
		AlertHud
	}
}
