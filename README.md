# Radial Menu

A pie menu for Hypixel SkyBlock. Bind commands to a ring, execute them with one click.

![Minecraft Version](https://img.shields.io/badge/Minecraft-1.21.10-green)
![Fabric](https://img.shields.io/badge/Loader-Fabric-blue)


## What it does

Press **R** → move mouse → click. Command runs. Done.

Supports sub-menus, custom icons (including custom player heads), and full colour theming.

<img width="2560" height="1440" alt="Screenshot 2026-03-16 at 6 06 51 am" src="https://github.com/user-attachments/assets/586c2f26-ba07-4bbc-b91d-482dcdfa7c3d" />

<img width="2560" height="1440" alt="Screenshot 2026-03-16 at 6 07 01 am" src="https://github.com/user-attachments/assets/df8bf0fa-3678-42bd-9373-19270a6c767b" />

<img width="2560" height="1440" alt="Screenshot 2026-03-16 at 6 07 21 am" src="https://github.com/user-attachments/assets/3c7a5f67-0b0c-4a26-827f-067e954aeccf" />

<img width="2560" height="1440" alt="Screenshot 2026-03-16 at 6 07 32 am" src="https://github.com/user-attachments/assets/7751688b-2dd0-4c5e-947d-5ad472af735f" />


## Install

Drop into your mods folder alongside:
- [Fabric API](https://modrinth.com/mod/fabric-api)
- [Fabric Language Kotlin](https://modrinth.com/mod/fabric-language-kotlin)


## Controls

| | |
|---|---|
| **R (Default, customisable)** | Open menu |
| **Mouse** | Hover to select |
| **Left click** | Execute |
| **Right click / Escape** | Close |
| **E** (in menu) | Open editor |


## Editor

- **Click a folder** to expand its children inline
- **Icon field** accepts a Minecraft item ID or a base64 value from [minecraft-heads.com](https://minecraft-heads.com) for custom heads
- **🎨 Theme** button to customise all colours


## Config

`~/.minecraft/config/radial-menu.json` — menu layout  
`~/.minecraft/config/radial-menu-theme.json` — colours


> Client-side only. Commands are sent exactly as if you typed them.  
> Use of macro-style mods on Hypixel is at your own risk.
