# 3.9 observation calculations

Primary definitions: [US Naval Observatory — Rise, Set, and Twilight Definitions](https://aa.usno.navy.mil/faq/RST_defs), consulted 2026-09-11. Conventional solar/lunar rise/set uses the upper limb and atmospheric refraction; this app deliberately labels **geometric centre crossing altitude 0 degrees**, consistent with its non-refracted sky map, rather than claiming conventional sunrise/sunset times. Astronomical night uses a solar altitude at or below -18 degrees.

Positions use the existing Astronomy Engine 2.1.19 (see solar-sources.md) and the app's IAU1976-precessed J2000 star path. Upper transit is hour angle zero, not simply the highest curve sample. For solar-system targets the code pairs equator-of-date RA with the engine's apparent sidereal time; fixed stars use the existing GMST/precession path. Tests compare solar and lunar transit with the engine's independent hour-angle search API.

The rolling window starts at the selected Instant and extends 24 hours, clipped at the app's supported maximum date. Samples are 10 minutes apart; detected altitude zero and upper-transit crossings are bisected to a bracket <=30 seconds. This is numerical solver resolution, not a promise of physical accuracy. Very short near-tangent horizon crossings between samples can be missed. Only the first rise, set and upper transit within the window is listed. Missing events do not assert permanent circumpolar visibility.

Time labels use the device timezone, shown explicitly. Daylight/astronomical darkness, target height and moonlight notes are planning context; no weather, extinction, local obstructions or measured light pollution is used. No new network requests or external image assets are introduced.

The observing list is separate from favorites and the observation journal. It stores stable target IDs and a manually managed completed flag across dates. It is not included in journal JSON export.
