package com.glamardor.micindicator;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Draws a microphone indicator above the head of every player who is currently
 * transmitting voice through Simple Voice Chat. Unlike Simple Voice Chat's own
 * icon (which is attached to the name tag and therefore hidden when name tags
 * are hidden), this indicator is rendered independently during world rendering,
 * so it stays visible on servers that hide player names.
 */
public class MicIndicatorClient implements ClientModInitializer {

	// Reference Simple Voice Chat's own icon textures at runtime (loaded from the
	// installed SVC mod – we don't bundle them). Matches SVC's name-tag indicator:
	// the local player shows a microphone, other players show a speaker.
	private static final Identifier ICON_SELF = Identifier.of("voicechat", "textures/icons/microphone.png");
	private static final Identifier ICON_SELF_WHISPER = Identifier.of("voicechat", "textures/icons/microphone_whisper.png");
	private static final Identifier ICON_OTHER = Identifier.of("voicechat", "textures/icons/speaker.png");
	private static final Identifier ICON_OTHER_WHISPER = Identifier.of("voicechat", "textures/icons/speaker_whisper.png");

	/** Our own little crown badge, drawn above the icon for the mod's creator. */
	private static final Identifier CROWN_TEXTURE = Identifier.of(VoicechatAddonPlugin.PLUGIN_ID, "textures/crown.png");

	/** Only this player (the mod's creator) gets the crown. */
	private static final String CREATOR_NAME = "Glam_Ardor";

	/** Diagnostic switch: draw above every player regardless of talking state. */
	private static final boolean DEBUG_FORCE_VISIBLE = false;

	/** Packed lightmap coordinate for full brightness (block 15, sky 15). */
	private static final int FULL_BRIGHT = 0xF000F0;

	/** RGB tint (the white SVC icon is multiplied by this). */
	private static final int COLOR_NORMAL = 0xFFFFFF;

	/** Base scale: 1 "text pixel" = this many blocks. */
	private static final float ICON_SCALE = 0.025F;

	/** How much the icon grows at full loudness (0.35 = up to +35%). */
	private static final float PULSE_AMOUNT = 0.35F;

	// Per-player animation state: [0] = fade level 0–1, [1] = smoothed loudness 0–1.
	private final Map<UUID, float[]> anim = new HashMap<>();
	private long lastFrameNanos = 0L;

	@Override
	public void onInitializeClient() {
		ModConfig.get(); // load (and write defaults on first run)

		// Render in LAST: fires every frame at the very end of world rendering,
		// AFTER block entities, translucent terrain AND clouds/weather, so none of
		// those can occlude the icon. The matrix stack is null this late, so we
		// rebuild it from the render's position matrix, and the vertex consumers
		// are null too, so we fall back to the client's entity buffer and flush it.
		WorldRenderEvents.LAST.register(this::renderIndicators);
	}

	private void renderIndicators(WorldRenderContext context) {
		ModConfig cfg = ModConfig.get();
		if (!cfg.enabled) {
			anim.clear();
			return;
		}

		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null || client.player == null) {
			return;
		}

		// The matrix stack is non-null from AFTER_ENTITIES onward (LAST included) and
		// holds the world matrix (camera at the origin) – the convention our
		// translate/billboard maths rely on. (Rebuilding it from positionMatrix()
		// instead put the icon in the wrong space – it appeared stuck in the sky.)
		MatrixStack matrices = context.matrixStack();
		if (matrices == null) {
			return;
		}

		VertexConsumerProvider consumers = context.consumers();
		if (consumers == null) {
			consumers = client.getBufferBuilders().getEntityVertexConsumers();
		}
		if (consumers == null) {
			return;
		}

		// Configurable values, resolved once per frame.
		float iconHalfBase = cfg.size / 2.0F;
		int baseAlpha = Math.max(0, Math.min(255, cfg.opacity * 255 / 100));
		double heightOffset = cfg.height / 100.0D;
		double configDist = cfg.distance;
		boolean seeThrough = cfg.throughWalls;
		boolean doFade = cfg.fade;
		boolean doPulse = cfg.pulse;
		boolean showSelf = cfg.showSelf;

