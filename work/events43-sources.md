# CelestialEvents 4.3 calculation reference

`CelestialEvents` uses Astronomy Engine 2.1.19. Its public Kotlin/Java documentation is the
authoritative API reference:

- https://github.com/cosinekitty/astronomy/blob/master/source/kotlin/README.md

The engine supplies the four lunar-quarter times, lunar-eclipse type/peak/semi-durations,
global solar-eclipse peaks, location-specific solar-eclipse contacts, and aberration-corrected
geocentric vectors used here. The event search accepts a half-open peak interval `[start,end)`,
is limited to 366 days and years 1900--2100, and checks thread interruption during every
potentially long scan.

## Event interpretation

- Lunar eclipse contacts are reconstructed from Astronomy Engine's documented semi-durations
  around peak. “红月” is used only for `EclipseKind.Total`.
- A solar eclipse is searched globally first. If Astronomy Engine finds the same eclipse for
  the requested observer, the returned window and contacts are the actual local partial and,
  where applicable, total/annular contacts. Otherwise the global event remains in the catalogue
  with `local=false` and no contacts, so a planner cannot recommend it as a local phenomenon.
- Close approaches are minima of apparent angular separation, not right-ascension
  conjunctions. Moon/planet pairs are retained at 8 degrees or closer; planet/planet pairs at
  5 degrees or closer. A six-hour grid brackets local minima and 28 ternary refinements reduce
  the bracket to about 0.06 seconds. The underlying ephemerides are precise, but these events
  remain marked `approximate=true` because a numerical minimum is being reported rather than a
  named exact astronomical event.
- Non-eclipse windows are peak plus/minus 18 hours; new moon uses plus/minus 36 hours. These
  windows communicate browsing/planning context, not visibility guarantees.

## Runtime reference checks

Direct Java 17 compilation against the cached 2.1.19 jar succeeded. A standalone runtime probe
returned:

- 2025-09-07 total lunar eclipse peak `18:11:41.502Z`, seven ordered contacts from
  `15:28:02.516Z` through `20:55:20.488Z`.
- 2024-04-08 Dallas total solar eclipse peak `18:42:37.111Z`, five ordered local contacts.
- The same 2024-04-08 eclipse at Sydney is `local=false` with no local contacts.
- The 2025-01-01 through 2025-04-01 sample produced 19 threshold-qualified angular minima;
  no catalogue entry is fabricated solely to satisfy a count.

The checked-in JUnit test covers those independent known dates, immutability/sorting, all four
quarter codes, range/location validation, interruption, and close-approach threshold semantics.
The root integration run used Gradle's bundled JUnit 4 jar. CelestialEventsTest plus EventObservationPlannerTest passed all 20 focused cases, including local-solar-peak query boundaries, lunar eclipses with the Sun below the horizon, and yearly close-approach recommendations retaining the angular threshold.
