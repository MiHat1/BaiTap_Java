package com.planetarysystem.core;

/**
 * Đại diện cho một vệ tinh hoặc đối tượng quay quanh một hành tinh.
 * Lưu trữ vị trí theo tọa độ địa lý (vĩ độ, kinh độ, độ cao).
 */
public class SatelliteObject {
    private int id;
    private String name;
    private String type;             // "comm" (viễn thông), "weather" (thời tiết), "nav" (điều hướng), v.v.
    private String planetName;

    // Vị trí địa lý
    private double latitude;        // độ, từ -90 đến +90
    private double longitude;       // độ, từ -180 đến +180
    private double altitude;        // mét (cao hơn bề mặt)

    // Vận tốc quỹ đạo được tính toán (m/s)
    private double orbitalVelocity;

    // Độ nghiêng quỹ đạo (tính bằng độ so với mặt phẳng xích đạo)
    private double inclination;

    // Góc hiện tại trên quỹ đạo (radian), dùng cho hiệu ứng xoay
    private double orbitAngle;

    // Tầm phủ sóng viễn thông (mét) - dùng cho liên lạc vệ tinh
    private double commRange;

    // Đây có phải là vệ tinh viễn thông không?
    private boolean isCommunicationSatellite;

    public SatelliteObject() {}

    public SatelliteObject(String name, String type, String planetName,
                           double latitude, double longitude, double altitude,
                           double inclination, boolean isCommunicationSatellite, double commRange) {
        this.name = name;
        this.type = type;
        this.planetName = planetName;
        this.latitude = latitude;
        this.longitude = longitude;
        this.altitude = altitude;
        this.inclination = inclination;
        this.isCommunicationSatellite = isCommunicationSatellite;
        this.commRange = commRange;
        this.orbitAngle = Math.toRadians(longitude); // khởi tạo góc từ kinh độ
    }

    // Getters và Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getPlanetName() { return planetName; }
    public void setPlanetName(String planetName) { this.planetName = planetName; }

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    public double getAltitude() { return altitude; }
    public void setAltitude(double altitude) { this.altitude = altitude; }

    public double getOrbitalVelocity() { return orbitalVelocity; }
    public void setOrbitalVelocity(double orbitalVelocity) { this.orbitalVelocity = orbitalVelocity; }

    public double getInclination() { return inclination; }
    public void setInclination(double inclination) { this.inclination = inclination; }

    public double getOrbitAngle() { return orbitAngle; }
    public void setOrbitAngle(double orbitAngle) { this.orbitAngle = orbitAngle; }

    public double getCommRange() { return commRange; }
    public void setCommRange(double commRange) { this.commRange = commRange; }

    public boolean isCommunicationSatellite() { return isCommunicationSatellite; }
    public void setCommunicationSatellite(boolean communicationSatellite) {
        isCommunicationSatellite = communicationSatellite;
    }

    /**
     * Trả về vị trí không gian 3D Cartesian của vệ tinh trong hệ quy chiếu trung tâm hành tinh.
     * X: hướng về 0°kinh độ/0°vĩ độ, Y: hướng về Bắc, Z: hướng về 90° Đông
     */
    public double[] getCartesianPosition(double planetRadius) {
        double r = planetRadius + altitude;
        double latRad = Math.toRadians(latitude);
        double lonRad = Math.toRadians(longitude);
        double x = r * Math.cos(latRad) * Math.sin(lonRad);
        double y = r * Math.sin(latRad);
        double z = r * Math.cos(latRad) * Math.cos(lonRad);
        return new double[]{x, y, z};
    }

    @Override
    public String toString() {
        return String.format("[%d] %s (%s) lat=%.1f° lon=%.1f° alt=%.0fkm v=%.0fm/s",
            id, name, type, latitude, longitude, altitude / 1000, orbitalVelocity);
    }
}