		// Frame delta for time-based animation.
		long now = System.nanoTime();
		float dt = lastFrameNanos == 0L ? 0f : (now - lastFrameNanos) / 1_000_000_000f;
		lastFrameNanos = now;
		dt = Math.max(0f, Math.min(0.1f, dt));
		float fadeInFactor = 1f - (float) Math.exp(-dt * 14f);
		float fadeOutFactor = 1f - (float) Math.exp(-dt * 9f);
		float volFactor = 1f - (float) Math.exp(-dt * 16f);

		Camera camera = context.camera();
		Vec3d cameraPos = camera.getPos();
		float tickDelta = context.tickCounter().getTickProgress(false);
		Vec3d selfPos = client.player.getLerpedPos(tickDelta);
		boolean firstPerson = client.options.getPerspective().isFirstPerson();

		Set<UUID> seen = new HashSet<>();
		int drawn = 0;

		for (PlayerEntity player : client.world.getPlayers()) {
			boolean self = player == client.player;

			// Our own icon: optional, and never in first person (it would be in our face).
			if (self && (!showSelf || firstPerson)) {
				continue;
			}
			if (player.isInvisibleTo(client.player)) {
				continue;
			}

			UUID id = player.getUuid();
			seen.add(id);

			boolean talking = DEBUG_FORCE_VISIBLE || VoicechatAddonPlugin.isTalking(id, self);

			float[] state = anim.get(id);
			if (state == null) {
				state = new float[]{0f, 0f};
				anim.put(id, state);
			}

			// Fade towards 1 while talking, 0 otherwise.
			float targetFade = talking ? 1f : 0f;
			if (doFade) {
				state[0] += (targetFade - state[0]) * (talking ? fadeInFactor : fadeOutFactor);
			} else {
				state[0] = targetFade;
			}
			// Smooth the loudness for the pulse.
			float targetVol = (talking && doPulse) ? VoicechatAddonPlugin.getVolume(id, self) : 0f;
			state[1] += (targetVol - state[1]) * volFactor;

			if (state[0] <= 0.01f && !talking) {
				anim.remove(id);
				continue;
			}

			Vec3d pos = player.getLerpedPos(tickDelta);
			// Cull by distance from the observing player, not the camera (which is
			// offset behind the player in third person, making it feel too short).
			// For other players also cap at the distance their sound can actually be
			// heard (whispering reports a small range), so the icon matches hearing.
			double maxDist = configDist;
			if (!self) {
				Float audible = VoicechatAddonPlugin.getAudibleDistance(id);
				if (audible != null && audible > 0f) {
					maxDist = Math.min(maxDist, (double) audible);
				}
			}
			double pdx = pos.x - selfPos.x;
			double pdy = pos.y - selfPos.y;
			double pdz = pos.z - selfPos.z;
			if (pdx * pdx + pdy * pdy + pdz * pdz > maxDist * maxDist) {
				continue;
			}
			double dx = pos.x - cameraPos.x;
			double dz = pos.z - cameraPos.z;

			int alpha = doFade ? Math.round(baseAlpha * state[0]) : baseAlpha;
			if (alpha <= 0) {
				continue;
			}
			float sizeMul = doPulse ? (1f + state[1] * PULSE_AMOUNT) : 1f;
			float iconHalf = iconHalfBase * sizeMul;
			float crownHalf = iconHalfBase * 0.8F;

			boolean whispering = VoicechatAddonPlugin.isWhispering(id, self);
			Identifier texture = self
					? (whispering ? ICON_SELF_WHISPER : ICON_SELF)
					: (whispering ? ICON_OTHER_WHISPER : ICON_OTHER);
			double iconY = pos.y + player.getHeight() + heightOffset;

			// Mirror the speaker icon (other players) so it faces the right way.
			drawQuad(matrices, consumers, camera,
					dx, iconY - cameraPos.y, dz, texture, COLOR_NORMAL, alpha, iconHalf, seeThrough, !self);

			// A little crown badge sitting just on top of the icon (creator only).
			// Crown alpha is proportional to the icon's alpha so they fade out
			// together (a flat offset made the crown linger after the mic vanished).
			if (isCreator(player)) {
				int crownAlpha = Math.min(255, Math.round(alpha * 1.4F));
				double crownY = iconY + (iconHalf + crownHalf) * ICON_SCALE;
				drawQuad(matrices, consumers, camera,
						dx, crownY - cameraPos.y, dz, CROWN_TEXTURE, COLOR_NORMAL, crownAlpha, crownHalf, seeThrough, false);
			}
			drawn++;
		}

