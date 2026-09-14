package com.startrail.app.astro;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Immutable J2000 catalogue entry. Right ascension is in hours, declination in degrees. */
public final class Star {
    public final String id;
    public final String name;
    public final String chineseName;
    public final String constellation;
    public final String description;
    /** Common Chinese/English search terms, excluding the primary names above. */
    public final List<String> aliases;
    public final double raHours;
    public final double decDeg;
    public final double decDegrees;
    public final double magnitude;

    public Star(String id, String name, String chineseName, String constellation,
                double raHours, double decDegrees, double magnitude) {
        this(id, name, chineseName, constellation, raHours, decDegrees, magnitude, "");
    }

    public Star(String id, String name, String chineseName, String constellation,
                double raHours, double decDegrees, double magnitude, String description) {
        this(id, name, chineseName, constellation, raHours, decDegrees, magnitude, description, new String[0]);
    }

    public Star(String id, String name, String chineseName, String constellation,
                double raHours, double decDegrees, double magnitude, String description, String... aliases) {
        this.id = id;
        this.name = name;
        this.chineseName = chineseName;
        this.constellation = constellation;
        this.description = description;
        this.aliases = Collections.unmodifiableList(Arrays.asList(aliases.clone()));
        this.raHours = raHours;
        this.decDeg = decDegrees;
        this.decDegrees = decDegrees;
        this.magnitude = magnitude;
    }

    public String displayName() {
        return chineseName == null || chineseName.isEmpty() ? name : chineseName + " · " + name;
    }
}
