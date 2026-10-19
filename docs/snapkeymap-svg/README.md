# SnapKey Map artwork

These SVGs and PNGs are the supplied brand assets. The website uses the icon in its header and footer, the favicon variants in browser tabs, the Apple touch icon, and the 192px and 512px icons in its web manifest.

Android resources in `app/src/main/res/drawable/` are vector adaptations of the same artwork:

- `ic_launcher_background.xml` uses the blue background gradient, with Android providing the launcher mask.
- `ic_launcher_foreground.xml` uses the main icon's colored symbol, scaled around its center to stay in the adaptive-icon safe area.
- `ic_launcher_monochrome.xml` uses the monochrome symbol with transparent cutouts instead of white fills for themed icons.
- `ic_stat_snap.xml` uses the monochrome symbol at notification-icon size.

When changing the SVG artwork, update the Android vectors and PNG variants together. Keep the Android foreground and monochrome layers aligned. Windows `Zone.Identifier` metadata is not part of the artwork and must not be published.
