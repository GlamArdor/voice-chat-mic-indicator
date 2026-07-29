package com.glamardor.micindicator;

import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.ClientReceiveSoundEvent;
import de.maxhenkel.voicechat.api.events.ClientSoundEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registers this mod as a Simple Voice Chat plugin and tracks, per player,
 * whether they are currently talking.
 *
 * <p>Simple Voice Chat does not hand a {@code VoicechatClientApi} to plugins via
 * {@link #initialize(VoicechatApi)} (on an integrated server it passes the server
 * api), and "currently talking" is not part of the synced player state – it is
 * derived from incoming audio. So instead we listen to the client sound events
 * (which <em>are</em> delivered to plugins) and mark a player as talking for a
 * short window every time an audio frame for them arrives.</p>
 */
public class VoicechatAddonPlugin implements VoicechatPlugin {

	public static final String PLUGIN_ID = "voicechat_mic_indicator";

	/** How long after the last audio frame a player is still considered talking. */
	private static final long TALK_HOLD_MS = 250L;

	// Other players, keyed by entity UUID. Updated from the audio thread.
	private static final Map<UUID, Long> talkingUntil = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> whisperUntil = new ConcurrentHashMap<>();

	// The local player (its own microphone has no UUID in the event).
	private static volatile long selfTalkingUntil = 0L;
	private static volatile long selfWhisperUntil = 0L;

	// Latest loudness (0–1) per player, for the volume pulse.
	private static final Map<UUID, Float> volume = new ConcurrentHashMap<>();
	private static volatile float selfVolume = 0f;
	private static final float VOLUME_GAIN = 6.0f;

	// Distance (blocks) at which the last sound from a player can actually be heard.
	// Whispering reports a much smaller value than normal speech.
	private static final Map<UUID, Float> audibleDistance = new ConcurrentHashMap<>();

	/** Set to true to print diagnostic log lines when audio is captured/received. */
	private static final boolean DEBUG_LOG = false;
	private static volatile long lastSelfLog = 0L;
	private static volatile long lastOtherLog = 0L;

	@Override
	public String getPluginId() {
		return PLUGIN_ID;
	}

	@Override
	public void registerEvents(EventRegistration registration) {
		registration.registerEvent(ClientReceiveSoundEvent.EntitySound.class, this::onEntitySound);
		registration.registerEvent(ClientSoundEvent.class, this::onClientSound);
	}

	/** Fired when we receive voice audio from another player's entity. */
	private void onEntitySound(ClientReceiveSoundEvent.EntitySound event) {
		short[] audio = event.getRawAudio();
		if (audio == null || audio.length == 0) {
			return; // empty frame = end of transmission
		}
		UUID id = event.getEntityId();
		if (id == null) {
			return;
		}
		long now = System.currentTimeMillis();
		long until = now + TALK_HOLD_MS;
		talkingUntil.put(id, until);
		volume.put(id, loudness(audio));
		audibleDistance.put(id, event.getDistance());
		if (event.isWhispering()) {
			whisperUntil.put(id, until);
		} else {
			whisperUntil.remove(id);
		}
		if (DEBUG_LOG && now - lastOtherLog > 1000L) {
			lastOtherLog = now;
			System.out.println("[MicIndicator] hearing player " + id + " (whisper=" + event.isWhispering() + ")");
		}
	}

	/** Fired right before our own captured microphone audio is sent. */
	private void onClientSound(ClientSoundEvent event) {
		short[] audio = event.getRawAudio();
		if (audio == null || audio.length == 0) {
			return;
		}
		long now = System.currentTimeMillis();
		long until = now + TALK_HOLD_MS;
		selfTalkingUntil = until;
		selfWhisperUntil = event.isWhispering() ? until : 0L;
		selfVolume = loudness(audio);
		if (DEBUG_LOG && now - lastSelfLog > 1000L) {
			lastSelfLog = now;
			System.out.println("[MicIndicator] local mic active (whisper=" + event.isWhispering() + ")");
		}
	}

	public static boolean isTalking(UUID id, boolean self) {
		long now = System.currentTimeMillis();
		if (self) {
			return now < selfTalkingUntil;
		}
		Long until = talkingUntil.get(id);
		return until != null && now < until;
	}

	public static boolean isWhispering(UUID id, boolean self) {
		long now = System.currentTimeMillis();
		if (self) {
			return now < selfWhisperUntil;
		}
		Long until = whisperUntil.get(id);
		return until != null && now < until;
	}

	/** @return distance the player's last sound can be heard, or {@code null} if unknown. */
	public static Float getAudibleDistance(UUID id) {
		return audibleDistance.get(id);
	}

	/** @return latest loudness (0–1) for the volume pulse. */
	public static float getVolume(UUID id, boolean self) {
		if (self) {
			return selfVolume;
		}
		Float v = volume.get(id);
		return v == null ? 0f : v;
	}

	/** RMS amplitude of a frame, normalised and scaled into a 0–1 loudness. */
	private static float loudness(short[] audio) {
		double sum = 0.0;
		for (short s : audio) {
			sum += (double) s * (double) s;
		}
		double rms = Math.sqrt(sum / audio.length) / 32768.0;
		return (float) Math.min(1.0, rms * VOLUME_GAIN);
	}
}
