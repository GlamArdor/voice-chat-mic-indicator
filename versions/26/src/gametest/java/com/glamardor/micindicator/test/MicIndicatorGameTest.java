package com.glamardor.micindicator.test;

import com.glamardor.micindicator.ModConfig;
import com.glamardor.micindicator.VoicechatAddonPlugin;
import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.player.RemotePlayer;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;

/** Makes a player "talk" as Simple Voice Chat would report it and photographs the icon over his head. */
public final class MicIndicatorGameTest implements FabricClientGameTest {
	private static final UUID KARA = UUID.fromString("6b1b7a4e-1d1e-4d7a-9a59-2b0f6cbbd001");

	@Override
	public void runTest(ClientGameTestContext ctx) {
		TestLog log = new TestLog("Voice Chat Mic Indicator 26.3");
		log.startup(ctx);
		try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
			sp.getConnection().waitForChunksRender();
			sp.getServer().runCommand("time set noon");
			sp.getServer().runCommand("tp @a 0.5 -60 0.5 0 0");
			// a wall between two of the players, to see the icon through it or not
			sp.getServer().runCommand("fill -3 -60 6 -1 -57 6 minecraft:stone");
			ctx.waitTicks(20);
			ctx.runOnClient(mc -> {
				RemotePlayer kara = new RemotePlayer(mc.level, new GameProfile(KARA, "Kara_Vell"));
				kara.setId(900001);
				kara.snapTo(2.5, -60, 4.5, 180, 0);
				mc.level.addEntity(kara);
				RemotePlayer hidden = new RemotePlayer(mc.level, new GameProfile(UUID.randomUUID(), "Behind_Wall"));
				hidden.setId(900002);
				hidden.snapTo(-2.0, -60, 9.5, 180, 0);
				mc.level.addEntity(hidden);
				hidden.setId(900002);
			});
			ctx.getInput().lookAt(0, 0);
			ctx.waitTicks(5);

			log.check("silent: not talking", !VoicechatAddonPlugin.isTalking(KARA, false));
			UUID hiddenId = ctx.computeOnClient(mc -> mc.level.getEntity(900002).getUUID());
			talk(KARA, false);
			talk(hiddenId, false);
			log.check("talking as reported by the voice events", VoicechatAddonPlugin.isTalking(KARA, false));
			settle(ctx);
			ctx.takeScreenshot("mic-talking");

			talk(KARA, true);
			log.check("whispering", VoicechatAddonPlugin.isWhispering(KARA, false));
			ModConfig.get().throughWalls = true;
			settle(ctx);
			ctx.takeScreenshot("mic-whisper-through-walls");
			ModConfig.get().throughWalls = false;
		} catch (Throwable e) {
			log.fail("crashed: " + e);
			e.printStackTrace();
		}
		log.finish();
	}

	/** Keeps the "talking" mark fresh for a minute, the way a stream of voice packets would. */
	@SuppressWarnings("unchecked")
	private static void talk(UUID id, boolean whisper) throws ReflectiveOperationException {
		long until = System.currentTimeMillis() + 60_000;
		field("talkingUntil", Map.class).put(id, until);
		field("volume", Map.class).put(id, 0.6f);
		if (whisper) {
			field("whisperUntil", Map.class).put(id, until);
		}
	}

	@SuppressWarnings("unchecked")
	private static <T> T field(String name, Class<T> type) throws ReflectiveOperationException {
		Field f = VoicechatAddonPlugin.class.getDeclaredField(name);
		f.setAccessible(true);
		return (T) f.get(null);
	}

	/** The fade-in runs on the wall clock, and game tests tick much faster than that. */
	private static void settle(ClientGameTestContext ctx) {
		long until = System.currentTimeMillis() + 1000;
		ctx.waitFor(mc -> System.currentTimeMillis() > until, 100000);
	}
}
