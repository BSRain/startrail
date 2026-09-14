# Astronomy core sources and limits

The catalogue is a manually curated offline selection intended for drawing and learning,
not a machine-readable reproduction of a single astrometric catalogue. Its J2000/ICRS
coordinates are rounded to four decimal places in hours/degrees. Bright-star names, ordering,
magnitudes and coordinate context were checked against ESA Hipparcos material; this is an
honest provenance statement rather than a claim that all rows were bulk-extracted from ESA:

- ESA Hipparcos, **The 50 Brightest Stars**: https://www.cosmos.esa.int/web/hipparcos/brightest
- ESA Hipparcos catalogue documentation (coordinate system and epochs):
  https://www.cosmos.esa.int/web/esdc/esasky-catalogues
- ESA Hipparcos/Tycho catalogue overview:
  https://www.cosmos.esa.int/web/hipparcos/catalogues
- CDS SIMBAD object records were used for targeted checks and additions, including Gamma
  Cassiopeiae: https://simbad.cds.unistra.fr/simbad/sim-basic?Ident=Gamma+Cassiopeiae

Time and Earth-rotation calculations were checked against the US Naval Observatory's
**Computing Approximate Sidereal Time**, including the J2000 epoch and GMST reference:
https://aa.usno.navy.mil/faq/GAST

The coordinate pipeline follows the standard sequence J2000 equatorial coordinates →
precession to observation date → local hour angle → horizontal coordinates. The IAU's
Standards of Fundamental Astronomy working group is the authoritative source for production
fundamental-astronomy models: https://www.iau.org/WG191/WG191/Home.aspx

## Deliberate MVP limits

- IAU 1976 precession is included so the chart does not retain all of the J2000-to-current-date
  drift. Frame bias, nutation, annual/diurnal aberration, gravitational deflection, polar motion,
  parallax, radial velocity and per-star proper motion are omitted.
- Atmospheric refraction is omitted. Near the horizon it can shift apparent altitude by about
  half a degree; therefore `visible` means geometrically above the horizon.
- UTC is used directly where the compact formulas require UT1 and TT; DUT1, leap-second-scale
  conversion and Delta-T are not modeled. This is appropriate for an educational finder chart,
  not telescope pointing, occultation timing, astrometry or navigation.
- Constellation lines are familiar illustrative stick figures. The IAU standardizes constellation
  boundaries, not a single official line-art pattern.

## Independent numerical verification

`work/astro-tests/AstronomyVerification.java` contains fixed reference fixtures generated with
PyERFA 2.0.1.5 `eraAtco13`, which is derived from the IAU SOFA library. Reference generation used
zero pressure (refraction disabled), zero proper motion/parallax/radial velocity, DUT1=0 and zero
polar motion. Twelve cases span 1900, J2000, 2026 and 2100; northern and southern high latitudes;
east/west longitudes; and coordinates on both sides of the 0h/24h RA boundary. The test compares
horizontal direction as a spherical angular separation, avoiding unstable azimuth comparisons at
the zenith, and requires separation no greater than 0.15 degree.

PyERFA API/source documentation: https://pyerfa.readthedocs.io/ and
https://github.com/liberfa/pyerfa. ERFA is the BSD-licensed, open-source implementation of SOFA
algorithms used to create these independent fixtures; it is not shipped in the Android app.

ERFA/SOFA is deliberately more complete than the app calculation, so this tolerance verifies the
finder-chart result without implying professional astrometric precision. Dates in 1900 and 2100
also produce ERFA's expected “dubious year” warning because historical/future UTC and Earth
orientation cannot be known at current-era precision.
