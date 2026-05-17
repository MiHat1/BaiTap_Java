package com.planetarysystem.db;

import com.planetarysystem.core.Planet;
import com.planetarysystem.core.SatelliteObject;
import com.planetarysystem.core.GroundStation;
import com.planetarysystem.physics.OrbitalMechanics;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Quản lý cơ sở dữ liệu sử dụng SQLite.
 * Xử lý tất cả các thao tác CRUD cho vệ tinh và trạm mặt đất.
 */
public class DatabaseManager {

    private static final String DB_FILE = "planetary_system.db";
    private static DatabaseManager instance;
    private Connection connection;

    private DatabaseManager() throws SQLException {
        connect();
        initializeSchema();
    }

    public static synchronized DatabaseManager getInstance() throws SQLException {
        if (instance == null) {
            instance = new DatabaseManager();
        }
        return instance;
    }

    private void connect() throws SQLException {
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + DB_FILE);
            connection.setAutoCommit(true);
            // Kích hoạt chế độ WAL để tăng hiệu suất
            try (Statement st = connection.createStatement()) {
                st.execute("PRAGMA journal_mode=WAL");
                st.execute("PRAGMA foreign_keys=ON");
            }
        } catch (ClassNotFoundException e) {
            throw new SQLException("SQLite JDBC driver not found: " + e.getMessage());
        }
    }

    private void initializeSchema() throws SQLException {
        try (Statement st = connection.createStatement()) {
            // Bảng vệ tinh (satellites)
            st.execute("""
                CREATE TABLE IF NOT EXISTS satellites (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL,
                    type TEXT NOT NULL DEFAULT 'generic',
                    planet_name TEXT NOT NULL,
                    latitude REAL NOT NULL DEFAULT 0.0,
                    longitude REAL NOT NULL DEFAULT 0.0,
                    altitude REAL NOT NULL DEFAULT 500000,
                    inclination REAL NOT NULL DEFAULT 0.0,
                    orbital_velocity REAL NOT NULL DEFAULT 0.0,
                    is_comm_satellite INTEGER NOT NULL DEFAULT 0,
                    comm_range REAL NOT NULL DEFAULT 2000000,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
                )
            """);

            // Bảng trạm mặt đất (ground_stations)
            st.execute("""
                CREATE TABLE IF NOT EXISTS ground_stations (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL,
                    planet_name TEXT NOT NULL,
                    latitude REAL NOT NULL DEFAULT 0.0,
                    longitude REAL NOT NULL DEFAULT 0.0,
                    description TEXT DEFAULT '',
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
                )
            """);


        }
    }



    // ============ Thao tác CRUD cho Vệ tinh ============

    public void insertSatellite(SatelliteObject sat) throws SQLException {
        String sql = """
            INSERT INTO satellites (name, type, planet_name, latitude, longitude, altitude,
                inclination, orbital_velocity, is_comm_satellite, comm_range)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, sat.getName());
            ps.setString(2, sat.getType());
            ps.setString(3, sat.getPlanetName());
            ps.setDouble(4, sat.getLatitude());
            ps.setDouble(5, sat.getLongitude());
            ps.setDouble(6, sat.getAltitude());
            ps.setDouble(7, sat.getInclination());
            ps.setDouble(8, sat.getOrbitalVelocity());
            ps.setInt(9, sat.isCommunicationSatellite() ? 1 : 0);
            ps.setDouble(10, sat.getCommRange());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) sat.setId(keys.getInt(1));
            }
        }
    }

    public void updateSatellite(SatelliteObject sat) throws SQLException {
        String sql = """
            UPDATE satellites SET name=?, type=?, planet_name=?, latitude=?, longitude=?,
                altitude=?, inclination=?, orbital_velocity=?, is_comm_satellite=?, comm_range=?
            WHERE id=?
        """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, sat.getName());
            ps.setString(2, sat.getType());
            ps.setString(3, sat.getPlanetName());
            ps.setDouble(4, sat.getLatitude());
            ps.setDouble(5, sat.getLongitude());
            ps.setDouble(6, sat.getAltitude());
            ps.setDouble(7, sat.getInclination());
            ps.setDouble(8, sat.getOrbitalVelocity());
            ps.setInt(9, sat.isCommunicationSatellite() ? 1 : 0);
            ps.setDouble(10, sat.getCommRange());
            ps.setInt(11, sat.getId());
            ps.executeUpdate();
        }
    }

    public void deleteSatellite(int id) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM satellites WHERE id=?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    public List<SatelliteObject> getAllSatellites() throws SQLException {
        List<SatelliteObject> list = new ArrayList<>();
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM satellites ORDER BY planet_name, name")) {
            while (rs.next()) {
                SatelliteObject sat = new SatelliteObject();
                sat.setId(rs.getInt("id"));
                sat.setName(rs.getString("name"));
                sat.setType(rs.getString("type"));
                sat.setPlanetName(rs.getString("planet_name"));
                sat.setLatitude(rs.getDouble("latitude"));
                sat.setLongitude(rs.getDouble("longitude"));
                sat.setAltitude(rs.getDouble("altitude"));
                sat.setInclination(rs.getDouble("inclination"));
                sat.setOrbitalVelocity(rs.getDouble("orbital_velocity"));
                sat.setCommunicationSatellite(rs.getInt("is_comm_satellite") == 1);
                sat.setCommRange(rs.getDouble("comm_range"));
                sat.setOrbitAngle(Math.toRadians(rs.getDouble("longitude")));
                list.add(sat);
            }
        }
        return list;
    }

    public List<SatelliteObject> getSatellitesByPlanet(String planetName) throws SQLException {
        List<SatelliteObject> list = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM satellites WHERE planet_name=? ORDER BY name")) {
            ps.setString(1, planetName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    SatelliteObject sat = new SatelliteObject();
                    sat.setId(rs.getInt("id"));
                    sat.setName(rs.getString("name"));
                    sat.setType(rs.getString("type"));
                    sat.setPlanetName(rs.getString("planet_name"));
                    sat.setLatitude(rs.getDouble("latitude"));
                    sat.setLongitude(rs.getDouble("longitude"));
                    sat.setAltitude(rs.getDouble("altitude"));
                    sat.setInclination(rs.getDouble("inclination"));
                    sat.setOrbitalVelocity(rs.getDouble("orbital_velocity"));
                    sat.setCommunicationSatellite(rs.getInt("is_comm_satellite") == 1);
                    sat.setCommRange(rs.getDouble("comm_range"));
                    sat.setOrbitAngle(Math.toRadians(rs.getDouble("longitude")));
                    list.add(sat);
                }
            }
        }
        return list;
    }

    // ============ Thao tác CRUD cho Trạm mặt đất ============

    public void insertGroundStation(GroundStation gs) throws SQLException {
        String sql = """
            INSERT INTO ground_stations (name, planet_name, latitude, longitude, description)
            VALUES (?, ?, ?, ?, ?)
        """;
        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, gs.getName());
            ps.setString(2, gs.getPlanetName());
            ps.setDouble(3, gs.getLatitude());
            ps.setDouble(4, gs.getLongitude());
            ps.setString(5, gs.getDescription());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) gs.setId(keys.getInt(1));
            }
        }
    }

    public void updateGroundStation(GroundStation gs) throws SQLException {
        String sql = """
            UPDATE ground_stations SET name=?, planet_name=?, latitude=?, longitude=?, description=?
            WHERE id=?
        """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, gs.getName());
            ps.setString(2, gs.getPlanetName());
            ps.setDouble(3, gs.getLatitude());
            ps.setDouble(4, gs.getLongitude());
            ps.setString(5, gs.getDescription());
            ps.setInt(6, gs.getId());
            ps.executeUpdate();
        }
    }

    public void deleteGroundStation(int id) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM ground_stations WHERE id=?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    public List<GroundStation> getAllGroundStations() throws SQLException {
        List<GroundStation> list = new ArrayList<>();
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM ground_stations ORDER BY planet_name, name")) {
            while (rs.next()) {
                GroundStation gs = new GroundStation();
                gs.setId(rs.getInt("id"));
                gs.setName(rs.getString("name"));
                gs.setPlanetName(rs.getString("planet_name"));
                gs.setLatitude(rs.getDouble("latitude"));
                gs.setLongitude(rs.getDouble("longitude"));
                gs.setDescription(rs.getString("description"));
                list.add(gs);
            }
        }
        return list;
    }

    public List<GroundStation> getGroundStationsByPlanet(String planetName) throws SQLException {
        List<GroundStation> list = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM ground_stations WHERE planet_name=? ORDER BY name")) {
            ps.setString(1, planetName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    GroundStation gs = new GroundStation();
                    gs.setId(rs.getInt("id"));
                    gs.setName(rs.getString("name"));
                    gs.setPlanetName(rs.getString("planet_name"));
                    gs.setLatitude(rs.getDouble("latitude"));
                    gs.setLongitude(rs.getDouble("longitude"));
                    gs.setDescription(rs.getString("description"));
                    list.add(gs);
                }
            }
        }
        return list;
    }



    public Connection getConnection() { return connection; }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            System.err.println("Error closing DB: " + e.getMessage());
        }
    }
}
