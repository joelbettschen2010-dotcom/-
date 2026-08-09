package ch.joel.westernmod;

import net.fabricmc.api.ModInitializer;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Wild West — bringt den Wilden Westen nach Minecraft.
 *
 * <p>Kernstücke: die Siedlungsurkunde ({@link ch.joel.westernmod.item.TownDeedItem}) baut eine
 * komplette Westernstadt, Revolvermänner ({@link ch.joel.westernmod.entity.GunslingerEntity})
 * tragen Schießereien zwischen Gesetz und Banditen aus, und der Planwagen
 * ({@link ch.joel.westernmod.entity.WagonEntity}) dient als Fahrzeug.
 */
public class WesternMod implements ModInitializer {
	public static final String MOD_ID = "westernmod";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static Identifier id(String path) {
		return Identifier.of(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		ModBlocks.register();
		ModItems.register();
		ModEntities.register();
		ModItemGroups.register();
		ModWorld.register();
		ModCommands.register();

		LOGGER.info("[Wild West] Saddle up — the frontier is open for business.");
	}
}
