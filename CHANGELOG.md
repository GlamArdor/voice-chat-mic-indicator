# Changelog

## 1.0.1

- Fixed: the icon was hidden by Minecraft's clouds – it now renders at the very
  end of the world pass, so clouds (and weather) no longer cover it.
- Fixed: whispering players showed an icon from far away even when they could not
  be heard – the icon now respects the actual audible range of the sound, so a
  whisper only shows within whisper range.

## 1.0.0

- Initial release: shows a microphone/speaker indicator above talking players in
  Simple Voice Chat, visible even when name tags are hidden.
- Visible through walls, above block entities (signs, chests…).
- In-game settings screen (Mod Menu + Cloth Config): toggle, size, opacity,
  height, render distance, through-walls, volume pulse, fade in/out, show own icon.
- Volume pulse and smooth fade in/out.
- Crown badge above the creator's icon.
