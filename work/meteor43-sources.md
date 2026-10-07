# Meteor shower calendar sources (4.3)

The catalogue is intentionally limited to calendar years 2026 and 2027. Its primary sources are
the International Meteor Organization (IMO) annual calendars:

- 2026 Meteor Shower Calendar: https://www.imo.net/files/meteor-shower/cal2026.pdf
- 2027 Meteor Shower Calendar: https://www.imo.net/resources/calendar/ShCal27s.pdf
- IMO calendar landing page: https://www.imo.net/resources/calendar/

The implementation carries eight established annual showers: Quadrantids (QUA), April Lyrids
(LYR), eta Aquariids (ETA), Southern delta Aquariids (SDA), Perseids (PER), Orionids (ORI),
Leonids (LEO), and Geminids (GEM). The solar longitude and the approximate J2000 radiant at
maximum come from the IMO working list and shower summaries. The 2026 calendar explicitly says
its maximum dates are accurate only for 2026; the 2027 PDF is therefore used for 2027 attribution.

`MeteorShowers43` does not store a made-up clock time. The IMO calendar defines every listed
solar longitude against equinox 2000.0. For each supported year the implementation therefore
uses Astronomy Engine 2.1.19's apparent geocentric Sun vector in the J2000 equatorial frame,
rotates it into the J2000 ecliptic frame, and solves for the listed longitude near the calendar
date. Astronomy Engine's `searchSunLongitude` is deliberately not used because it solves true
ecliptic longitude of date; by 2026/2027 that different equinox would move these results by
about nine hours.

The eight stored longitudes describe the customary maxima in the IMO Working List and are
shared between these two annual catalogues. They are not claims that the observed maximum is
identical every year, nor do they replace year-specific predictions for dust-trail encounters
or extra sub-peaks. The result is marked approximate. Meteor-stream structure can move or split
the observed maximum, and real counts depend on the year, site, radiant altitude, sky brightness,
weather, and observing method. The displayed 72-hour window is a planning window, not a
prediction that meteors will be visible throughout it. Radiants also drift over each shower's
activity period, so the supplied J2000 coordinates are approximate peak coordinates.

The catalogue deliberately omits a numeric ZHR. IMO ZHR values describe standardized ideal
conditions and, for several showers, vary materially between returns; presenting one as a local
forecast would be misleading.