		// Forget players that left the world.
		anim.keySet().removeIf(k -> !seen.contains(k));

		if (drawn > 0 && consumers instanceof VertexConsumerProvider.Immediate immediate) {
			immediate.draw();
		}
	}

	/** @return whether this player is the mod's creator (gets the crown). */
	private boolean isCreator(PlayerEntity player) {
		String name = player.getGameProfile().getName();
		return name != null && CREATOR_NAME.equalsIgnoreCase(name);
	}

	/**
	 * Renders a single camera-facing (billboarded) textured quad at the given
	 * position (relative to the camera). The transform mirrors vanilla name-tag
	 * rendering: translate, cancel the view rotation, then flip into the
	 * text/GUI coordinate system. {@code mirror} flips the texture horizontally.
	 */
	private void drawQuad(MatrixStack matrices, VertexConsumerProvider consumers, Camera camera,
	                      double x, double y, double z, Identifier texture, int color, int alpha,
	                      float halfSize, boolean seeThrough, boolean mirror) {
		matrices.push();
		matrices.translate(x, y, z);
		matrices.multiply(camera.getRotation());
		matrices.scale(-ICON_SCALE, -ICON_SCALE, ICON_SCALE);

		Matrix4f matrix = matrices.peek().getPositionMatrix();
		// "See through" = no depth test (visible behind blocks); plain text layer
		// respects depth. Either way emit the quad double-sided to avoid back-face
		// culling hiding it.
		RenderLayer layer = seeThrough ? RenderLayer.getTextSeeThrough(texture) : RenderLayer.getText(texture);
		VertexConsumer buffer = consumers.getBuffer(layer);

		int r = (color >> 16) & 0xFF;
		int g = (color >> 8) & 0xFF;
		int b = color & 0xFF;
		int a = alpha;
		float h = halfSize;
		float uL = mirror ? 1.0F : 0.0F; // u for the left edge
		float uR = mirror ? 0.0F : 1.0F; // u for the right edge

		// Front face (format POSITION_COLOR_TEXTURE_LIGHT).
		buffer.vertex(matrix, -h, -h, 0.0F).color(r, g, b, a).texture(uL, 0.0F).light(FULL_BRIGHT);
		buffer.vertex(matrix, -h, h, 0.0F).color(r, g, b, a).texture(uL, 1.0F).light(FULL_BRIGHT);
		buffer.vertex(matrix, h, h, 0.0F).color(r, g, b, a).texture(uR, 1.0F).light(FULL_BRIGHT);
		buffer.vertex(matrix, h, -h, 0.0F).color(r, g, b, a).texture(uR, 0.0F).light(FULL_BRIGHT);
		// Back face (reverse winding, same UVs).
		buffer.vertex(matrix, h, -h, 0.0F).color(r, g, b, a).texture(uR, 0.0F).light(FULL_BRIGHT);
		buffer.vertex(matrix, h, h, 0.0F).color(r, g, b, a).texture(uR, 1.0F).light(FULL_BRIGHT);
		buffer.vertex(matrix, -h, h, 0.0F).color(r, g, b, a).texture(uL, 1.0F).light(FULL_BRIGHT);
		buffer.vertex(matrix, -h, -h, 0.0F).color(r, g, b, a).texture(uL, 0.0F).light(FULL_BRIGHT);

		matrices.pop();
	}
}
