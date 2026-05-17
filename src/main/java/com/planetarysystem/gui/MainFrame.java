package com.planetarysystem.gui;

import com.planetarysystem.core.*;
import com.planetarysystem.db.DatabaseManager;
import com.planetarysystem.physics.OrbitalMechanics;
import com.planetarysystem.routing.SatelliteRouter;

import javax.swing.*;

import java.awt.*;
import java.awt.event.*;

import java.sql.SQLException;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;


/** Cửa sổ chính của ứng dụng */
public class MainFrame extends JFrame {

    private DatabaseManager db;
    private Planet currentPlanet = Planet.EARTH;

    private Planet3DRenderer renderer;
    private SatelliteManagerPanel satPanel;
    private RoutingPanel routingPanel;

    private JLabel lblSatCount, lblDbStatus, lblAnimStatus, lblFps;
    private JToggleButton btnAnimate;
    private Timer animTimer;
    private boolean animating = false;
    private double simSpeed = 60.0; // Số giây cho mỗi tick khung hình (mặc định 1 phút/tick)
    private JSlider speedSlider;
    private JLabel lblSpeedValue;

    private long lastFrameTime = System.currentTimeMillis();
    private int frameCount = 0;

    public MainFrame() {
        super("Hệ thống Vệ tinh Trái Đất — Mô phỏng 3D");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1440, 880);
        setMinimumSize(new Dimension(1100, 700));
        setLocationRelativeTo(null);

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        try {
            db = DatabaseManager.getInstance();
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(null, "DB Error: " + e.getMessage());
            System.exit(1);
        }

