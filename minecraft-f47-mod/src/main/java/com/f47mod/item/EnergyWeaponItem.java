package com.f47mod.item;

import com.f47mod.F47Config;
import com.f47mod.entity.projectile.PlasmaBoltEntity;
import com.f47mod.entity.vehicle.F47Entity;
import com.f47mod.registry.ModDamage;
import com.f47mod.util.Iff;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Energiewaffen der Zukunft. Eine Klasse deckt drei Bauarten ab:
 *
 * <ul>
 *   <li>LASER - sofortiger Strahl, trifft ohne Vorhalt, geringer Rueckstoss</li>
 *   <li>RAILGUN - muss aufgeladen werden, durchschlaegt mehrere Ziele</li>
 *   <li>PLASMA - verschiesst langsame, explodierende Plasmakugeln</li>
 * </ul>
 *
 * <p>Statt Munition haben sie eine Energiezelle: Die Haltbarkeitsanzeige ist
 * der Ladestand, der sich im Inventar von selbst wieder auflaedt.
 */
public class EnergyWeaponItem extends Item {

	public enum Mode {
		LASER(1, 8, 90.0, SoundEvents.BLOCK_CONDUIT_ATTACK_TARGET),
		RAILGUN(6, 30, 140.0, SoundEvents.ITEM_TRIDENT_THUNDER.value()),
		PLASMA(3, 16, 0.0, SoundEvents.ENTITY_BLAZE_SHOOT);

		public final int energyCost;
		public final int cooldownTicks;
		public final double range;
		public final SoundEvent sound;

		Mode(int energyCost, int cooldownTicks, double range, SoundEvent sound) {
			this.energyCost = energyCost;
			this.cooldownTicks = cooldownTicks;
			this.range = range;
			this.sound = sound;
		}
	}

	private final Mode mode;

	public EnergyWeaponItem(Settings settings, Mode mode) {
		super(settings);
		this.mode = mode;
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
		ItemStack stack = player.getStackInHand(hand);

		if (mode == Mode.RAILGUN) {
			// Railgun laedt sich beim Halten der rechten Maustaste auf.
			player.setCurrentHand(hand);
			return TypedActionResult.consume(stack);
		}
		if (!hasEnergy(stack, mode.energyCost)) {
			playEmptySound(world, player);
			return TypedActionResult.fail(stack);
		}

		fire(world, player, stack, 1.0f);
		player.getItemCooldownManager().set(this, mode.cooldownTicks);
		return TypedActionResult.consume(stack);
	}

	@Override
	public UseAction getUseAction(ItemStack stack) {
		return mode == Mode.RAILGUN ? UseAction.BOW : UseAction.NONE;
	}

	@Override
	public int getMaxUseTime(ItemStack stack, LivingEntity user) {
		return 72000;
	}

