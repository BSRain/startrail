# Solar-system positions and verification

Runtime: Astronomy Engine 2.1.19, Don Cross, MIT.
Source: https://github.com/cosinekitty/astronomy/tree/v2.1.19
JVM artifact: io.github.cosinekitty:astronomy:2.1.19 from JitPack.
The engine and Kotlin runtime are bundled; no runtime network is used.

SolarSystem.java requests observer-based equatorial coordinates of date with corrected aberration, then airless horizontal coordinates. Observer altitude is sea level. The Moon includes topocentric parallax. Illumination and moon phase come from the engine; symbols are enlarged for recognition.

Independent reference: NASA/JPL Horizons API, https://ssd-api.jpl.nasa.gov/doc/horizons.html
Retrieval: work/fetch-solar-reference.py. Raw JSON and extracted fixtures: work/solar-tests/. Queries use coord@399 observer coordinates, sea level, AIRLESS, quantities 4/10/20, km, no range rate.

Fixtures: all nine bodies at 2026-09-06T12:00Z Beijing; Moon at the same time Sydney; Moon and Sun at 2024-04-08T18:36Z Austin. Tests compare spherical angular separation, fractional illumination and observer distance. Maximum position separation: 0.004300169442532115 degrees, a sampled result, not an all-date bound.

work/generate-solar-tests.py converts fixtures to SolarReferenceData.java. Runtime date selection is 1900–2100; stars use a separate simplified precession model documented in astronomy-sources.md. Atmospheric refraction, weather and local terrain are not modeled. Heading accuracy depends on phone sensors and calibration.
