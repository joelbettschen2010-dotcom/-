package ch.joel.westernmod.item;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;

import java.util.List;

/**
 * Lasso: zähmt Pferde und Esel auf einen Wurf und nimmt jedes andere Tier an die Leine.
 */
public class LassoItem extends Item {

	public LassoItem(Settings settings) {
		super(settings);
	}

	@Override
	public ActionResult useOnEntity(ItemStack stack, PlayerEntity user, LivingEntity entity, Hand hand) {
		if (user.getWorld().isClient) {
			return ActionResult.SUCCESS;
		}
		if (!(entity instanceof MobEntity mob)) {
			return ActionResult.PASS;
		}

		if (mob instanceof AbstractHorseEntity horse && !horse.isTame()) {
			horse.setTame(true);
			horse.setOwnerUuid(user.getUuid());
			horse.setPersistent();
			((ServerWorld) user.getWorld()).spawnParticles(ParticleTypes.HEART,
					horse.getX(), horse.getBodyY(1.0), horse.getZ(), 7, 0.5, 0.5, 0.5, 0.0);
			user.getWorld().playSound(null, horse.getBlockPos(), SoundEvents.ENTITY_HORSE_SADDLE,
					SoundCategory.NEUTRAL, 0.9f, 1.0f);
			user.sendMessage(Text.translatable("message.westernmod.lasso.tamed", mob.getName())
					.formatted(Formatting.GREEN), true);
		} else {
			mob.attachLeash(user, true);
			user.getWorld().playSound(null, mob.getBlockPos(), SoundEvents.ENTITY_LEASH_KNOT_PLACE,
					SoundCategory.NEUTRAL, 0.9f, 1.2f);
		}

		stack.damage(1, user, hand == Hand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
		return ActionResult.CONSUME;
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("tooltip.westernmod.lasso").formatted(Formatting.GRAY));
	}
}
