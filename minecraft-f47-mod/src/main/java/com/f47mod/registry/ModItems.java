package com.f47mod.registry;

import com.f47mod.F47Mod;
import com.f47mod.entity.mob.SoldierRole;
import com.f47mod.item.BaseBuilderItem;
import com.f47mod.item.CommandTabletItem;
import com.f47mod.item.EnergyWeaponItem;
import com.f47mod.item.JetSpawnItem;
import com.f47mod.item.VehicleSpawnItem;
import com.f47mod.item.RecruitOrderItem;
import com.f47mod.item.TeamBadgeItem;
import com.f47mod.item.ThreatBeaconItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

/** Alle Gegenstaende des Mods. */
public final class ModItems {
	private ModItems() {
	}

	// --- Werkstoffe -------------------------------------------------------
	public static final Item RAW_TITANIUM = register("raw_titanium", new Item(new Item.Settings()));
	public static final Item TITANIUM_INGOT = register("titanium_ingot", new Item(new Item.Settings()));
	public static final Item TITANIUM_PLATE = register("titanium_plate", new Item(new Item.Settings()));
	public static final Item COMPOSITE_PLATE = register("composite_plate", new Item(new Item.Settings()));
	public static final Item AVIONICS_MODULE = register("avionics_module", new Item(new Item.Settings()));
	public static final Item JET_ENGINE = register("jet_engine", new Item(new Item.Settings()));
	public static final Item STEALTH_COATING = register("stealth_coating", new Item(new Item.Settings()));
	public static final Item ENERGY_CELL = register("energy_cell", new Item(new Item.Settings()));

	// --- Verbrauchsgueter -------------------------------------------------
	public static final Item JET_FUEL = register("jet_fuel", new Item(new Item.Settings().maxCount(16)));
	public static final Item CANNON_AMMO = register("cannon_ammo", new Item(new Item.Settings()));
	public static final Item AIM_MISSILE = register("aim_missile", new Item(new Item.Settings().maxCount(16)));
	public static final Item AERIAL_BOMB = register("aerial_bomb", new Item(new Item.Settings().maxCount(16)));
	public static final Item INTERCEPTOR_MISSILE =
			register("interceptor_missile", new Item(new Item.Settings().maxCount(32)));

	// --- Flugzeuge --------------------------------------------------------
	public static final Item F47_AIRCRAFT =
			register("f47_aircraft", new JetSpawnItem(new Item.Settings().maxCount(1), false));
	public static final Item F47_AUTONOMOUS =
			register("f47_autonomous", new JetSpawnItem(new Item.Settings().maxCount(1), true));

	public static final Item B21_BOMBER =
			register("b21_bomber", new VehicleSpawnItem(new Item.Settings().maxCount(1),
					() -> ModEntities.B21));
	public static final Item TRANSPORT_AIRCRAFT =
			register("transport_aircraft", new VehicleSpawnItem(new Item.Settings().maxCount(1),
					() -> ModEntities.TRANSPORT));
	public static final Item ARMORED_VEHICLE =
			register("armored_vehicle", new VehicleSpawnItem(new Item.Settings().maxCount(1),
					() -> ModEntities.ARMORED_VEHICLE));

	// --- Energiewaffen ----------------------------------------------------
	public static final Item LASER_RIFLE = register("laser_rifle",
			new EnergyWeaponItem(new Item.Settings().maxCount(1).maxDamage(120), EnergyWeaponItem.Mode.LASER));
	public static final Item RAILGUN = register("railgun",
			new EnergyWeaponItem(new Item.Settings().maxCount(1).maxDamage(90), EnergyWeaponItem.Mode.RAILGUN));
	public static final Item PLASMA_LAUNCHER = register("plasma_launcher",
			new EnergyWeaponItem(new Item.Settings().maxCount(1).maxDamage(100), EnergyWeaponItem.Mode.PLASMA));

	// --- Fuehrung ---------------------------------------------------------
	public static final Item COMMAND_TABLET =
			register("command_tablet", new CommandTabletItem(new Item.Settings().maxCount(1)));
	public static final Item BASE_BUILDER =
			register("base_builder", new BaseBuilderItem(new Item.Settings().maxCount(4)));
	public static final Item TEAM_BADGE =
			register("team_badge", new TeamBadgeItem(new Item.Settings().maxCount(1)));
	public static final Item THREAT_BEACON =
			register("threat_beacon", new ThreatBeaconItem(new Item.Settings().maxCount(8)));

	// --- Marschbefehle ----------------------------------------------------
	public static final Item RECRUIT_RIFLEMAN =
			register("recruit_rifleman", new RecruitOrderItem(new Item.Settings(), SoldierRole.RIFLEMAN));
	public static final Item RECRUIT_HEAVY =
			register("recruit_heavy", new RecruitOrderItem(new Item.Settings(), SoldierRole.HEAVY));
	public static final Item RECRUIT_MEDIC =
			register("recruit_medic", new RecruitOrderItem(new Item.Settings(), SoldierRole.MEDIC));
	public static final Item RECRUIT_ENGINEER =
			register("recruit_engineer", new RecruitOrderItem(new Item.Settings(), SoldierRole.ENGINEER));
	public static final Item RECRUIT_PILOT =
			register("recruit_pilot", new RecruitOrderItem(new Item.Settings(), SoldierRole.PILOT));

	private static Item register(String name, Item item) {
		return Registry.register(Registries.ITEM, F47Mod.id(name), item);
	}

	public static void init() {
	}
}
