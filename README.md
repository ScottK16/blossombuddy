# BlossomBuddy

A Fabric client mod for the BlossomCraft server, continuing on from the still existing BlossomSuite mod, with Jobs Overflow XP tracking folded in as a native feature.

## What's in it

- Cross-realm chat between realms, opt-in
- A player list of who else is using the mod, and where
- Vote-party alerts shared across realms
- Emotes (dances, a backflip, a cartwheel and more) other BlossomBuddy players can see
- Leaderboards: top earners and top overflow XP, overall or per realm, in game (`/buddy top`) and on the website - on by default after a notice, `/buddy top off` leaves and deletes your entry
- A dungeon timer: your own run clock and the 8 hour cooldown, kept per player (a teammate dying never resets it) - `/buddy dungeon`
- Chat Notify: a ding when words you pick (or your name) show up in chat - `/buddy notify`, off by default
- A server TPS panel (estimated from the server clock) - `/buddy tps`
- An Anticheat chat filter for staff (Vulcan alerts) in the secondary chat
- Session stats: `/buddy session` shows time played, earnings and overflow XP for this session, today and this week
- Chest finder: `/buddy find diamond` says where items were the last time you opened a container, and lights those chests up
- Discord Rich Presence ("Playing BlossomBuddy - On Cherry Realm"), opt-in - `/buddy presence`
- Music controls: what your computer is playing on a HUD, skip keys, and optional sharing in the player list (Windows and macOS, opt-in) - `/buddy music`
- A map art tool: turn a picture into a real in-game build, including a Litematica-compatible schematic export
- Item cooldown tracking, Jobs Overflow XP, inventory slot locks, and the rest of the original BlossomSuite toolkit
- A searchable options screen, and a draggable HUD editor for every panel

## Building

Requires JDK 25 (Minecraft 26.1.2).

```
./gradlew build
```

The jar lands in `build/libs/`.

## Backend

Cross-realm chat, the player list, vote sharing and map art all talk to a small relay service that runs separately from this mod. That service isn't in this repo, since it also runs the moderation and admin tools used to manage the server side of things - but everything it collects, what's opt-in, and how long it's kept is documented on the [privacy page](https://blossombuddy.site/privacy.html), and with `/buddy privacy` in-game.

## Credits

- Originally built by Zodancy as BlossomSuite (MIT, Copyright Stephen Willis)
- Jobs Overflow XP tracking based on Mills' Jobs Overflow XP (MIT)
- Continued and maintained by IrishScotty

## License

Source-available, not fully open source: read it, build it, send pull requests - but redistributing, rebranding, or running your own public copy needs permission first. The framework this was built on (the original BlossomSuite and Jobs Overflow XP) stays MIT, since that permission was already granted by their authors. See [LICENSE](LICENSE) and [NOTICE](NOTICE) for the full split.

## Support

[Discord](https://discord.gg/EA4WwSdGTj)
