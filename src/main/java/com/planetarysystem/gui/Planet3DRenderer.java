package com.planetarysystem.gui;

import com.planetarysystem.core.Planet;
import com.planetarysystem.core.SatelliteObject;
import com.planetarysystem.core.GroundStation;
import com.planetarysystem.routing.SatelliteRouter;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.List;

/**
 * Trình xem hành tinh 3D sử dụng phép chiếu hình cầu Java2D.
 * Ánh xạ kết cấu Equirectangular, quầng sáng khí quyển,
 * quỹ đạo vệ tinh, trạm mặt đất, hiển thị định tuyến.
 */
public class Planet3DRenderer extends JPanel {

    private Planet planet;
    private List<SatelliteObject> satellites = new ArrayList<>();
    private List<GroundStation> groundStations = new ArrayList<>();
    private List<SatelliteRouter.Node> routeNodes = new ArrayList<>();
    private List<SatelliteRouter.Edge> routeEdges = new ArrayList<>();
    private List<SatelliteRouter.Node> highlightedPath = new ArrayList<>();

    private BufferedImage texture;
    private BufferedImage renderedSphere;

    private double rotationY = 23.0;
    private double rotationX = -15.0;
    private double zoom = 1.0;

    private int lastMouseX, lastMouseY;
    private boolean isDragging = false;

    // Vệ tinh được chọn để hiển thị thông tin tooltip
    private SatelliteObject selectedSat = null;

    // Màu sắc
    private static final Color SAT_COMM = new Color(0, 255, 180);
    private static final Color GS_COLOR = new Color(255, 80, 80);
    private static final Color ROUTE_LINE = new Color(255, 255, 0, 220);
    private static final Color LINK_LINE = new Color(0, 255, 150, 100);

    public Planet3DRenderer(Planet planet) {
        this.planet = planet;
        setBackground(new Color(5, 5, 20));
        setPreferredSize(new Dimension(700, 700));
        setToolTipText("");
        loadTexture();
        setupMouseListeners();
    }

    private void loadTexture() {
        String[] paths = {
                "resources/textures/" + planet.getTextureName(),
                "textures/" + planet.getTextureName(),
                planet.getTextureName()
        };
        for (String path : paths) {
            File f = new File(path);
            if (f.exists()) {
                try {
                    texture = ImageIO.read(f);
                    invalidateCache();
                    return;
                } catch (IOException ignored) {
                }
            }
        }
        try (InputStream is = getClass().getResourceAsStream("/textures/" + planet.getTextureName())) {
            if (is != null) {
                texture = ImageIO.read(is);
                invalidateCache();
                return;
            }
        } catch (IOException ignored) {
        }
        texture = generateProceduralTexture(planet);
        invalidateCache();
    }

    private BufferedImage generateProceduralTexture(Planet p) {
        int w = 512, h = 256;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        Random rnd = new Random(p.getName().hashCode());
        Color base, land;
        switch (p.getName()) {
            case "Earth" -> {
                base = new Color(30, 80, 160);
                land = new Color(60, 130, 50);
            }
            case "Mars" -> {
                base = new Color(160, 70, 30);
                land = new Color(200, 110, 55);
            }
            case "Jupiter" -> {
                base = new Color(200, 160, 100);
                land = new Color(230, 190, 140);
            }
            default -> {
                base = new Color(170, 170, 170);
                land = new Color(210, 210, 210);
            }
        }
        g.setColor(base);
        g.fillRect(0, 0, w, h);
        for (int i = 0; i < 35; i++) {
            int x = rnd.nextInt(w), y = rnd.nextInt(h), sw = 20 + rnd.nextInt(90), sh = 10 + rnd.nextInt(45);
            g.setColor(new Color(land.getRed(), land.getGreen(), land.getBlue(), 130 + rnd.nextInt(120)));
            g.fillOval(x, y, sw, sh);
        }
        if (p.getName().equals("Jupiter")) {
            for (int y = 0; y < h; y += 15 + rnd.nextInt(20)) {
                g.setColor(new Color(200 + rnd.nextInt(40), 130 + rnd.nextInt(50), 70 + rnd.nextInt(40), 130));
                g.fillRect(0, y, w, 6 + rnd.nextInt(14));
            }
        }
        if (p.getName().equals("Earth")) {
            // chỏm băng ở hai cực
            g.setColor(new Color(240, 248, 255, 180));
            g.fillRect(0, 0, w, 18);
            g.fillRect(0, h - 18, w, 18);
        }
        g.dispose();
        return img;
    }

