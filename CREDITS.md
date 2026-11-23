# Credits

## Blightcaller

Blightcaller (RPG Series Plus) by Drehverschluss, licensed under the [MIT License](LICENSE). The license text is also in the root of the mod jar.

Blightcaller is a standalone class mod for **Spell Engine and the RPG Series by Daedelus**. It is not an addon for Wizards and does not need it. It is not made by or affiliated with the original author.

## Assets

All textures, models and icons in the jar were made for Blightcaller: the robe models and textures of all five robes with their glow masks, the item icons, the spell, effect and skill icons, the curse icons above the head, the projectile model of Hex Bolt, the chain link model of Gravebind, the textures of the Drain Soul strands and the Blight Burst trail, and the background of the separate skill tab. The slime ball of Blight Burst, the chains of Gravebind, the soul strands of Drain Soul and the veil over cursed mobs are drawn by code. The equip sounds of the robes are my own sound definitions that play vanilla sound files; Blightcaller has no sound files of its own.

Models, textures, sounds and other files of other mods are **only referenced by their id and not bundled**:

- Spell Engine: particles (`spell_engine:magic_skull`, `spell_engine:magic_spark`, `spell_engine:smoke_medium`, `spell_engine:dripping_blood` …), sounds (`spell_engine:generic_soul_casting`, `spell_engine:generic_soul_release`), cast animations, the spell book and scroll items and the Spell Binding Table, used by id in the spell files and item data.
- `runes:soul_stone`: the Soul Rune of Runes, used as the spell cost and as the result of the rune recipes; the rune crafting recipe type `runes:crafting`.
- `betterend:dragon_bone_block` and `betterend:aeternium_ingot`: used by id in the two recipes of the dragonbone weapons (only with Better End).
- Vanilla Minecraft: the chain texture (on the Gravebind chain model), the sound files behind the equip sounds of the robes (cloth, moss and bone block steps, referenced in `sounds.json`), other sounds and particles of the spells, and the vanilla smithing slot icons. No sound file is bundled.

The spell files are my own, written in the format of Spell Engine. No code, models, textures, sounds or data files of the RPG Series mods are copied.

## Ideas

- The layout of the Blightcaller branch (28 nodes, 13 points, two sides with a choice on every tier) follows the class branches of More RPG Classes – Skill Tree by Fichte, the same way as my Stormcaller branch. Only structure and scale were used, no files.
- A standalone class without Wizards was modeled on the class mods Berserker and Forcemaster of the RPG Series. No files or code of them are used.
- The Wizard Merchant offers are added to the merchant's pools the way Elemental Wizards (RPG Series Plus) by Fichte does it. No code of it is used.

## Mods Blightcaller works with (not bundled)

Blightcaller contains no code, textures, models, sounds or translations of these mods. It is compiled against the APIs listed below and otherwise only refers to their ids (items, spells, tags, particles, sounds, `neoforge:mod_loaded` conditions).

- [Spell Engine](https://www.curseforge.com/minecraft/mc-mods/spell-engine) by Daedelus (GPL-3.0): required. Compiled against its API; spells, items, equipment sets, the spell book and loot run on it. Not bundled.
- Tiny Config by Daedelus (CC0-1.0): comes with Spell Engine; the JSON configs use it. Not bundled.
- [Spell Power Attributes](https://www.curseforge.com/minecraft/mc-mods/spell-power) by Daedelus (GPL-3.0): required. The soul school, its attribute and damage type. Compiled against it, not bundled.
- [Runes](https://www.curseforge.com/minecraft/mc-mods/rune-crafting) by Daedelus (All Rights Reserved): required. The Soul Rune and the rune crafting recipe type are used by id. Not bundled.
- [Armor Model API](https://www.curseforge.com/minecraft/mc-mods/armor-model-api) by Daedelus (MIT): required. Draws the robe models. Compiled against it, not bundled.
- [Skill Tree (RPG Series)](https://www.curseforge.com/minecraft/mc-mods/skill-tree) by Daedelus (All Rights Reserved): optional. When its Class Skills and Weapon Skills tabs load, Blightcaller adds its own nodes to them (the Blightcaller class branch and three nodes for Hex Bolt and Blight Burst) and reads the tabs only to find a free place. Not bundled.
- Pufferfish's Skills by Pufferfish (All Rights Reserved according to its mod file): optional, comes with Skill Tree. The separate skill tab is a datapack for it; compiled against its API for the class lock. Blightcaller has mixins in Pufferfish's Skills that add its nodes to the Class Skills and Weapon Skills tabs while they load (only at runtime, in memory). Not bundled.
- More RPG Classes – Skill Tree by Fichte (All Rights Reserved): optional, tested with 1.3.1. Blightcaller adds its nodes to its versions of the Class Skills and Weapon Skills tabs the same way. Not bundled.
- No files of Skill Tree, More RPG Classes – Skill Tree or Pufferfish's Skills are copied or bundled: the class branch, the weapon nodes, their spell files and the layout files (`class_branch.json`, `weapon_branch.json`) are my own. Their nodes, tabs and ids are only read while the game runs.
- [Curios API](https://www.curseforge.com/minecraft/mc-mods/curios) by C4 (LGPL-3.0-or-later): optional. Compiled against its API for the rings, amulets and relics. Not bundled.
- [Jade](https://www.curseforge.com/minecraft/mc-mods/jade) by Snownee (CC-BY-NC-SA-4.0): optional. Compiled against its API for the curse lines in its tooltip. Not bundled.
- [Health Bars](https://www.curseforge.com/minecraft/mc-mods/health-bars) by Fuzs (MPL-2.0): optional, client only. Blightcaller reads its public config and adds a row of curse icons to its health bar box. Not bundled.
- [RunelessRPG](https://www.curseforge.com/minecraft/mc-mods/runelessrpg) by Drehverschluss (MIT): recommended. No direct use; the file `data/blightcaller/runelessrpg/spell_costs/blightcaller.json` gives the spells their mana costs. Not bundled.
- [Better Combat](https://www.curseforge.com/minecraft/mc-mods/better-combat-by-daedelus) by Daedelus (All Rights Reserved): recommended. The weapon files of the wands refer to its preset `bettercombat:wand`. Not bundled.
- [Better End](https://www.curseforge.com/minecraft/mc-mods/betterend-neoforge) by quiqueck, paulevs and others (MIT, CC BY-NC-SA 4.0 for some assets): optional. Its Dragon Bone Block and Aeternium Ingot are used by id in two recipes, the Aeternium Ingot also as repair material. Not bundled.
- [Wizards (RPG Series)](https://www.curseforge.com/minecraft/mc-mods/wizards) by Daedelus (All Rights Reserved): optional. Only its Wizard Merchant is used by id, to add Blightcaller offers. Not bundled.
- [Stormcaller (RPG Series Plus)](https://www.curseforge.com/minecraft/mc-mods/stormcaller-rpg-series-plus) by Drehverschluss (MIT): optional. Both branches fit into the Class Skills tab together. Not bundled.
- Other ids that are only used in optional tag entries: `#c:bosses`, `#spell_engine:bosses`, `#rpg_series:*` loot and type tags, `#spell_engine:spell_ring`, `spell_necklace`, `spell_trinket`, `#wizards:wizard_runes`, `#curios:ring`, `#curios:necklace` and `#apothic_attributes:cannot_critically_strike`.

## License

Blightcaller is licensed under the [MIT License](LICENSE).
