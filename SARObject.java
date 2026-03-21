package org.ka2ddo.yaac.gui.tile;

import java.util.UUID;

/**
 * A SAR evidence/object marker placed on the map.
 */
public class SARObject {

    private String id;
    private String category;
    private String type;
    private double lat;
    private double lon;
    private String foundBy;
    private String notes;
    private long timestamp;

    public SARObject() {
        this.id = UUID.randomUUID().toString();
        this.timestamp = System.currentTimeMillis();
    }

    public SARObject(String category, String type, double lat, double lon,
                     String foundBy, String notes) {
        this();
        this.category = category;
        this.type = type;
        this.lat = lat;
        this.lon = lon;
        this.foundBy = foundBy;
        this.notes = notes;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public double getLat() { return lat; }
    public void setLat(double lat) { this.lat = lat; }

    public double getLon() { return lon; }
    public void setLon(double lon) { this.lon = lon; }

    public String getFoundBy() { return foundBy; }
    public void setFoundBy(String foundBy) { this.foundBy = foundBy; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}