    private void invalidateCache() {
        renderedSphere = null;
        repaint();
    }

    private void setupMouseListeners() {
        MouseAdapter ma = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                lastMouseX = e.getX();
                lastMouseY = e.getY();
                isDragging = false;
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (!isDragging) {
                    // Click chuột → thử chọn vệ tinh
                    trySelectSatellite(e.getX(), e.getY());
                }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                isDragging = true;
                int dx = e.getX() - lastMouseX, dy = e.getY() - lastMouseY;
                rotationY += dx * 0.5;
                rotationX += dy * 0.3;
                rotationX = Math.max(-80, Math.min(80, rotationX));
                lastMouseX = e.getX();
                lastMouseY = e.getY();
                invalidateCache();
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                zoom -= e.getPreciseWheelRotation() * 0.08;
                zoom = Math.max(0.4, Math.min(3.0, zoom));
                invalidateCache();
            }

            @Override
            public void mouseMoved(MouseEvent e) {
            }
        };
        addMouseListener(ma);
        addMouseMotionListener(ma);
        addMouseWheelListener(ma);
    }

    private void trySelectSatellite(int mx, int my) {
        int cx = getWidth() / 2, cy = getHeight() / 2;
        int radius = (int) (Math.min(getWidth(), getHeight()) * 0.45 * zoom);
        SatelliteObject found = null;
        double bestDist = 14;
        for (SatelliteObject sat : satellites) {
            double[] pos = sat.getCartesianPosition(planet.getRadius());
            double nx = pos[0] / planet.getRadius();
            double ny = pos[1] / planet.getRadius();
            double nz = pos[2] / planet.getRadius();
            double rotYRad = Math.toRadians(rotationY), rotXRad = Math.toRadians(rotationX);
            double nx2 = nx * Math.cos(rotYRad) + nz * Math.sin(rotYRad);
            double nz2 = -nx * Math.sin(rotYRad) + nz * Math.cos(rotYRad);
            double ny2 = ny * Math.cos(rotXRad) - nz2 * Math.sin(rotXRad);
            double nz3 = ny * Math.sin(rotXRad) + nz2 * Math.cos(rotXRad);
            if (nz3 < -0.2)
                continue;
            int sx = (int) (cx + nx2 * radius), sy = (int) (cy - ny2 * radius);
            double d = Math.hypot(mx - sx, my - sy);
            if (d < bestDist) {
                bestDist = d;
                found = sat;
            }
        }
        selectedSat = found;
        repaint();
    }

    // ────────────────── Quá trình kết xuất (Rendering) ──────────────────

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        int cx = getWidth() / 2, cy = getHeight() / 2;
        int radius = (int) (Math.min(getWidth(), getHeight()) * 0.45 * zoom);

        drawStarfield(g);
        drawAtmosphere(g, cx, cy, radius);
        drawPlanetSphere(g, cx, cy, radius);
        drawSatelliteOrbits(g, cx, cy, radius);
        drawCommLinks(g, cx, cy, radius);
        drawRouteHighlight(g, cx, cy, radius);
        drawGroundStations(g, cx, cy, radius);
        drawSatellites(g, cx, cy, radius);
        drawPlanetLabel(g, cx, cy, radius);
        if (selectedSat != null)
            drawSelectedInfo(g, cx, cy, radius);
    }

    private void drawStarfield(Graphics2D g) {
        Random rnd = new Random(1337);
        for (int i = 0; i < 250; i++) {
            int x = rnd.nextInt(getWidth()), y = rnd.nextInt(getHeight());
            int s = rnd.nextInt(2) + 1;
            int bright = 160 + rnd.nextInt(95);
            g.setColor(new Color(bright, bright, bright, 140 + rnd.nextInt(115)));
            g.fillOval(x, y, s, s);
        }
    }

    private void drawAtmosphere(Graphics2D g, int cx, int cy, int radius) {
        int glow = (int) (radius * 1.13);
        RadialGradientPaint atm = new RadialGradientPaint(
                new Point2D.Float(cx, cy), glow,
                new float[] { 0.82f, 1.0f },
                new Color[] { new Color(80, 140, 255, 0), new Color(80, 160, 255, 100) });
        g.setPaint(atm);
        g.fillOval(cx - glow, cy - glow, glow * 2, glow * 2);
    }

    private void drawPlanetSphere(Graphics2D g, int cx, int cy, int radius) {
        if (texture == null)
            return;
        if (renderedSphere == null || renderedSphere.getWidth() != radius * 2) {
            renderedSphere = renderSphereTexture(radius);
        }
        g.drawImage(renderedSphere, cx - radius, cy - radius, null);

        // Đổ bóng mặt tối
        RadialGradientPaint shade = new RadialGradientPaint(
                new Point2D.Float(cx + radius * 0.28f, cy - radius * 0.18f), radius * 1.1f,
                new float[] { 0f, 0.52f, 1f },
                new Color[] { new Color(0, 0, 0, 0), new Color(0, 0, 0, 20), new Color(0, 0, 0, 210) });
        g.setPaint(shade);
        Shape clip = new Ellipse2D.Float(cx - radius, cy - radius, radius * 2, radius * 2);
        g.setClip(clip);
        g.fillOval(cx - radius, cy - radius, radius * 2, radius * 2);
        g.setClip(null);

        // Ánh sáng phản chiếu (Specular)
        RadialGradientPaint spec = new RadialGradientPaint(
                new Point2D.Float(cx - radius * 0.32f, cy - radius * 0.38f), radius * 0.55f,
                new float[] { 0f, 1f },
                new Color[] { new Color(255, 255, 255, 70), new Color(255, 255, 255, 0) });
        g.setPaint(spec);
        g.setClip(clip);
        g.fillOval(cx - radius, cy - radius, radius * 2, radius * 2);
        g.setClip(null);
    }

    private BufferedImage renderSphereTexture(int radius) {
        int size = radius * 2;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        int txW = texture.getWidth(), txH = texture.getHeight();
        double rotYRad = Math.toRadians(rotationY), rotXRad = Math.toRadians(rotationX);
        double cosX = Math.cos(rotXRad), sinX = Math.sin(rotXRad);
        double cosY = Math.cos(rotYRad), sinY = Math.sin(rotYRad);
        for (int py = 0; py < size; py++) {
            for (int px = 0; px < size; px++) {
                double nx = (px - radius) / (double) radius;
                double ny = -(py - radius) / (double) radius; // Sửa lỗi trục Y bị ngược
                double nz2 = 1 - nx * nx - ny * ny;
                if (nz2 < 0)
                    continue;
                double nz = Math.sqrt(nz2);

                // Xoay ngược
                double ny_local = ny * cosX + nz * sinX;
                double nz2_temp = -ny * sinX + nz * cosX;

                double nx_local = nx * cosY - nz2_temp * sinY;
                double nz_local = nx * sinY + nz2_temp * cosY;

                double lat = Math.asin(Math.max(-1, Math.min(1, ny_local)));
                double lon = Math.atan2(nx_local, nz_local);

                double u = (lon / (2 * Math.PI) + 0.5) % 1.0;
                if (u < 0)
                    u += 1;
                double v = 0.5 - lat / Math.PI;

                int tx = (int) (u * (txW - 1)), ty = (int) (v * (txH - 1));
                tx = Math.max(0, Math.min(txW - 1, tx));
                ty = Math.max(0, Math.min(txH - 1, ty));
                img.setRGB(px, py, texture.getRGB(tx, ty) | 0xFF000000);
            }
        }
        return img;
    }

    private double[] project(double lat, double lon, double altitude,
            int cx, int cy, int radius, double planetRadius) {
        double latRad = Math.toRadians(lat), lonRad = Math.toRadians(lon);
        double r = 1.0 + altitude / planetRadius;
        double nx = r * Math.cos(latRad) * Math.sin(lonRad);
        double ny = r * Math.sin(latRad);
        double nz = r * Math.cos(latRad) * Math.cos(lonRad);
        double rotYRad = Math.toRadians(rotationY), rotXRad = Math.toRadians(rotationX);
        double nx2 = nx * Math.cos(rotYRad) + nz * Math.sin(rotYRad);
        double nz2 = -nx * Math.sin(rotYRad) + nz * Math.cos(rotYRad);
        double ny2 = ny * Math.cos(rotXRad) - nz2 * Math.sin(rotXRad);
        double nz3 = ny * Math.sin(rotXRad) + nz2 * Math.cos(rotXRad);
        return new double[] { cx + nx2 * radius, cy - ny2 * radius, nz3 };
    }

    private void drawSatelliteOrbits(Graphics2D g, int cx, int cy, int radius) {
        g.setStroke(new BasicStroke(1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                1, new float[] { 4, 4 }, 0));
        Set<String> drawn = new HashSet<>();
        for (SatelliteObject sat : satellites) {
            String key = String.format("%.0f_%.1f", sat.getAltitude(), sat.getInclination());
            if (drawn.contains(key))
                continue;
            drawn.add(key);
            g.setColor(new Color(0, 255, 180, 45));
            int pts = 120;
            int[] xs = new int[pts], ys = new int[pts];
            boolean[] vis = new boolean[pts];
            for (int i = 0; i < pts; i++) {
                double theta = 2 * Math.PI * i / pts;
                double incRad = Math.toRadians(sat.getInclination());
                double lat = Math.toDegrees(Math.asin(Math.sin(incRad) * Math.sin(theta)));
                double lon = Math.toDegrees(Math.atan2(Math.cos(incRad) * Math.sin(theta), Math.cos(theta)));
                double[] p = project(lat, lon, sat.getAltitude(), cx, cy, radius, planet.getRadius());
                xs[i] = (int) p[0];
                ys[i] = (int) p[1];
                vis[i] = p[2] > -0.3;
            }
            for (int i = 0; i < pts; i++) {
                int j = (i + 1) % pts;
                if (vis[i] && vis[j])
                    g.drawLine(xs[i], ys[i], xs[j], ys[j]);
            }
        }
        g.setStroke(new BasicStroke(1.5f));
    }

    private void drawCommLinks(Graphics2D g, int cx, int cy, int radius) {
        if (routeEdges.isEmpty())
            return;
        g.setColor(LINK_LINE);
        g.setStroke(new BasicStroke(1f));
        for (SatelliteRouter.Edge edge : routeEdges) {
            SatelliteRouter.Node a = findRouteNode(edge.fromId), b = findRouteNode(edge.toId);
            if (a == null || b == null)
                continue;
            double[] pa = projectNode(a, cx, cy, radius), pb = projectNode(b, cx, cy, radius);
            if (pa[2] > 0 && pb[2] > 0)
                g.drawLine((int) pa[0], (int) pa[1], (int) pb[0], (int) pb[1]);
        }
        g.setStroke(new BasicStroke(1.5f));
    }

    private void drawRouteHighlight(Graphics2D g, int cx, int cy, int radius) {
        if (highlightedPath.size() < 2)
            return;
        // Vẽ quầng sáng
        g.setColor(new Color(255, 255, 0, 40));
        g.setStroke(new BasicStroke(7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 0; i < highlightedPath.size() - 1; i++) {
            double[] pa = projectNode(highlightedPath.get(i), cx, cy, radius);
            double[] pb = projectNode(highlightedPath.get(i + 1), cx, cy, radius);
            if (pa[2] > 0 || pb[2] > 0)
                g.drawLine((int) pa[0], (int) pa[1], (int) pb[0], (int) pb[1]);
        }
        g.setColor(ROUTE_LINE);
        g.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 0; i < highlightedPath.size() - 1; i++) {
            double[] pa = projectNode(highlightedPath.get(i), cx, cy, radius);
            double[] pb = projectNode(highlightedPath.get(i + 1), cx, cy, radius);
            if (pa[2] > 0 || pb[2] > 0)
                g.drawLine((int) pa[0], (int) pa[1], (int) pb[0], (int) pb[1]);
        }
        g.setStroke(new BasicStroke(1.5f));
    }

    private double[] projectNode(SatelliteRouter.Node node, int cx, int cy, int radius) {
        if (!node.isGroundStation) {
            String satIdStr = node.id.replace("SAT_", "");
            try {
                int satId = Integer.parseInt(satIdStr);
                for (SatelliteObject sat : satellites) {
                    if (sat.getId() == satId) {
                        double[] pos = sat.getCartesianPosition(planet.getRadius());
                        double nx = pos[0] / planet.getRadius();
                        double ny = pos[1] / planet.getRadius();
                        double nz = pos[2] / planet.getRadius();
                        double rotYRad = Math.toRadians(rotationY), rotXRad = Math.toRadians(rotationX);
                        double nx2 = nx * Math.cos(rotYRad) + nz * Math.sin(rotYRad);
                        double nz2 = -nx * Math.sin(rotYRad) + nz * Math.cos(rotYRad);
                        double ny2 = ny * Math.cos(rotXRad) - nz2 * Math.sin(rotXRad);
                        double nz3 = ny * Math.sin(rotXRad) + nz2 * Math.cos(rotXRad);
                        return new double[] { cx + nx2 * radius, cy - ny2 * radius, nz3 };
                    }
                }
            } catch (Exception e) {
            }
        } else {
            String gsIdStr = node.id.replace("GS_", "");
            try {
                int gsId = Integer.parseInt(gsIdStr);
                for (GroundStation gs : groundStations) {
                    if (gs.getId() == gsId) {
                        return project(gs.getLatitude(), gs.getLongitude(), 25000, cx, cy, radius, planet.getRadius());
                    }
                }
            } catch (Exception e) {
            }
        }

        // Dự phòng
        double[] p3 = node.pos3D;
        double mag = Math.sqrt(p3[0] * p3[0] + p3[1] * p3[1] + p3[2] * p3[2]);
        double alt = node.isGroundStation ? 0 : mag * 1000 - planet.getRadius();
        double lat = Math.toDegrees(Math.asin(p3[1] / mag));
        double lon = Math.toDegrees(Math.atan2(p3[0], p3[2]));
        return project(lat, lon, node.isGroundStation ? 25000 : alt, cx, cy, radius, planet.getRadius());
    }

    private SatelliteRouter.Node findRouteNode(String id) {
        for (SatelliteRouter.Node n : routeNodes)
            if (n.id.equals(id))
                return n;
        return null;
    }

    private void drawGroundStations(Graphics2D g, int cx, int cy, int radius) {
        g.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        for (GroundStation gs : groundStations) {
            double[] p = project(gs.getLatitude(), gs.getLongitude(), 25000, cx, cy, radius, planet.getRadius());
            if (p[2] < 0)
                continue;
            int sx = (int) p[0], sy = (int) p[1];
            // Quầng sáng
            g.setColor(new Color(255, 80, 80, 40));
            g.fillOval(sx - 9, sy - 9, 18, 18);
            // Điểm đánh dấu
            g.setColor(GS_COLOR);
            g.fillOval(sx - 4, sy - 4, 8, 8);
            g.setColor(new Color(255, 140, 140));
            g.setStroke(new BasicStroke(1.5f));
            g.drawOval(sx - 6, sy - 6, 12, 12);
            g.setStroke(new BasicStroke(1f));
            g.setColor(Color.WHITE);
            g.drawString(gs.getName(), sx + 9, sy + 4);
        }
    }

    private void drawSatellites(Graphics2D g, int cx, int cy, int radius) {
        for (SatelliteObject sat : satellites) {
            double[] pos = sat.getCartesianPosition(planet.getRadius());
            double nx = pos[0] / planet.getRadius();
            double ny = pos[1] / planet.getRadius();
            double nz = pos[2] / planet.getRadius();
            double rotYRad = Math.toRadians(rotationY), rotXRad = Math.toRadians(rotationX);
            double nx2 = nx * Math.cos(rotYRad) + nz * Math.sin(rotYRad);
            double nz2 = -nx * Math.sin(rotYRad) + nz * Math.cos(rotYRad);
            double ny2 = ny * Math.cos(rotXRad) - nz2 * Math.sin(rotXRad);
            double nz3 = ny * Math.sin(rotXRad) + nz2 * Math.cos(rotXRad);
            if (nz3 < -0.2)
                continue;
            int sx = (int) (cx + nx2 * radius), sy = (int) (cy - ny2 * radius);
            boolean isSelected = sat == selectedSat;
            Color c = SAT_COMM;
            if (isSelected) {
                g.setColor(new Color(255, 255, 255, 80));
                g.fillOval(sx - 12, sy - 12, 24, 24);
            }
            // Quầng sáng
            g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 55));
            g.fillOval(sx - 9, sy - 9, 18, 18);
            g.setColor(c);

            g.setStroke(new BasicStroke(2f));
            g.drawLine(sx - 6, sy, sx + 6, sy);
            g.drawLine(sx, sy - 6, sx, sy + 6);
            g.setStroke(new BasicStroke(1f));
            g.fillOval(sx - 3, sy - 3, 6, 6);
            if (isSelected || satellites.indexOf(sat) < 6) {
                g.setFont(new Font("Segoe UI", Font.PLAIN, 9));
                g.setColor(isSelected ? Color.WHITE : new Color(200, 255, 200, 180));
                g.drawString(sat.getName(), sx + 8, sy - 2);
            }
        }
    }

    private void drawSelectedInfo(Graphics2D g, int cx, int cy, int radius) {
        if (selectedSat == null)
            return;
        String info = String.format(
                "  %s  |  Độ cao: %.0f km  |  v: %.2f km/s  |  Nghiêng: %.1f°  |  🛰 VỆ TINH  ",
                selectedSat.getName(),
                selectedSat.getAltitude() / 1000,
                selectedSat.getOrbitalVelocity() / 1000,
                selectedSat.getInclination());
        g.setFont(new Font("Segoe UI", Font.BOLD, 12));
        FontMetrics fm = g.getFontMetrics();
        int tw = fm.stringWidth(info) + 16, th = 24;
        int bx = cx - tw / 2, by = cy + radius + 55;
        g.setColor(new Color(20, 30, 60, 210));
        g.fillRoundRect(bx, by, tw, th, 10, 10);
        g.setColor(new Color(100, 200, 255, 160));
        g.setStroke(new BasicStroke(1f));
        g.drawRoundRect(bx, by, tw, th, 10, 10);
        g.setColor(new Color(200, 240, 255));
        g.drawString(info, bx + 8, by + 17);
    }

    private void drawPlanetLabel(Graphics2D g, int cx, int cy, int radius) {
        g.setFont(new Font("Segoe UI", Font.BOLD, 16));
        g.setColor(new Color(200, 220, 255, 180));
        FontMetrics fm = g.getFontMetrics();
        String label = planet.getName();
        g.drawString(label, cx - fm.stringWidth(label) / 2, cy + radius + 28);
        g.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        g.setColor(new Color(130, 170, 210, 140));
        String info = String.format("R=%.0f km  |  Kéo để xoay  |  Cuộn để thu phóng  |  Nhấp vệ tinh để xem",
                planet.getRadius() / 1000);
        g.drawString(info, cx - fm.stringWidth(info) / 2 + 10, cy + radius + 46);
    }

    // ────── Các hàm API công khai ──────
    public void setPlanet(Planet planet) {
        this.planet = planet;
        loadTexture();
        repaint();
    }

    public void setSatellites(List<SatelliteObject> satellites) {
        this.satellites = satellites;
        repaint();
    }

    public void setGroundStations(List<GroundStation> groundStations) {
        this.groundStations = groundStations;
        repaint();
    }

    public void updatePlanetRotation(double deltaTimeSec) {
        // Trái Đất thực tế xoay: 360 độ trong 86400 giây
        double rotationAmount = (360.0 / 86400.0) * deltaTimeSec;
        this.rotationY += rotationAmount;
        invalidateCache();
    }

    public void setRoute(List<SatelliteRouter.Node> path,
            List<SatelliteRouter.Node> allNodes,
            List<SatelliteRouter.Edge> allEdges) {
        this.highlightedPath = path != null ? path : new ArrayList<>();
        this.routeNodes = allNodes != null ? allNodes : new ArrayList<>();
        this.routeEdges = allEdges != null ? allEdges : new ArrayList<>();
        repaint();
    }

    public void clearRoute() {
        highlightedPath.clear();
        routeNodes.clear();
        routeEdges.clear();
        repaint();
    }
}