        initComponents();
        refreshAll();
        startAnimation();
    }

    private void initComponents() {
        setLayout(new BorderLayout(0, 0));
        getContentPane().setBackground(new Color(5, 8, 20));

        // ── Thanh công cụ (Top bar) ──
        add(buildTopBar(), BorderLayout.NORTH);

        // ── Trung tâm: Khung nhìn 3D + Bảng điều khiển dạng tab ──
        JSplitPane centerSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        centerSplit.setDividerLocation(900);
        centerSplit.setDividerSize(4);
        centerSplit.setBackground(new Color(10, 14, 28));

        renderer = new Planet3DRenderer(currentPlanet);
        renderer.setPreferredSize(new Dimension(720, 600));
        JPanel rendererWrap = new JPanel(new BorderLayout());
        rendererWrap.setPreferredSize(new Dimension(720, 600));
        rendererWrap.setMinimumSize(new Dimension(400, 400));
        rendererWrap.setBackground(new Color(5, 8, 20));
        rendererWrap.add(renderer, BorderLayout.CENTER);

        satPanel     = new SatelliteManagerPanel(db, currentPlanet);
        routingPanel = new RoutingPanel(db, currentPlanet, null);

        // Khi vệ tinh thay đổi trong CSDL, làm mới tất cả
        satPanel.addPropertyChangeListener("satellites_changed", e -> refreshAll());

        // Khi đường đi được tính toán, đánh dấu đường đi + tất cả các cạnh trên khung nhìn 3D
        routingPanel.addPropertyChangeListener("route_computed", e -> {
            SatelliteRouter.RouteResult result = (SatelliteRouter.RouteResult) e.getNewValue();
            if (result != null && result.found) {
                // Xây dựng định tuyến để lấy toàn bộ đồ thị đỉnh/cạnh phục vụ hiển thị trực quan
                try {
                    List<SatelliteObject> sats = db.getSatellitesByPlanet(currentPlanet.getName());
                    List<GroundStation> gs     = db.getGroundStationsByPlanet(currentPlanet.getName());
                    SatelliteRouter router = new SatelliteRouter(currentPlanet, sats, gs);
                    router.buildGraph();
                    renderer.setRoute(result.path, null, null);
                } catch (SQLException ex) {
                    renderer.setRoute(result.path, null, null);
                }
            }
        });
        routingPanel.addPropertyChangeListener("route_cleared", e -> renderer.clearRoute());

        JTabbedPane tabs = new JTabbedPane();
        tabs.setBackground(new Color(20, 25, 45));
        tabs.setForeground(new Color(150, 200, 255));
        tabs.setFont(new Font("Segoe UI", Font.BOLD, 13));
        tabs.addTab("Vệ tinh",      satPanel);
        tabs.addTab("Định tuyến",    routingPanel);

        centerSplit.setLeftComponent(rendererWrap);
        centerSplit.setRightComponent(tabs);
        centerSplit.setResizeWeight(0.5);
        centerSplit.setDividerLocation(720);
        add(centerSplit, BorderLayout.CENTER);

        // ── Thanh trạng thái ──
        add(buildStatusBar(), BorderLayout.SOUTH);
    }

    private JPanel buildTopBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 8));
        bar.setBackground(new Color(15, 20, 40));
        bar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(40, 60, 120)));

        JLabel title = new JLabel("Hệ thống Vệ tinh Trái Đất");
        title.setFont(new Font("Segoe UI", Font.BOLD, 18));
        title.setForeground(new Color(150, 200, 255));

        btnAnimate = new JToggleButton("Chạy hiệu ứng");
        btnAnimate.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnAnimate.setBackground(new Color(40, 120, 60));
        btnAnimate.setForeground(Color.WHITE);
        btnAnimate.setFocusPainted(false);
        btnAnimate.setBorderPainted(false);
        btnAnimate.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnAnimate.addActionListener(e -> toggleAnimation());

        JButton btnRefresh = topBtn("Làm mới", new Color(40, 80, 140));
        btnRefresh.addActionListener(e -> refreshAll());

        // Thanh trượt tốc độ
        speedSlider = new JSlider(1, 3600, 60);
        speedSlider.setPreferredSize(new Dimension(150, 20));
        speedSlider.setBackground(new Color(15, 20, 40));
        speedSlider.addChangeListener(e -> {
            simSpeed = speedSlider.getValue();
            lblSpeedValue.setText(String.format("%.0fs/tick", simSpeed));
        });

        lblSpeedValue = new JLabel("60s/tick");
        lblSpeedValue.setForeground(new Color(120, 160, 200));
        lblSpeedValue.setFont(new Font("Segoe UI", Font.BOLD, 11));

        bar.add(title);
        bar.add(Box.createHorizontalStrut(20));
        bar.add(Box.createHorizontalStrut(10));
        bar.add(btnAnimate);
        bar.add(btnRefresh);
        bar.add(Box.createHorizontalStrut(16));
        bar.add(styledLabel("Tốc độ:"));
        bar.add(speedSlider);
        bar.add(lblSpeedValue);

        return bar;
    }



    private JPanel buildStatusBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 4));
        bar.setBackground(new Color(12, 16, 30));
        bar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(30, 50, 100)));

        lblSatCount  = statusLabel("Vệ tinh: 0");
        lblDbStatus  = statusLabel("CSDL: ✓ Đã kết nối");
        lblDbStatus.setForeground(new Color(80, 220, 80));
        lblAnimStatus = statusLabel("Hiệu ứng: TẮT");
        lblFps        = statusLabel("FPS: –");

        bar.add(lblSatCount);
        bar.add(sep()); bar.add(lblDbStatus);
        bar.add(sep()); bar.add(lblAnimStatus);
        bar.add(sep()); bar.add(lblFps);
        bar.add(sep()); bar.add(statusLabel("Kéo để xoay | Cuộn để thu phóng | Nhấp vệ tinh để chọn"));
        return bar;
    }

    private void refreshAll() {
        try {
            List<SatelliteObject> sats = db.getSatellitesByPlanet(currentPlanet.getName());
            List<GroundStation>   gs   = db.getGroundStationsByPlanet(currentPlanet.getName());

            // Tính toán lại vận tốc
            for (SatelliteObject s : sats) {
                double v = OrbitalMechanics.circularOrbitalVelocity(currentPlanet, s.getAltitude());
                s.setOrbitalVelocity(v);
            }

            renderer.setSatellites(sats);
            renderer.setGroundStations(gs);
            satPanel.refreshTable();
            routingPanel.setSatellites(sats);
            routingPanel.refreshData();

            long commCount = sats.stream().filter(SatelliteObject::isCommunicationSatellite).count();
            lblSatCount.setText(String.format(
                "Vệ tinh: %d  (Viễn thông: %d)  |  Trạm mặt đất: %d",
                sats.size(), commCount, gs.size()));

        } catch (SQLException e) {
            lblDbStatus.setText("Lỗi CSDL: " + e.getMessage());
            lblDbStatus.setForeground(new Color(255, 80, 80));
        }
    }

    private void toggleAnimation() {
        if (btnAnimate.isSelected()) {
            startAnimation();
        } else {
            stopAnimation();
        }
    }

    private void startAnimation() {
        if (animating) return;
        animating = true;
        btnAnimate.setSelected(true);
        btnAnimate.setText("Dừng");
        btnAnimate.setBackground(new Color(140, 50, 50));
        lblAnimStatus.setText("Hiệu ứng: BẬT");
        lblAnimStatus.setForeground(new Color(80, 220, 80));

        animTimer = new Timer(true);
        animTimer.scheduleAtFixedRate(new TimerTask() {
            @Override public void run() {
                try {
                    List<SatelliteObject> sats = db.getSatellitesByPlanet(currentPlanet.getName());
                    for (SatelliteObject s : sats) {
                        OrbitalMechanics.updateOrbitAngle(s, currentPlanet, simSpeed);
                    }
                    SwingUtilities.invokeLater(() -> {
                        renderer.updatePlanetRotation(simSpeed);
                        renderer.setSatellites(sats);
                        renderer.repaint();
                        // Bộ đếm FPS (Khung hình/giây)
                        frameCount++;
                        long now = System.currentTimeMillis();
                        if (now - lastFrameTime >= 1000) {
                            lblFps.setText("FPS: " + frameCount);
                            frameCount = 0;
                            lastFrameTime = now;
                        }
                    });
                } catch (SQLException e) { /* bỏ qua lỗi trong vòng lặp hiệu ứng */ }
            }
        }, 0, 50); // ~20 fps
    }

    private void stopAnimation() {
        animating = false;
        if (animTimer != null) { animTimer.cancel(); animTimer = null; }
        btnAnimate.setSelected(false);
        btnAnimate.setText("Chạy hiệu ứng");
        btnAnimate.setBackground(new Color(40, 120, 60));
        lblAnimStatus.setText("Hiệu ứng: TẮT");
        lblAnimStatus.setForeground(new Color(150, 150, 150));
        lblFps.setText("FPS: –");
    }


    // ── Các hàm hỗ trợ giao diện ──
    private JButton topBtn(String txt, Color bg) {
        JButton b = new JButton(txt);
        b.setBackground(bg); b.setForeground(Color.WHITE);
        b.setFont(new Font("Segoe UI", Font.BOLD, 12));
        b.setFocusPainted(false); b.setBorderPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { b.setBackground(bg.brighter()); }
            @Override public void mouseExited(MouseEvent e)  { b.setBackground(bg); }
        });
        return b;
    }
    private JLabel statusLabel(String t) {
        JLabel l = new JLabel(t);
        l.setForeground(new Color(140, 170, 210));
        l.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        return l;
    }
    private JLabel styledLabel(String t) {
        JLabel l = new JLabel(t);
        l.setForeground(new Color(150, 180, 220));
        l.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        return l;
    }
    private JSeparator sep() {
        JSeparator s = new JSeparator(JSeparator.VERTICAL);
        s.setPreferredSize(new Dimension(1, 18));
        s.setForeground(new Color(50, 70, 120));
        return s;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new MainFrame().setVisible(true));
    }
}
