package ch.joel.westernmod.item;

import ch.joel.westernmod.entity.BanditEntity;
import ch.joel.westernmod.entity.BanditLeaderEntity;
import ch.joel.westernmod.entity.GunslingerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

import java.util.List;

/**
 * Sheriffstern: ruft eine Fahndung aus. Alle Banditen im Umkreis leuchten auf und jeder
 * Gesetzeshüter in der Nähe nimmt sofort die Verfolgung auf.
 */
public class SheriffStarItem extends Item {
	private static final double RADIUS = 48.0;

	public SheriffStarItem(Settings settings) {
		super(settings);
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (user.getItemCooldownManager().isCoolingDown(this)) {
			return TypedActionResult.pass(stack);
		}

		if (!world.isClient) {
			Box area = user.getBoundingBox().expand(RADIUS);
			List<MobEntity> outlaws = world.getEntitiesByClass(MobEntity.class, area,
					entity -> entity instanceof BanditEntity || entity instanceof BanditLeaderEntity);

			for (MobEntity outlaw : outlaws) {
				outlaw.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 600, 0, false, false));
			}

			List<GunslingerEntity> lawmen = world.getEntitiesByClass(GunslingerEntity.class, area,
					GunslingerEntity::isLawman);
			for (GunslingerEntity lawman : lawmen) {
				LivingEntity nearest = nearestTo(lawman, outlaws);
				if (nearest != null) {
					lawman.setTarget(nearest);
				}
			}

			user.sendMessage(Text.translatable("message.westernmod.manhunt", outlaws.size(), lawmen.size())
					.formatted(Formatting.GOLD), false);
		}

		// `user` ausgenommen — sein Client spielt den Ton selbst, sonst klingt es doppelt.
		world.playSound(user, user.getX(), user.getY(), user.getZ(), SoundEvents.BLOCK_BELL_USE,
				SoundCategory.PLAYERS, 1.0f, 1.3f);
		user.getItemCooldownManager().set(this, 200);
		return TypedActionResult.success(stack, world.isClient());
	}

	private static LivingEntity nearestTo(Entity origin, List<MobEntity> candidates) {
		LivingEntity best = null;
		double bestDistance = Double.MAX_VALUE;
		for (MobEntity candidate : candidates) {
			double distance = candidate.squaredDistanceTo(origin);
			if (distance < bestDistance) {
				bestDistance = distance;
				best = candidate;
			}
		}
		return best;
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("tooltip.westernmod.sheriff_star").formatted(Formatting.GRAY));
	}
}
