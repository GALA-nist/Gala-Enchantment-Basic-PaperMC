# Gala Enchantment Basic (GEB)

Custom enchantment creator for **PaperMC 26.1.2**. Admins build effect-based
enchantments for tools, weapons and armor entirely through in-game chest GUIs.

## Commands (OP / `geb.admin` only)

| Command | What it does |
|---|---|
| `/geb create` | Opens the creator GUI (row 1 tools, row 2 weapons, row 3 armor) |
| `/geb remove` | Opens the remove GUI — deleting an enchant reloads instantly |
| `/geb reload` | Reloads `config.yml` and `systemenchantment.yml` |
| `/geb list`   | Lists every created enchantment |
| `/geb give <player> <id>` | Gives the enchanted-book version |

Aliases: `/gbe`, `/gba`, `/galaenchant` — all identical to `/geb`.

## Create flow

1. **Item select** — click Pickaxe / Sword / Chestplate / etc.
2. **Type select** — `Effect` (vanilla effects) or `Custom` (Bloodlust, Ignite, ...).
3. **Effect list** — paginated, blacklist already filtered out.
4. **Level select** — I up to XI (configurable via `max-level`).
5. **Result tab** — shows item + effect + level with:
   - **Accept** → saves to `systemenchantment.yml`
   - **Back** → previous page
   - **Decline** → cancels and reverts to page 1

Every page also has **Back** (arrow) and **Exit** (barrier) buttons.

## How enchantments work in-game

- **Armor** (Effect type): worn pieces give the wearer the effect constantly.
- **Tools** (Effect type): buff the holder while the tool is in the main hand.
- **Weapons** (Effect type): apply the effect to the entity you hit
  (5 s per level).
- **Bloodlust** (Custom, weapon only, max I): every hit on any player or mob
  heals the attacker with Instant Health I.
- **Enchanting table**: enchanting a matching item has a configurable chance
  (default 20 %) to add a random GEB enchant on top of vanilla ones.
- **Anvil**: combine an item with a GEB enchanted book (`/geb give`).

## Blacklist

`config.yml` → `blacklist.global` blocks abusable effects everywhere
(Instant Health, Levitation "shulker flying", Saturation, ...). On top of that:

- Armor/tools block **harmful** effects (they'd hurt the owner).
- Weapons block **beneficial** effects (they'd only help the enemy you hit).

## Data folder

All plugin files (config.yml + systemenchantment.yml) are stored in:
`plugins/Gala Enchantment Basic/`


