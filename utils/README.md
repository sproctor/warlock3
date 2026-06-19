# utils

Developer helper scripts for this repository. Not part of the build.

## fetch_material_symbol.py

Downloads icons from [Google Material Symbols](https://fonts.google.com/icons)
as Android vector drawables and drops them into the Compose resources so they
are usable as `Res.drawable.<name>`.

Use this script to add any Material Symbols icon. It pulls the Android
VectorDrawable XML that Google already bakes to the 960 grid (so you never
hand-transform the y-up web SVG), and rewrites the fill color to the literal
`#FFFFFF` the rest of our drawables use.

```bash
# outlined / single-version icon -> circle.xml
utils/fetch_material_symbol.py circle

# filled variant (FILL 1)        -> circle_filled.xml
utils/fetch_material_symbol.py circle --filled

# several at once
utils/fetch_material_symbol.py more_horiz logout check
```

Naming convention (matches existing drawables): no suffix for the outlined /
single-version icon, `_filled` for the filled variant. Defaults follow
fonts.google.com defaults (outlined, weight 400, grade 0, optical size 24);
`--style rounded|sharp` selects a different family. Standard library only, no
dependencies.
