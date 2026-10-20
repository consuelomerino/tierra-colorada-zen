# Credits

## Sounds

Every sound in `app/src/main/res/raw/` is synthesised by [`tools/gen_sounds.py`](tools/gen_sounds.py)
in this repository and is released under [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/).

| File | What it is |
|---|---|
| `ambient_rainforest.wav` | 30 s seamless loop: soft rain, distant water rumble, bird chirps |
| `rake_scrape.wav` | 2 s loop of sand scraping, played while raking |
| `stone_place.wav` | Low thud when a stone is set down |
| `water_tap.wav` | "Plip" when the pond is touched |
| `clear_sweep.wav` | Sweep when the garden is cleared |

To regenerate them: `python3 tools/gen_sounds.py app/src/main/res/raw`

If any file is replaced by a field recording later, add a line here with its source URL and licence.
