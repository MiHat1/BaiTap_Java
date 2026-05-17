package com.planetarysystem.core;

/**
 * Đại diện cho một hành tinh với các thuộc tính vật lý.
 */
public class Planet {
    private final String name;
    private final double mass;          // kg
    private final double radius;        // mét
    private final double mu;            // Tham số hấp dẫn tiêu chuẩn (GM) tính bằng m³/s²
    private final String textureName;   // tên file texture trong thư mục resources/textures

    // Hằng số hấp dẫn G = 6.674e-11 N·m²/kg²
    public static final double G = 6.674e-11;

    // Các hành tinh đã được định nghĩa sẵn
    public static final Planet EARTH = new Planet(
        "Earth",
        5.972e24,
        6_371_000,
        "2k_earth_daymap.jpg"
    );

    public Planet(String name, double mass, double radius, String textureName) {
        this.name = name;
        this.mass = mass;
        this.radius = radius;
        this.mu = G * mass;
        this.textureName = textureName;
    }

    public String getName() { return name; }
    public double getMass() { return mass; }
    public double getRadius() { return radius; }
    public double getMu() { return mu; }
    public String getTextureName() { return textureName; }

    @Override
    public String toString() { return name; }
}
