package com.f47mod.registry;

import com.f47mod.F47Mod;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;

/** Eigener Kreativ-Reiter mit allen Inhalten des Mods. */
public final class ModItemGroups {
	private ModItemGroups() {
	}

	public static final RegistryKey<ItemGroup> F47_GROUP =
			RegistryKey.of(RegistryKeys.ITEM_GROUP, F47Mod.id("general"));

	public static void register() {
		Registry.register(Registries.ITEM_GROUP, F47_GROUP, FabricItemGroup.builder()
				.displayName(Text.translatable("itemgroup.f47.general"))
				.icon(() -> new ItemStack(ModItems.F47_AIRCRAFT))
				.entries((context, entries) -> {
					// Flugzeuge
					entries.add(ModItems.F47_AIRCRAFT);
					entries.add(ModItems.F47_AUTONOMOUS);

					// Fuehrung und Truppen
					entries.add(ModItems.COMMAND_TABLET);
					entries.add(ModItems.TEAM_BADGE);
					entries.add(ModItems.RECRUIT_RIFLEMAN);
					entries.add(ModItems.RECRUIT_HEAVY);
					entries.add(ModItems.RECRUIT_MEDIC);
					entries.add(ModItems.RECRUIT_ENGINEER);
					entries.add(ModItems.RECRUIT_PILOT);

					// Waffen
					entries.add(ModItems.LASER_RIFLE);
					entries.add(ModItems.RAILGUN);
					entries.add(ModItems.PLASMA_LAUNCHER);

					// Nachschub
					entries.add(ModItems.JET_FUEL);
					entries.add(ModItems.CANNON_AMMO);
					entries.add(ModItems.AIM_MISSILE);
					entries.add(ModItems.AERIAL_BOMB);
					entries.add(ModItems.INTERCEPTOR_MISSILE);

					// Basisbau
					entries.add(ModBlocks.RUNWAY);
					entries.add(ModBlocks.RUNWAY_CENTERLINE);
					entries.add(ModBlocks.RUNWAY_THRESHOLD);
					entries.add(ModBlocks.RUNWAY_LIGHT);
					entries.add(ModBlocks.HANGAR_DOOR);
					entries.add(ModBlocks.HANGAR_WALL);
					entries.add(ModBlocks.RADAR);
					entries.add(ModBlocks.IRON_DOME);
					entries.add(ModBlocks.REARM_PAD);
					entries.add(ModBlocks.BARRACKS);

					// Werkstoffe
					entries.add(ModBlocks.TITANIUM_ORE);
					entries.add(ModBlocks.DEEPSLATE_TITANIUM_ORE);
					entries.add(ModBlocks.TITANIUM_BLOCK);
					entries.add(ModItems.RAW_TITANIUM);
					entries.add(ModItems.TITANIUM_INGOT);
					entries.add(ModItems.TITANIUM_PLATE);
					entries.add(ModItems.COMPOSITE_PLATE);
					entries.add(ModItems.AVIONICS_MODULE);
					entries.add(ModItems.JET_ENGINE);
					entries.add(ModItems.STEALTH_COATING);
					entries.add(ModItems.ENERGY_CELL);

					// Uebung
					entries.add(ModItems.THREAT_BEACON);
				})
				.build());
	}
}
