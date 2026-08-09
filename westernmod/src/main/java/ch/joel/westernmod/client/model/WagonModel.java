package ch.joel.westernmod.client.model;

import ch.joel.westernmod.entity.WagonEntity;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.entity.model.SinglePartEntityModel;
import net.minecraft.util.math.MathHelper;

/**
 * Pferdegespann mit Planwagen.
 *
 * <p>Alles steht in Modellkoordinaten mit der Wurzel auf Bodenhöhe: negatives {@code y} zeigt
 * nach oben, negatives {@code z} nach vorne. Vorne läuft das Pferd, dahinter hängt an der
 * Deichsel der Wagen mit Kutschbock, Ladefläche und Plane. Die grossen Hinterräder und die
 * kleineren Vorderräder drehen sich mit der Fahrstrecke, die Pferdebeine laufen im Takt dazu.
 */
public class WagonModel extends SinglePartEntityModel<WagonEntity> {
	private final ModelPart root;
	private final ModelPart legFrontLeft;
	private final ModelPart legFrontRight;
	private final ModelPart legBackLeft;
	private final ModelPart legBackRight;
	private final ModelPart wheelFrontLeft;
	private final ModelPart wheelFrontRight;
	private final ModelPart wheelBackLeft;
	private final ModelPart wheelBackRight;

	public WagonModel(ModelPart root) {
		this.root = root;
		ModelPart horse = root.getChild("horse");
		this.legFrontLeft = horse.getChild("leg_front_left");
		this.legFrontRight = horse.getChild("leg_front_right");
		this.legBackLeft = horse.getChild("leg_back_left");
		this.legBackRight = horse.getChild("leg_back_right");

		ModelPart wagon = root.getChild("wagon");
		this.wheelFrontLeft = wagon.getChild("wheel_front_left");
		this.wheelFrontRight = wagon.getChild("wheel_front_right");
		this.wheelBackLeft = wagon.getChild("wheel_back_left");
		this.wheelBackRight = wagon.getChild("wheel_back_right");
	}

