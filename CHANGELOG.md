# Changelog

## 0.1.0 (Beta)

First public version, released as a beta: the numbers can still change, and bug reports and balance feedback are very welcome on the [issue tracker](https://github.com/Drehverschluss/Blightcaller/issues).

- **A curse class of the soul school** (`spell_power:soul`) for Spell Engine and the RPG Series. Standalone: it does not need Wizards and brings its own tier 1 gear.
- **Curses**: own damage-over-time effects that tick every second and work on undead and on bosses that refuse effects (Wither, Ender Dragon and others). Every caster has their own entry on a target, a broken last second still ticks with its share, and curse ticks trigger nothing (no relics, no passives, no knockback).
- **Curse level**: the number of different curses on a target, from anyone, up to 3. Hex Bolt, Drain Soul and Last Rites deal more damage per curse level.
- **New attribute Curse Duration** (`blightcaller:curse_duration`): curses last longer, up to +60 % by default. Robes, jewelry and relics give it.
- **Eight spells**: Hex Bolt (wand) and Blight Burst (grimoire), and in the Tome of Blight Soul Rot, Enfeeble, Gravebind, Drain Soul, Pestilence and Last Rites. Hex Bolt flies as a curse skull with a soul flame trail, Blight Burst as a ball of slime, Gravebind binds enemies with ghostly chains and Drain Soul tears a see-through copy of the soul out of its target.
- **Bosses**: damage over time works fully on bosses; Enfeeble, Gravebind and the other weakenings work at half strength on `#blightcaller:curse_resistant` (`#c:bosses`, `#spell_engine:bosses`, Warden, Elder Guardian). `#blightcaller:curse_immune` makes a mob immune on purpose.
- **Seeing curses**: own particles for every curse, curse icons above the head with their remaining time (your own bright, those of other players pale), an optional veil per curse level, a curse row at the Health Bars box and curse lines in Jade. Client options for all of them and for the red flash and the hurt sound of curse ticks.
- **Robes** from tier 1 to 4 with Soul Spell Power and Curse Duration: Blightcaller Robe, Blight Robe, Netherite Blight Robe, and two tier 4 sets with set bonuses, the Blightspore Robe (Pestilence) and the Hexbinder Robe (Last Rites), made with the Gravemother Upgrade and two Gravemother crystals from loot. Own equip sounds.
- **Weapons**: one-handed wands (Hex Bolt) and grimoires (Blight Burst) from tier 1 to 3, the Dragonbone Blight Wand and Grimoire of tier 4 (with Better End), and two unique weapons of the Gravemother from loot: Gravemother's Fingerbone with the passive Bone Spike and Gravemother's Herbarium with the passive Grave Mould.
- **Soul Runes**: recipes by hand and at the Rune Crafting Altar. Without RunelessRPG every spell costs one Soul Rune.
- **RunelessRPG**: with it, the spells cost mana instead (3.5 to 10, Drain Soul 20 for the full channel); relics and passives cost nothing.
- **Jewelry and relics** (with Curios API): Rotheart Signet and Amulet, Witchbone Band and Pendant, Grave Candle, Blight Orb and Wailing Skull.
- **Skill tree** (with Skill Tree (RPG Series)): a Blightcaller branch with 28 nodes in the Class Skills tab, next to Stormcaller if both are installed, with side A for groups (Soul Migration, Blightbloom, Infectious Curses) and side B for single targets (Deepening Rot, Vengeful Hex, Coup de Grace) on every tier. Three nodes for Hex Bolt and Blight Burst in the Weapon Skills tab. Also works with More RPG Classes – Skill Tree, or as a separate tab.
- **Soul Migration without Skill Tree**: one config switch makes it a base mechanic for packs without the skill tree.
- **Loot, trades and advancements**: Blightcaller gear, spell scrolls and the tier 4 materials drop through the RPG Series loot tiers; with Wizards the Wizard Merchant sells Soul Runes, the Blight Wand and an enchanted Blight Grimoire; a chain of advancements; every recipe unlocks in the recipe book.
- **For pack makers**: spell values in the spell files (datapack), server config `blightcaller-server.toml`, client config, `equipment.json`, `trinkets.json`, `tweaks.json`, the mana file for RunelessRPG, the skill tree layout files and the command `/blightcaller curse apply|info|clear`.
- Tested on a dedicated server and with Complementary Unbound and Euphoria Patches.
