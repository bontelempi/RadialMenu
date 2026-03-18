# Radial Menu

Too many keybinds? This is the solution.

## Features

- **Radial menu** — press R to open, move mouse to select, click to execute
<img width="1830" height="1080" alt="Screenshot_2026-03-17_at_5_19_31_pm_cropped" src="https://github.com/user-attachments/assets/f2e05c49-9ee4-421f-a04b-5cdc6a6a7143" />

- **Sub-menus** — up to 4 rings deep, expanding outward on hover. Sub-menu items can also run a command on click.
<img width="1830" height="1080" alt="Screenshot_2026-03-17_at_5_19_45_pm_cropped" src="https://github.com/user-attachments/assets/552a7cf9-1ecf-4fc6-89f4-ef0dd0854de0" />

- **Presets** — up to 10 named menu configs. Cycle with A/D while the menu is open, or click the arrows in the centre.
- **Custom icons** — any Minecraft item ID, or a custom player head from minecraft-heads.com
- **In-game editor** — add, delete, reorder, and nest items without leaving the game. Editor colours follow your active theme.
<img width="1830" height="1080" alt="Screenshot_2026-03-17_at_5_20_09_pm_cropped" src="https://github.com/user-attachments/assets/e28d8a58-707e-4621-b613-8d6692a72150" />

- **Themes** — 5 built-in colour presets, assignable per menu preset. Custom themes can be added by dropping a JSON file into the config folder.

## Requires

- [Fabric API](https://modrinth.com/mod/fabric-api)
- [Fabric Language Kotlin](https://modrinth.com/mod/fabric-language-kotlin)

## Controls

| | |
|---|---|
| **R** (rebindable) | Open menu |
| **Mouse** | Hover to select |
| **Left click** | Execute |
| **A / D** | Cycle presets |
| **Right click / Escape** | Close |
| **E** (in menu) | Open editor |

## Config

`~/.minecraft/config/radial-menu.json` — menu layout and presets  
`~/.minecraft/config/radial-menu-theme.json` — custom theme library

> Client-side only. Commands are sent exactly as if you typed them.  
> Use of macro-style mods on Hypixel is at your own risk.
