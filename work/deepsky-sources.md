# Deep-sky catalogue sources

`DeepSkyCatalog` is a deliberately small offline observing list. Coordinates are
ICRS/J2000, right ascension is stored in hours, and the displayed magnitude is an
integrated apparent visual magnitude. Values are rounded for finder-chart use.
Because an extended object's light is spread over an area, its integrated magnitude
must not be compared directly with the point-like appearance of a star.

## Tabular data and attribution

Coordinates, major-axis sizes and (where present) V magnitudes were transcribed and
rounded from [OpenNGC's upstream `NGC.csv`](https://github.com/mattiaverga/OpenNGC/blob/master/database_files/NGC.csv)
and [`addendum.csv`](https://github.com/mattiaverga/OpenNGC/blob/master/database_files/addendum.csv).
OpenNGC is by Mattia Verga and is distributed under
[CC BY-SA 4.0](https://github.com/mattiaverga/OpenNGC/blob/master/LICENSES/CC-BY-SA-4.0.txt); this
derived selection therefore retains that attribution and share-alike notice.
OpenNGC documents its upstream provenance (including NASA/IPAC NED, HyperLEDA,
SIMBAD and HEASARC) in its [project README](https://github.com/mattiaverga/OpenNGC).

Per-object upstream records (the link identifies the exact row/object used):

| App ID | Catalogue identity | Primary data record |
|---|---|---|
| `m31` | M31 / NGC 224 | [OpenNGC NGC.csv](https://github.com/mattiaverga/OpenNGC/blob/master/database_files/NGC.csv) (`Name=NGC0224`) · [SIMBAD M31](https://simbad.cds.unistra.fr/simbad/sim-id?Ident=M+31) |
| `m33` | M33 / NGC 598 | [OpenNGC NGC.csv](https://github.com/mattiaverga/OpenNGC/blob/master/database_files/NGC.csv) (`Name=NGC0598`) · [SIMBAD M33](https://simbad.cds.unistra.fr/simbad/sim-id?Ident=M+33) |
| `m42` | M42 / NGC 1976 | [OpenNGC NGC.csv](https://github.com/mattiaverga/OpenNGC/blob/master/database_files/NGC.csv) (`Name=NGC1976`) · [SIMBAD M42](https://simbad.cds.unistra.fr/simbad/sim-id?Ident=M+42) |
| `m45` | M45 / Pleiades | [OpenNGC addendum](https://github.com/mattiaverga/OpenNGC/blob/master/database_files/addendum.csv) (`Name=Mel022`) · [SIMBAD M45](https://simbad.cds.unistra.fr/simbad/sim-id?Ident=M+45) |
| `m44` | M44 / NGC 2632 | [OpenNGC NGC.csv](https://github.com/mattiaverga/OpenNGC/blob/master/database_files/NGC.csv) (`Name=NGC2632`) · [SIMBAD M44](https://simbad.cds.unistra.fr/simbad/sim-id?Ident=M+44) |
| `m13` | M13 / NGC 6205 | [OpenNGC NGC.csv](https://github.com/mattiaverga/OpenNGC/blob/master/database_files/NGC.csv) (`Name=NGC6205`) · [SIMBAD M13](https://simbad.cds.unistra.fr/simbad/sim-id?Ident=M+13) |
| `m22` | M22 / NGC 6656 | [OpenNGC NGC.csv](https://github.com/mattiaverga/OpenNGC/blob/master/database_files/NGC.csv) (`Name=NGC6656`) · [SIMBAD M22](https://simbad.cds.unistra.fr/simbad/sim-id?Ident=M+22) |
| `omega_cen` | Omega Centauri / NGC 5139 | [OpenNGC NGC.csv](https://github.com/mattiaverga/OpenNGC/blob/master/database_files/NGC.csv) (`Name=NGC5139`) · [SIMBAD NGC 5139](https://simbad.cds.unistra.fr/simbad/sim-id?Ident=NGC+5139) |
| `m57` | M57 / NGC 6720 | [OpenNGC NGC.csv](https://github.com/mattiaverga/OpenNGC/blob/master/database_files/NGC.csv) (`Name=NGC6720`) · [SIMBAD M57](https://simbad.cds.unistra.fr/simbad/sim-id?Ident=M+57) |
| `m27` | M27 / NGC 6853 | [OpenNGC NGC.csv](https://github.com/mattiaverga/OpenNGC/blob/master/database_files/NGC.csv) (`Name=NGC6853`) · [SIMBAD M27](https://simbad.cds.unistra.fr/simbad/sim-id?Ident=M+27) |
| `m8` | M8 / NGC 6523 | [OpenNGC NGC.csv](https://github.com/mattiaverga/OpenNGC/blob/master/database_files/NGC.csv) (`Name=NGC6523`) · [SIMBAD M8](https://simbad.cds.unistra.fr/simbad/sim-id?Ident=M+8) |
| `m20` | M20 / NGC 6514 | [OpenNGC NGC.csv](https://github.com/mattiaverga/OpenNGC/blob/master/database_files/NGC.csv) (`Name=NGC6514`) · [SIMBAD M20](https://simbad.cds.unistra.fr/simbad/sim-id?Ident=M+20) |
| `m51` | M51 / NGC 5194 | [OpenNGC NGC.csv](https://github.com/mattiaverga/OpenNGC/blob/master/database_files/NGC.csv) (`Name=NGC5194`) · [SIMBAD M51](https://simbad.cds.unistra.fr/simbad/sim-id?Ident=M+51) |
| `m81` | M81 / NGC 3031 | [OpenNGC NGC.csv](https://github.com/mattiaverga/OpenNGC/blob/master/database_files/NGC.csv) (`Name=NGC3031`) · [SIMBAD M81](https://simbad.cds.unistra.fr/simbad/sim-id?Ident=M+81) |

The exact extracted fields used by the app are committed in `work/deepsky-reference.csv`.
`angularSizeArcmin` is OpenNGC's reference-catalogue major axis. It is useful for
choosing a field of view, but does not promise the full faint photographic extent
or the visually detectable diameter under a particular sky. All 14 selected rows
have an OpenNGC V magnitude; none substitutes a central-star magnitude.

## Descriptions and observing guidance

The Chinese descriptions and equipment notes are original summaries. They are
grounded in NASA's public Messier observing pages, especially the
[Hubble Messier catalogue](https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/),
[M31 page](https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-31/),
and NASA's [smartphone astrophotography guide](https://science.nasa.gov/wp-content/uploads/2023/09/Astrophotography_Guide.pdf).
They intentionally avoid promising photographic colour or structure at the eyepiece.
No external imagery is included or redistributed.
