package com.planetarysystem.physics;

import com.planetarysystem.core.Planet;
import com.planetarysystem.core.SatelliteObject;

/**
 * Động cơ vật lý để tính toán cơ học quỹ đạo.
 * 
 * Các công thức chính:
 *   - Vận tốc quỹ đạo:  v = sqrt(GM / r)
 *   - Chu kỳ quỹ đạo:    T = 2π * sqrt(r³ / GM)
 *   - Vận tốc vũ trụ (vận tốc thoát):   v_e = sqrt(2GM / r)
 *   - Chuyển đạo Hohmann:  dùng để thay đổi quỹ đạo
 */
public class OrbitalMechanics {

    /**
     * Tính toán vận tốc quỹ đạo tròn cho một vệ tinh ở độ cao cho trước.
     *
     * @param planet   Hành tinh đang được quay quanh
     * @param altitude Độ cao so với bề mặt tính bằng mét
     * @return Vận tốc quỹ đạo tính bằng m/s
     */
    public static double circularOrbitalVelocity(Planet planet, double altitude) {
        double r = planet.getRadius() + altitude;
        return Math.sqrt(planet.getMu() / r);
    }

    /**
     * Tính toán chu kỳ quỹ đạo cho một quỹ đạo tròn.
     *
     * @param planet   Hành tinh đang được quay quanh
     * @param altitude Độ cao so với bề mặt tính bằng mét
     * @return Chu kỳ quỹ đạo tính bằng giây
     */
    public static double orbitalPeriod(Planet planet, double altitude) {
        double r = planet.getRadius() + altitude;
        return 2 * Math.PI * Math.sqrt((r * r * r) / planet.getMu());
    }

    /**
     * Tính toán vận tốc vũ trụ (vận tốc thoát) khỏi hành tinh ở độ cao cho trước.
     *
     * @param planet   Hành tinh
     * @param altitude Độ cao so với bề mặt tính bằng mét
     * @return Vận tốc thoát tính bằng m/s
     */
    public static double escapeVelocity(Planet planet, double altitude) {
        double r = planet.getRadius() + altitude;
        return Math.sqrt(2 * planet.getMu() / r);
    }

    /**
     * Kiểm tra xem vệ tinh có ở quỹ đạo ổn định không (không bị rơi rớt).
     * Một quỹ đạo ổn định yêu cầu độ cao trên ~100km đối với Trái Đất (đường Karman).
     * Độ cao xấp xỉ > 100,000m.
     *
     * @param planet   Hành tinh
     * @param altitude Độ cao tính bằng mét
     * @return true nếu quỹ đạo được coi là ổn định
     */
    public static boolean isStableOrbit(Planet planet, double altitude) {
        // Độ cao ổn định tối thiểu = khoảng 1.5% bán kính hành tinh
        double minAlt = planet.getRadius() * 0.015;
        return altitude >= minAlt;
    }

    /**
     * Tính toán vận tốc góc (rad/s) của một quỹ đạo tròn.
     *
     * @param planet   Hành tinh
     * @param altitude Độ cao tính bằng mét
     * @return Vận tốc góc tính bằng rad/s
     */
    public static double angularVelocity(Planet planet, double altitude) {
        double r = planet.getRadius() + altitude;
        return Math.sqrt(planet.getMu() / (r * r * r));
    }

    /**
     * Áp dụng cơ học quỹ đạo để cập nhật góc quỹ đạo của vệ tinh.
     * Điều này mô phỏng sự di chuyển của vệ tinh dọc theo quỹ đạo của nó.
     *
     * @param sat        Vệ tinh cần cập nhật
     * @param planet     Hành tinh đang được quay quanh
     * @param deltaTimeSec Bước thời gian tính bằng giây
     */
    public static void updateOrbitAngle(SatelliteObject sat, Planet planet, double deltaTimeSec) {
        double omega = angularVelocity(planet, sat.getAltitude());
        double newAngle = sat.getOrbitAngle() + omega * deltaTimeSec;
        if (newAngle > 2 * Math.PI) {
            newAngle -= 2 * Math.PI;
        }
        sat.setOrbitAngle(newAngle);

        double incRad = Math.toRadians(sat.getInclination());
        double theta = newAngle;

        // Quỹ đạo không nghiêng: x = sin(theta), y = 0, z = cos(theta)
        // Xoay quanh trục X một góc incRad
        double x = Math.sin(theta);
        double y = Math.cos(theta) * Math.sin(incRad);
        double z = Math.cos(theta) * Math.cos(incRad);

        double lat = Math.toDegrees(Math.asin(y));
        double lon = Math.toDegrees(Math.atan2(x, z));

        sat.setLongitude(lon);
        sat.setLatitude(lat);
    }

    /**
     * Định dạng dữ liệu quỹ đạo thành một chuỗi tóm tắt dễ đọc cho người dùng.
     */
    public static String orbitalSummary(Planet planet, SatelliteObject sat) {
        double v = sat.getOrbitalVelocity();
        double T = orbitalPeriod(planet, sat.getAltitude());
        double ve = escapeVelocity(planet, sat.getAltitude());
        double r = planet.getRadius() + sat.getAltitude();

        int hours = (int) (T / 3600);
        int minutes = (int) ((T % 3600) / 60);
        int seconds = (int) (T % 60);

        return String.format(
            "Bán kính quỹ đạo: %.0f km\n" +
            "Vận tốc quỹ đạo: %.2f km/s\n" +
            "Vận tốc thoát: %.2f km/s\n" +
            "Chu kỳ quỹ đạo: %dh %dm %ds\n" +
            "Ổn định: %s",
            r / 1000,
            v / 1000,
            ve / 1000,
            hours, minutes, seconds,
            isStableOrbit(planet, sat.getAltitude()) ? "Có ✓" : "Không ✗"
        );
    }
}