	public static TexturedModelData getTexturedModelData() {
		ModelData modelData = new ModelData();
		ModelPartData root = modelData.getRoot();

		// --- Zugpferd ---
		ModelPartData horse = root.addChild("horse", ModelPartBuilder.create(), ModelTransform.pivot(0.0f, 24.0f, 0.0f));
		horse.addChild("horse_body",
				ModelPartBuilder.create().uv(0, 0).cuboid(-4.0f, -22.0f, -16.0f, 8.0f, 8.0f, 13.0f),
				ModelTransform.NONE);
		horse.addChild("horse_neck",
				ModelPartBuilder.create().uv(44, 0).cuboid(-2.5f, -28.0f, -18.0f, 5.0f, 8.0f, 5.0f),
				ModelTransform.NONE);
		horse.addChild("horse_head",
				ModelPartBuilder.create().uv(64, 0).cuboid(-2.5f, -30.0f, -23.0f, 5.0f, 5.0f, 7.0f),
				ModelTransform.NONE);
		horse.addChild("horse_tail",
				ModelPartBuilder.create().uv(100, 0).cuboid(-1.0f, -22.0f, -3.0f, 2.0f, 6.0f, 2.0f),
				ModelTransform.NONE);

		ModelPartBuilder leg = ModelPartBuilder.create().uv(88, 0).cuboid(-1.5f, 0.0f, -1.5f, 3.0f, 14.0f, 3.0f);
		horse.addChild("leg_front_left", leg, ModelTransform.pivot(-2.5f, -14.0f, -14.0f));
		horse.addChild("leg_front_right", leg, ModelTransform.pivot(2.5f, -14.0f, -14.0f));
		horse.addChild("leg_back_left", leg, ModelTransform.pivot(-2.5f, -14.0f, -5.0f));
		horse.addChild("leg_back_right", leg, ModelTransform.pivot(2.5f, -14.0f, -5.0f));

		// --- Wagen ---
		ModelPartData wagon = root.addChild("wagon", ModelPartBuilder.create(), ModelTransform.pivot(0.0f, 24.0f, 0.0f));
		wagon.addChild("shaft",
				ModelPartBuilder.create().uv(36, 110).cuboid(-1.0f, -13.0f, -16.0f, 2.0f, 2.0f, 14.0f),
				ModelTransform.NONE);
		wagon.addChild("bed",
				ModelPartBuilder.create().uv(0, 24).cuboid(-8.0f, -14.0f, -2.0f, 16.0f, 3.0f, 24.0f),
				ModelTransform.NONE);
		wagon.addChild("side_left",
				ModelPartBuilder.create().uv(0, 52).cuboid(-8.0f, -20.0f, -2.0f, 1.0f, 6.0f, 24.0f),
				ModelTransform.NONE);
		wagon.addChild("side_right",
				ModelPartBuilder.create().uv(0, 52).cuboid(7.0f, -20.0f, -2.0f, 1.0f, 6.0f, 24.0f),
				ModelTransform.NONE);
		wagon.addChild("bench",
				ModelPartBuilder.create().uv(0, 110).cuboid(-7.0f, -20.0f, -3.0f, 14.0f, 2.0f, 3.0f),
				ModelTransform.NONE);
		wagon.addChild("canvas_lower",
				ModelPartBuilder.create().uv(52, 52).cuboid(-8.0f, -28.0f, 0.0f, 16.0f, 8.0f, 20.0f),
				ModelTransform.NONE);
		wagon.addChild("canvas_upper",
				ModelPartBuilder.create().uv(0, 84).cuboid(-6.5f, -33.0f, 0.5f, 13.0f, 5.0f, 19.0f),
				ModelTransform.NONE);

		// Räder als flache Scheiben — die runde Form kommt aus der Textur.
		ModelPartBuilder bigWheel = ModelPartBuilder.create().uv(64, 84)
				.cuboid(-7.0f, -7.0f, -1.0f, 14.0f, 14.0f, 2.0f);
		ModelPartBuilder smallWheel = ModelPartBuilder.create().uv(96, 84)
				.cuboid(-5.0f, -5.0f, -1.0f, 10.0f, 10.0f, 2.0f);

		wagon.addChild("wheel_back_left", bigWheel, ModelTransform.pivot(-8.5f, -7.0f, 16.0f));
		wagon.addChild("wheel_back_right", bigWheel, ModelTransform.pivot(8.5f, -7.0f, 16.0f));
		wagon.addChild("wheel_front_left", smallWheel, ModelTransform.pivot(-8.5f, -5.0f, 3.0f));
		wagon.addChild("wheel_front_right", smallWheel, ModelTransform.pivot(8.5f, -5.0f, 3.0f));

		return TexturedModelData.of(modelData, 128, 128);
	}

	@Override
	public ModelPart getPart() {
		return this.root;
	}

	@Override
	public void setAngles(WagonEntity entity, float limbAngle, float limbDistance, float animationProgress,
			float headYaw, float headPitch) {
		// Die Räder laufen mit der zurückgelegten Strecke mit.
		float spin = limbAngle * 0.5f;
		this.wheelBackLeft.pitch = spin;
		this.wheelBackRight.pitch = spin;
		// Kleinere Vorderräder drehen schneller.
		this.wheelFrontLeft.pitch = spin * 1.4f;
		this.wheelFrontRight.pitch = spin * 1.4f;

		// Beinschlag des Pferds, diagonal versetzt wie bei einem Trab.
		float swing = MathHelper.cos(limbAngle * 0.6662f) * 1.2f * limbDistance;
		this.legFrontLeft.pitch = swing;
		this.legBackRight.pitch = swing;
		this.legFrontRight.pitch = -swing;
		this.legBackLeft.pitch = -swing;
	}
}
