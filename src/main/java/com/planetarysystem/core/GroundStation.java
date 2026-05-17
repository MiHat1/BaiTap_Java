package com.planetarysystem.core;

/**
 * Đại diện cho một trạm mặt đất hoặc điểm tham chiếu trên bề mặt hành tinh.
 */
public class GroundStation {
    private int id;
    private String name;
    private String planetName;
    private double latitude;   // độ
    private double longitude;  // độ
    private String description;

    public GroundStation() {}

    public GroundStation(String name, String planetName, double latitude, double longitude, String description) {
        this.name = name;
        this.planetName = planetName;
        this.latitude = latitude;
        this.longitude = longitude;
        this.description = description;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPlanetName() { return planetName; }
    public void setPlanetName(String planetName) { this.planetName = planetName; }

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    /**
     * Trả về vị trí bề mặt 3D (được chuẩn hóa theo bán kính 1 để hiển thị).
     */
    public double[] getSurfaceNormal() {
        double latRad = Math.toRadians(latitude);
        double lonRad = Math.toRadians(longitude);
        double x = Math.cos(latRad) * Math.sin(lonRad);
        double y = Math.sin(latRad);
        double z = Math.cos(latRad) * Math.cos(lonRad);
        return new double[]{x, y, z};
    }

    @Override
    public String toString() {
        return String.format("%s (%.1f°, %.1f°)", name, latitude, longitude);
    }
}
