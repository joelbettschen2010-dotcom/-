package ch.joel.westernmod;

import ch.joel.westernmod.item.DynamiteItem;
import ch.joel.westernmod.item.GunItem;
import ch.joel.westernmod.item.GunProfile;
import ch.joel.westernmod.item.LassoItem;
import ch.joel.westernmod.item.SheriffStarItem;
import ch.joel.westernmod.item.TownDeedItem;
import ch.joel.westernmod.item.WagonItem;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Rarity;

public final class ModItems {

	/** Patronen — Munition für alle Schusswaffen. */
	public static final Item AMMO = register("ammo", new Item(new Item.Settings()));

	/** Golddollar — Beute der Banditen und Währung des Westens. */
	public static final Item GOLD_COIN = register("gold_coin", new Item(new Item.Settings()));

	public static final Item REVOLVER = register("revolver",
			new GunItem(new Item.Settings().maxCount(1).maxDamage(384), GunProfile.REVOLVER));

	public static final Item WINCHESTER = register("winchester",
			new GunItem(new Item.Settings().maxCount(1).maxDamage(512), GunProfile.WINCHESTER));

	public static final Item SHOTGUN = register("shotgun",
			new GunItem(new Item.Settings().maxCount(1).maxDamage(320), GunProfile.SHOTGUN));

	public static final Item DYNAMITE = register("dynamite", new DynamiteItem(new Item.Settings().maxCount(16)));

	/** Siedlungsurkunde — baut eine komplette Westernstadt. */
	public static final Item TOWN_DEED = register("town_deed",
			new TownDeedItem(new Item.Settings().maxCount(1).rarity(Rarity.RARE)));

	/** Sheriffstern — ruft eine Fahndung aus und hetzt das Gesetz auf die Banditen. */
	public static final Item SHERIFF_STAR = register("sheriff_star",
			new SheriffStarItem(new Item.Settings().maxCount(1).rarity(Rarity.UNCOMMON)));

	/** Lasso — zähmt Pferde und zieht Tiere hinter sich her. */
	public static final Item LASSO = register("lasso", new LassoItem(new Item.Settings().maxCount(1).maxDamage(128)));

	/** Planwagen zum Aufstellen. */
	public static final Item WAGON = register("wagon", new WagonItem(new Item.Settings().maxCount(1)));

	/** Whiskey — wärmt durch und macht mutig, aber auch benommen. */
	public static final Item WHISKEY = register("whiskey", new Item(new Item.Settings()
			.maxCount(16)
			.food(new FoodComponent.Builder()
					.nutrition(1)
					.saturationModifier(0.1f)
					.alwaysEdible()
					.statusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 900, 0), 1.0f)
					.statusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 600, 0), 1.0f)
					.statusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 200, 0), 0.6f)
					.build())));

	/** Bohnen mit Speck — das Lagerfeueressen der Cowboys. */
	public static final Item BEANS = register("beans", new Item(new Item.Settings()
			.maxCount(32)
			.food(new FoodComponent.Builder()
					.nutrition(7)
					.saturationModifier(0.7f)
					.build())));

	private ModItems() {
	}

	private static Item register(String name, Item item) {
		return Registry.register(Registries.ITEM, WesternMod.id(name), item);
	}

	/** Löst die statische Initialisierung aus. */
	public static void register() {
	}
}
