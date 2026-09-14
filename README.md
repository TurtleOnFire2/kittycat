mraow~

## Commands

- `/kc` or `/kc gui` — opens the click gui (default keybind: Right Shift)
- `/kc hud` — opens the hud editor
- `/kc macros` — opens the chat macro manager
- `/rotate <yaw> <pitch>` — rotates your camera to the given angles

## Features

### Kuudra

- **Auto GFS** — Keeps 16 ender pearls in your inventory via `/gfs` and gets Toxic / Twilight Arrow Poison (configurable amounts) when the ballista is ready
- **Backbone Alert** — Alerts when the backbone is hit, optionally with the item, helmet and time it was hit at, plus a waypoint for the backbone position
- **Build** — Progress HUD for build/pile percentages (configurable order), hides useless armor stands, highlights the pile with a progress-colored outline, replaces build sounds with a chosen sound, announces Fresh Tools, "Flowstate" glow and streaks while Fresh is active, and a "GO STUN!" alert above a configurable progress threshold (optionally only on the left side of the ballista)
- **Crate Priority** — Detects which crate you pre'd and shows a title for what your party is missing (Triangle, X, Equals, Slash, X Cannon, Square, Shop)
- **Fixes** — Hollow Wand click-through fix and cancels placing the Etherwarp Conduit
- **Hide Tags** — Hides `[Lv...]` mob name tags in Kuudra
- **Kuudra Display** — Highlights Kuudra's hitbox and shows its HP
- **Pearl Waypoints** — Waypoints and timers for single/double pearl throws to supplies, with configurable offsets, a triggerbot that throws when aimed correctly and separate aim assists (FOV + strength) for single and double pearls
- **Rend Damage** — Tracks who did damage during the rend with a configurable swing window
- **Rend Macro**:
  - Auto sneak: sneaks when getting tped down
  - Auto rotate: rotates to the pod per side (front / right / left / back yaw ranges, yaw and pitch offsets, rotation delay)
  - Auto walk / Auto jump: walks and jumps at the edge
  - Auto hollow wand: uses hollow wand when tping down (separate click delays for both clicks), optional Raging Wind
  - Auto rod: uses rod after tping down or after the hollow wand, with per-side position offsets and an optional area render
  - Auto bone: throws the bone when you exit the configured area (bone delay = fastest it will throw after rod)
  - Auto halberd: after using the bone (manually or automatically) swaps to halberd and right clicks it
  - Auto loadout: after the halberd opens the loadout menu, clicks the selected slot and closes the gui. Optionally uses Ice Spray afterwards and swaps back to AOTS
  - Auto pull on backbone: swaps to the selected slot and left clicks after a configurable delay
  - Debug toggle
- **Safe Spots** — Renders the safe spots during supplies, green if no magma cube is in the way and red otherwise
- **Stun** — Auto opens the shop (with an optional area render), waypoint and aim assist for the etherwarp spot to insta-mount the cannon, optionally only on the left side of the ballista, auto sets the cursor on shop open, auto closes the shop, no blindness, stun waypoint for the chosen pod (Left / Back / Right) with aim assist, auto Pickobulus and early Pickobulus when entering the belly
- **Supplies** — Pickup progress HUD, supply and drop-off beacons with configurable colors (including a hovered color) and auto walk after the pearl lands within a configurable range
- **Supply Cheats** — Reach with configurable range, aura with range, checks (on ground, FOV check, RMB only, rod only), FOV and delay
- **Tiny kuudra mobs** — Scales Kuudra mobs down to a configurable size

### Dungeons

- **Lever Triggerbot** — Auto clicks gate and/or device levers when you look at them
- **Relics** — Cauldron triggerbot for the relic you picked up and renders the relic spawn boxes after Necron dies (green if in reach)
- **Storm** — Death Bow tint at max pull, auto swap crit item (delay + slot), auto swap armor via loadout slot, auto release Last Breath at a configurable time, auto track Storm (pitch limit, waypoint offset), auto walk forward, auto swap term, left click with term after, auto hit Storm at purple / yellow times, swap item after right click leap, auto sneak at yellow
- **Terminals** — Terminal triggerbot and terminal hitbox render (green if in reach)

### Misc

- **Bestiary HUD** — Displays bestiary gained per hour and an ETA until the next level, with timeout, show-active-only, reset on world change and a manual session reset
- **Chat Macros** — Runs something on configurable messages (`/kc macros`)
- **Farm Helper** — Auto warp to pests (sets spawn first), auto loadout with separate spawning and farming slots, auto warp back after pests, random delay and pest cooldown
- **Pests** — Pest ESP with configurable color

### Visual

- **Cat Ears** — Renders cat ears and an animated tail on players, with tint and an option for other players
- **Click Gui** — Gui keybind, font mode, base and accent colors, color themes and a reset

### Debug

- **Pearl Landing Debug** — Predicts where your thrown pearl lands from its actual motion, renders the path and the estimated Hypixel destination, with local debug messages, landing position printing and a configurable time to keep the prediction after landing
- **Kuudra Dev** — Force the kuudra / supplies / build / stun / dps phase states
- **Example Feature** — Showcase of every setting type
