# Radial Menu

A pie menu for Hypixel SkyBlock. Bind commands to a ring, execute them with one click.

![Minecraft Version](https://img.shields.io/badge/Minecraft-1.21.10-green)
![Fabric](https://img.shields.io/badge/Loader-Fabric-blue)

---

## What it does

Press **R** → move mouse → click. Command runs. Done.

Supports sub-menus, custom icons (including custom player heads), and full colour theming.

![menu screenshot](screenshot.png)

---

## Install

Drop into your mods folder alongside:
- [Fabric API](https://modrinth.com/mod/fabric-api)
- [Fabric Language Kotlin](https://modrinth.com/mod/fabric-language-kotlin)

---

## Controls

| | |
|---|---|
| **R (Default, customisable)** | Open menu |
| **Mouse** | Hover to select |
| **Left click** | Execute |
| **Right click / Escape** | Close |
| **E** (in menu) | Open editor |

---

## Editor

- **Click a folder** to expand its children inline
- **Icon field** accepts a Minecraft item ID or a base64 value from [minecraft-heads.com](https://minecraft-heads.com) for custom heads
- **🎨 Theme** button to customise all colours

---

## Config

`~/.minecraft/config/radial-menu.json` — menu layout  
`~/.minecraft/config/radial-menu-theme.json` — colours

---

> Client-side only. Commands are sent exactly as if you typed them.  
> Use of macro-style mods on Hypixel is at your own risk.
