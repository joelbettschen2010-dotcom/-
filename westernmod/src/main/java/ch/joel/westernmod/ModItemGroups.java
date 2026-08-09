package ch.joel.westernmod;

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;

public final class ModItemGroups {

	public static final ItemGroup WESTERN = Registry.register(Registries.ITEM_GROUP, WesternMod.id("western"),
			FabricItemGroup.builder()
					.icon(() -> new ItemStack(ModItems.REVOLVER))
					.displayName(Text.translatable("itemgroup.westernmod.western"))
					.entries((context, entries) -> {
						entries.add(ModItems.TOWN_DEED);
						entries.add(ModItems.WAGON);

						entries.add(ModItems.REVOLVER);
						entries.add(ModItems.WINCHESTER);
						entries.add(ModItems.SHOTGUN);
						entries.add(ModItems.AMMO);
						entries.add(ModItems.DYNAMITE);

						entries.add(ModItems.SHERIFF_STAR);
						entries.add(ModItems.LASSO);
						entries.add(ModItems.GOLD_COIN);

						entries.add(ModItems.WHISKEY);
						entries.add(ModItems.BEANS);

						entries.add(ModBlocks.HITCHING_POST);
						entries.add(ModBlocks.SALOON_DOOR);
						entries.add(ModBlocks.WANTED_POSTER);
					})
					.build());

	private ModItemGroups() {
	}

	/** Löst die statische Initialisierung aus. */
	public static void register() {
	}
}