	@Override
	public void onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
		if (!(user instanceof PlayerEntity player)) {
			return;
		}
		int used = getMaxUseTime(stack, user) - remainingUseTicks;
		float charge = Math.min(1.0f, used / 30.0f);
		if (charge < 0.35f) {
			return;
		}
		if (!hasEnergy(stack, mode.energyCost)) {
			playEmptySound(world, player);
			return;
		}
		fire(world, player, stack, charge);
		player.getItemCooldownManager().set(this, mode.cooldownTicks);
	}

	/** Schuss ausloesen. Auf dem Client wird nur der Ton abgespielt. */
	private void fire(World world, PlayerEntity player, ItemStack stack, float charge) {
		world.playSound(null, player.getX(), player.getY(), player.getZ(),
				mode.sound, SoundCategory.PLAYERS, 1.0f, mode == Mode.RAILGUN ? 0.8f : 1.4f);
		if (world.isClient) {
			return;
		}

		consumeEnergy(stack, player, mode.energyCost);
		Vec3d start = player.getEyePos();
		Vec3d direction = player.getRotationVec(1.0f);

		switch (mode) {
			case LASER -> fireHitscan(world, player, start, direction, F47Config.get().laserDamage, false);
			case RAILGUN -> fireHitscan(world, player, start, direction,
					F47Config.get().railgunDamage * charge, true);
			case PLASMA -> {
				PlasmaBoltEntity bolt = new PlasmaBoltEntity(world, player);
					bolt.setTeam(com.f47mod.util.TeamState.teamOf(player));
				bolt.setPosition(start.x, start.y - 0.15, start.z);
				bolt.setVelocity(direction.x, direction.y, direction.z, 1.6f, 1.0f);
				world.spawnEntity(bolt);
			}
		}
	}

	/** Strahlwaffe: trifft sofort entlang der Blickrichtung. */
	private void fireHitscan(World world, PlayerEntity player, Vec3d start, Vec3d direction,
			double damage, boolean piercing) {
		Vec3d end = start.add(direction.multiply(mode.range));
		// Der Strahl endet an der ersten Wand.
		var blockHit = world.raycast(new net.minecraft.world.RaycastContext(start, end,
				net.minecraft.world.RaycastContext.ShapeType.COLLIDER,
				net.minecraft.world.RaycastContext.FluidHandling.NONE, player));
		if (blockHit.getType() != net.minecraft.util.hit.HitResult.Type.MISS) {
			end = blockHit.getPos();
		}

		List<EntityHitResult> hits = raycastEntities(world, player, start, end);
		int pierced = 0;
		for (EntityHitResult hit : hits) {
			Entity target = hit.getEntity();
			target.damage(ModDamage.laser(world, player), (float) damage);
			target.setOnFireFor(mode == Mode.LASER ? 3 : 1);
			if (!piercing) {
				end = hit.getPos();
				break;
			}
			if (++pierced >= 4) {
				end = hit.getPos();
				break;
			}
		}

		if (world instanceof ServerWorld server) {
			F47Entity.drawBeam(server, start.subtract(0, 0.15, 0), end);
			if (piercing) {
				server.spawnParticles(ParticleTypes.ELECTRIC_SPARK, end.x, end.y, end.z, 12, 0.2, 0.2, 0.2, 0.1);
			}
		}
	}

	/** Alle Treffer entlang des Strahls, sortiert nach Entfernung. */
	private List<EntityHitResult> raycastEntities(World world, PlayerEntity player, Vec3d start, Vec3d end) {
		Box box = new Box(start, end).expand(1.0);
		return world.getOtherEntities(player, box, entity -> entity.canHit() && Iff.isEnemy(player, entity))
				.stream()
				.map(entity -> entity.getBoundingBox().expand(0.35).raycast(start, end)
						.map(pos -> new EntityHitResult(entity, pos)).orElse(null))
				.filter(Objects::nonNull)
				.sorted(Comparator.comparingDouble(hit -> hit.getPos().squaredDistanceTo(start)))
				.toList();
	}

	// ------------------------------------------------------------------
	// Energiezelle (ueber die Haltbarkeitsleiste dargestellt)
	// ------------------------------------------------------------------

	private boolean hasEnergy(ItemStack stack, int cost) {
		return stack.getMaxDamage() - stack.getDamage() >= cost;
	}

	private void consumeEnergy(ItemStack stack, PlayerEntity player, int cost) {
		if (player.getAbilities().creativeMode) {
			return;
		}
		stack.setDamage(Math.min(stack.getMaxDamage() - 1, stack.getDamage() + cost));
	}

	private void playEmptySound(World world, PlayerEntity player) {
		world.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.BLOCK_LEVER_CLICK, SoundCategory.PLAYERS, 0.7f, 0.6f);
	}

	/** Die Zelle laedt sich langsam von selbst wieder auf. */
	@Override
	public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
		if (world.isClient || stack.getDamage() <= 0) {
			return;
		}
		// Etwa alle zweieinhalb Sekunden ein Ladeschritt.
		if (world.getTime() % 50 == 0) {
			stack.setDamage(Math.max(0, stack.getDamage() - 1));
		}
	}

	@Override
	public boolean isEnchantable(ItemStack stack) {
		return false;
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		int charge = stack.getMaxDamage() - stack.getDamage();
		tooltip.add(Text.translatable("tooltip.f47.energy", charge, stack.getMaxDamage())
				.formatted(Formatting.AQUA));
		tooltip.add(Text.translatable("tooltip.f47.weapon." + mode.name().toLowerCase(java.util.Locale.ROOT))
				.formatted(Formatting.GRAY));
	}
}
