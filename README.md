# ZelDuelsAnnouncer

A small add-on for [ZelDuels](https://builtbybit.com/resources/zelduels-auto-arena-gen-duels.49087/) that
announces every duel result to the whole server:

> **DUEL** » Player1 just defeated Player2 in Classic with 3.5❤ left!

## Install

1. Download `ZelDuelsAnnouncer-x.y.z.jar` from the [Releases](../../releases) page.
2. Put it in your `plugins` folder next to ZelDuels and restart.
3. Edit `plugins/ZelDuelsAnnouncer/config.yml` to change the messages, then run `/zdannouncer reload`.

## Messages

- **win**: someone was defeated normally.
- **forfeit**: the loser left the arena or disconnected.
- **timeout**: the timer ran out with no winner (off by default).

Placeholders: `<winner>`, `<loser>`, `<kit>`, `<arena>`, `<hearts>`, `<health>`, `<player1>`, `<player2>`.
Formatting uses [MiniMessage](https://docs.advntr.dev/minimessage/format.html).

## Commands & permissions

| Command | Permission | Default |
| --- | --- | --- |
| `/zdannouncer reload` | `zelduelsannouncer.admin` | op |

## Building

GitHub Actions builds the jar on every push and tests it on a real Paper 26.3 server.
Changing `version` in `build.gradle.kts` publishes a new GitHub Release.
