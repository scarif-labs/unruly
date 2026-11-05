# Unruly logo

The mark is Inter Black's lowercase "u" with its right stem kicked out past the baseline (logo
direction L2-D, `~/Desktop/Unruly Logo Concepts/Round 2 - Icon + Loading Directions/`). It is built
from the real glyph outline, so it matches the type in the onboarding tunnel exactly.

    python3 -m venv /tmp/logovenv && /tmp/logovenv/bin/pip install fonttools skia-pathops
    cd tools/logo && /tmp/logovenv/bin/python assets.py

writes `ic_launcher_foreground`, `ic_launcher_background`, `ic_splash`, `ic_splash_wordmark` and
`ic_unruly_24` into `app/src/main/res/drawable/`. The legacy `mipmap-*/ic_launcher.png` icons (for
Android 7.x) were rendered from the same path with the skin kit's `shot.mjs --transparent`.

Colours: Unruly red `#E3191B`, ink `#111111`.
