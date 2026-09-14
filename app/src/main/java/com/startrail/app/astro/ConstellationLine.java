package com.startrail.app.astro;

/** One display line between two catalogue stars. Lines are illustrative, not IAU boundaries. */
public final class ConstellationLine {
    public final String constellation;
    public final String fromStarId;
    public final String toStarId;
    public final String fromId;
    public final String toId;

    public ConstellationLine(String constellation, String fromStarId, String toStarId) {
        this.constellation = constellation;
        this.fromStarId = fromStarId;
        this.toStarId = toStarId;
        this.fromId = fromStarId;
        this.toId = toStarId;
    }
}
