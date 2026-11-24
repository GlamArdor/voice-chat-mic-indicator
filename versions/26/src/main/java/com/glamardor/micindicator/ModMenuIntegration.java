package com.glamardor.micindicator;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.network.chat.Component;

/**
 * Provides the in-game settings screen via Mod Menu + Cloth Config. This class
 * is only loaded through the "modmenu" entrypoint, so the mod still runs fine
 * when Mod Menu / Cloth Config are absent.
 */
public class ModMenuIntegration implements ModMenuApi {

	private static final String P = "config." + VoicechatAddonPlugin.PLUGIN_ID + ".";

	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return parent -> {
			ModConfig c = ModConfig.get();

			ConfigBuilder builder = ConfigBuilder.create()
					.setParentScreen(parent)
					.setTitle(Component.translatable(P + "title"))
					.setSavingRunnable(ModConfig::save);

			ConfigEntryBuilder eb = builder.entryBuilder();
			ConfigCategory cat = builder.getOrCreateCategory(Component.translatable(P + "category"));

			cat.addEntry(eb.startBooleanToggle(Component.translatable(P + "enabled"), c.enabled)
					.setDefaultValue(true)
					.setTooltip(Component.translatable(P + "enabled.tooltip"))
					.setSaveConsumer(v -> c.enabled = v)
					.build());

			cat.addEntry(eb.startBooleanToggle(Component.translatable(P + "show_self"), c.showSelf)
					.setDefaultValue(true)
					.setTooltip(Component.translatable(P + "show_self.tooltip"))
					.setSaveConsumer(v -> c.showSelf = v)
					.build());

			cat.addEntry(eb.startIntSlider(Component.translatable(P + "size"), c.size, 4, 24)
					.setDefaultValue(10)
					.setTextGetter(v -> Component.literal(v + " px"))
					.setTooltip(Component.translatable(P + "size.tooltip"))
					.setSaveConsumer(v -> c.size = v)
					.build());

			cat.addEntry(eb.startIntSlider(Component.translatable(P + "opacity"), c.opacity, 0, 100)
					.setDefaultValue(41)
					.setTextGetter(v -> Component.literal(v + "%"))
					.setTooltip(Component.translatable(P + "opacity.tooltip"))
					.setSaveConsumer(v -> c.opacity = v)
					.build());

			cat.addEntry(eb.startIntSlider(Component.translatable(P + "height"), c.height, 0, 150)
					.setDefaultValue(30)
					.setTextGetter(v -> Component.literal(String.format("%.2f", v / 100.0)))
					.setTooltip(Component.translatable(P + "height.tooltip"))
					.setSaveConsumer(v -> c.height = v)
					.build());

			cat.addEntry(eb.startIntSlider(Component.translatable(P + "distance"), c.distance, 4, 128)
					.setDefaultValue(48)
					.setTextGetter(v -> Component.translatable(P + "blocks", v))
					.setTooltip(Component.translatable(P + "distance.tooltip"))
					.setSaveConsumer(v -> c.distance = v)
					.build());

			cat.addEntry(eb.startBooleanToggle(Component.translatable(P + "through_walls"), c.throughWalls)
					.setDefaultValue(true)
					.setTooltip(Component.translatable(P + "through_walls.tooltip"))
					.setSaveConsumer(v -> c.throughWalls = v)
					.build());

			cat.addEntry(eb.startBooleanToggle(Component.translatable(P + "pulse"), c.pulse)
					.setDefaultValue(true)
					.setTooltip(Component.translatable(P + "pulse.tooltip"))
					.setSaveConsumer(v -> c.pulse = v)
					.build());

			cat.addEntry(eb.startBooleanToggle(Component.translatable(P + "fade"), c.fade)
					.setDefaultValue(true)
					.setTooltip(Component.translatable(P + "fade.tooltip"))
					.setSaveConsumer(v -> c.fade = v)
					.build());

			return builder.build();
		};
	}
}
