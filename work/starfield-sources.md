# Background star catalogue: source and reuse notice

`app/src/main/assets/background-stars.csv` is a magnitude-limited extract of the **HYG
Database v4.1**, assembled and maintained by David Nash (`astronexus`). HYG combines the
Hipparcos, Yale Bright Star and Gliese catalogues. The exact upstream file used was:

- Repository: https://github.com/astronexus/HYG-Database
- File: `hyg/CURRENT/hygdata_v41.csv`
- Pinned repository revision: `c7f7f883fe67`
- Download URL: https://raw.githubusercontent.com/astronexus/HYG-Database/c7f7f883fe67/hyg/CURRENT/hygdata_v41.csv
- Upstream catalogue notes: https://github.com/astronexus/HYG-Database/blob/c7f7f883fe67/hyg/README.md
- Upstream license notice: https://github.com/astronexus/HYG-Database/blob/c7f7f883fe67/hyg/CURRENT/LICENSE

HYG v4.1 and this derived CSV are licensed under the **Creative Commons
Attribution-ShareAlike 4.0 International license (CC BY-SA 4.0)**. The license text is at
https://creativecommons.org/licenses/by-sa/4.0/legalcode. Attribution: HYG Database v4.1,
David Nash / astronexus. Changes made here are described below. No endorsement by the HYG
author is implied.

## Transformation

The extract retains rows whose `mag` (apparent visual magnitude) is at most 5.5 and whose
`ra`, `dec`, and `mag` fields are present. HYG row 0, the synthetic entry for the Sun, is
excluded. Output columns are renamed/reduced to `ra_hours,dec_deg,mag,hip`; an absent HIP
identifier is represented by an empty final field. Rows retain upstream order and numeric
precision. No random or interpolated stars were added.

The file contains **2,865 real catalogue rows** plus its header and attribution comments.
Coordinates are HYG's epoch and equinox J2000.0 values: right ascension in hours and
declination in degrees. The app intentionally does not propagate per-star proper motion;
that approximation is suitable for a faint phone-scale backdrop, but not for precision
astrometry.

The app's curated `StarCatalog` contains separately maintained named stars. A renderer
should suppress a background entry when its angular position is very close to a curated
star (a threshold around 0.05 degrees is enough for the rounded curated coordinates), so a
bright object is not drawn twice. Keeping that filter in the renderer avoids coupling this
source extract to the evolving curated catalogue.
