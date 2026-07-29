# **Voice Chat Mic Indicator**

A client-side add-on for Simple Voice Chat that shows a microphone indicator above a player's head while they're talking - even when name tags are hidden.

## The problem:

Simple Voice Chat draws its "speaking" icon next to the player's name tag. On servers that hide name tags, that icon is hidden too, so you can't tell who's talking in a crowd.

## What this mod does:

This add-on draws the indicator directly above the talking player's head, completely independent of the name tag - so you always know who's speaking.

## Features:

- Shows an icon above any player currently transmitting voice in Simple Voice Chat.
- Microphone icon for yourself, speaker icon for other players, with separate whisper variants.
- Visible even with hidden name plates and through walls.
- Semi-transparent.
- Your own indicator shows only in third person.
- In-game settings screen: toggle the indicator on/off, and adjust size, opacity, height, render distance, and see-through mode.

## Requirements:

- Fabric API (https://modrinth.com/mod/fabric-api)
- Simple Voice Chat (https://modrinth.com/plugin/simple-voice-chat)

## Notes:

This is a client-side mod - install it only on the client of anyone who wants to see the indicators. It does not need to be installed on the server, and other players don't need it for you to see their indicators (it's rendered locally from the voice audio you receive).