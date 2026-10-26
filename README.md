![icon](https://cdn.modrinth.com/data/cached_images/42fb4c14522d728405429424c54214521d7740d8_0.webp)

# New:Sim-U-Kraft

New:Sim-U-Kraft is a city-building and NPC life simulation mod for Minecraft. It lets you
found a city, claim land, place buildings, hire citizens, and turn a simple settlement into
a working town with construction, farming, commerce, industry, and logistics.

The mod is available for **Minecraft 1.21.1 on NeoForge** and also supports
**Minecraft 1.20.1 on Forge**.

![Available for NeoForge](https://img.shields.io/badge/Available%20for-NeoForge-f16436?style=for-the-badge)
![Available for Forge](https://img.shields.io/badge/Available%20for-Forge-f16436?style=for-the-badge)

## Installation Guide

Download the file that matches your Minecraft version and mod loader. Use the **NeoForge**
build for Minecraft **1.21.1**, or the **Forge** build for Minecraft **1.20.1**.

Once the correct loader is installed, place New: Sim-U-Kraft into your `mods` folder. You
also need the matching version of **LDLib / LDLib2** for your Minecraft version.

## Gameplay

Start by placing a City Core to create your city. From there, you can claim chunks, manage
city funds, and begin growing a population of citizen NPCs.

Buildings are selected through the Build Box. Choose a building, preview it in the world,
confirm the placement, and let hired builders gather materials and construct it.

Citizens can take on different jobs such as builder, planner, farmer, commercial worker,
industrial worker, and logistics worker. Over time, your city can grow from a small base
into a living production network with homes, farms, shops, factories, warehouses, and item
routes.

## Extra Notes

New: Sim-U-Kraft is designed for both singleplayer and dedicated servers. City data and NPC
work are handled by the server, while the client displays HUDs, screens, previews, and
status updates.

The mod includes English and Simplified Chinese localization, configurable client and
server options, and optional city highlighting support for Xaero's World Map.

## Sponsor Authorization

Only sponsor-edition builds (`-PsponsorEdition`) enable website-confirmed device-code
pairing. Public builds do not initialize authorization, register its key binding or command,
or show its configuration entry. Sponsor builds require a locally verified `sponsor`
entitlement before gameplay and keep the native authorization screen open until a valid
License is available. The screen is also available from the NeoForge Mods configuration
page, the configurable key binding, or the client-only `/nsuk auth` command. Network
validation remains asynchronous, and a still-valid cached License supports offline use.
The production issuer/API origin and current RS256 public key are pinned in the client;
rotating that key requires releasing a transition client that trusts both keys before the
server switches signers. Legacy v2 token validation is not accepted by current clients.
Long-lived credentials and cached licenses are OS-bound: Windows uses per-user DPAPI,
Linux derives an AES-GCM key from machine-id and the OS user, and macOS uses
IOPlatformUUID and the OS user. File permissions are restricted as well, so copying the
authorization file to another computer or user account cannot clone authorization.

Sponsor builds hash their final running JAR and submit the SHA-256, Minecraft version,
and mod version during pairing and refresh. The website only issues a build-bound v3
License for an enabled official build. `@SponsorFeature` and `SponsorGate` provide the
framework for future sponsor-only entry points; no existing gameplay feature is gated by
that framework in this version. The Gradle `verifySponsorFeatureGates` check rejects a
future annotated source file that omits its gate call.

Sponsor artifacts also embed the private `nsuk-injection-prevention-detection` library as
JiJ. It checks the authoritative NeoForge mod-file path and explicit JVM injection options
without scanning arbitrary files or processes. Public builds use a reflection bridge and
therefore remain compilable without access to the private repository; sponsor builds fail
their Gradle validation or runtime authorization if the private JiJ is missing.

## License

New: Sim-U-Kraft is licensed under **GPL 3.0**.
