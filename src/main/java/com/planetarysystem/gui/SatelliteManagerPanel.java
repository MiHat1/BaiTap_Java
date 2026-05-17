package com.planetarysystem.gui;

import com.planetarysystem.core.*;
import com.planetarysystem.db.DatabaseManager;
import com.planetarysystem.physics.OrbitalMechanics;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.sql.SQLException;
import java.util.List;

/** Panel quản lý vệ tinh - CRUD + tính vận tốc quỹ đạo */
public class SatelliteManagerPanel extends JPanel {

    private DatabaseManager db;
    private Planet currentPlanet;
    private DefaultTableModel tableModel;
    private JTable table;
    private List<SatelliteObject> satellites;

    private JTextField txtName, txtLat, txtLon, txtAlt, txtInc, txtCommRange;

    private JLabel lblVelocity, lblPeriod, lblEscape, lblStable, lblRadius, lblAngVel;

    public SatelliteManagerPanel(DatabaseManager db, Planet planet) {
        this.db = db;
        this.currentPlanet = planet;
        setLayout(new BorderLayout(8, 8));
        setBackground(new Color(18, 22, 38));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        buildUI();
        refreshTable();
    }

    private void buildUI() {
        // ── Table ──
        String[] cols = { "ID", "Tên", "Vĩ độ°", "Kinh độ°", "Độ cao(km)", "Góc nghiêng°", "Vận tốc(km/s)", "Tầm(km)" };
        tableModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };
        table = new JTable(tableModel);
        styleTable(table);
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting())
                fillFormFromSelection();
        });
        // Độ rộng cột
        int[] widths = { 40, 100, 60, 60, 60, 80, 90, 70 };
        for (int i = 0; i < widths.length; i++)
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);

        JScrollPane scroll = new JScrollPane(table);
        scroll.getViewport().setBackground(new Color(25, 30, 50));
        add(scroll, BorderLayout.CENTER);

        // ── Form Panel ──
        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(new Color(25, 30, 50));
        form.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(60, 100, 180)), "Thuộc tính Vệ tinh",
                0, 0, new Font("Segoe UI", Font.BOLD, 13), new Color(120, 180, 255)));

        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(4, 6, 4, 6);
        gc.fill = GridBagConstraints.HORIZONTAL;

        txtName = styleField(new JTextField("NewSat-1", 12));
        txtLat = styleField(new JTextField("0.0", 8));
        txtLon = styleField(new JTextField("0.0", 8));
        txtAlt = styleField(new JTextField("500", 8));
        txtInc = styleField(new JTextField("51.6", 8));
        txtCommRange = styleField(new JTextField("3000", 8));

        lblVelocity = infoLabel("–");
        lblPeriod = infoLabel("–");
        lblEscape = infoLabel("–");
        lblStable = infoLabel("–");
        lblRadius = infoLabel("–");
        lblAngVel = infoLabel("–");

        // Dòng 0: Tên
        gc.gridx = 0;
        gc.gridy = 0;
        form.add(label("Tên:"), gc);
        gc.gridx = 1;
        form.add(txtName, gc);

        // Dòng 1: Vĩ độ, Kinh độ
        gc.gridx = 0;
        gc.gridy = 1;
        form.add(label("Vĩ độ (°):"), gc);
        gc.gridx = 1;
        form.add(txtLat, gc);
        gc.gridx = 2;
        form.add(label("Kinh độ (°):"), gc);
        gc.gridx = 3;
        form.add(txtLon, gc);

        // Dòng 2: Độ cao, Độ nghiêng
        gc.gridx = 0;
        gc.gridy = 2;
        form.add(label("Độ cao (km):"), gc);
        gc.gridx = 1;
        form.add(txtAlt, gc);
        gc.gridx = 2;
        form.add(label("Góc nghiêng (°):"), gc);
        gc.gridx = 3;
        form.add(txtInc, gc);

        // Dòng 3: Tầm viễn thông
        gc.gridx = 0;
        gc.gridy = 3;
        form.add(label("Tầm viễn thông (km):"), gc);
        gc.gridx = 1;
        form.add(txtCommRange, gc);

        // Dòng 4: Vận tốc, Chu kỳ
        gc.gridx = 0;
        gc.gridy = 4;
        form.add(label("Vận tốc quỹ đạo:"), gc);
        gc.gridx = 1;
        form.add(lblVelocity, gc);
        gc.gridx = 2;
        form.add(label("Chu kỳ:"), gc);
        gc.gridx = 3;
        form.add(lblPeriod, gc);

        // Dòng 5: Vận tốc vũ trụ, Bán kính quỹ đạo
        gc.gridx = 0;
        gc.gridy = 5;
        form.add(label("Vận tốc vũ trụ 2:"), gc);
        gc.gridx = 1;
        form.add(lblEscape, gc);
        gc.gridx = 2;
        form.add(label("Bán kính quỹ đạo:"), gc);
        gc.gridx = 3;
        form.add(lblRadius, gc);

        // Dòng 6: Vận tốc góc, Quỹ đạo ổn định?
        gc.gridx = 0;
        gc.gridy = 6;
        form.add(label("Vận tốc góc:"), gc);
        gc.gridx = 1;
        form.add(lblAngVel, gc);
        gc.gridx = 2;
        form.add(label("Quỹ đạo Ổn định:"), gc);
        gc.gridx = 3;
        form.add(lblStable, gc);

        // Dòng 7: Các nút chức năng
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        btnPanel.setBackground(new Color(25, 30, 50));
        JButton btnCalc = btn("Tính toán", new Color(60, 120, 200));
        JButton btnAdd = btn("Thêm", new Color(40, 160, 80));
        JButton btnUpdate = btn("Cập nhật", new Color(160, 120, 30));
        JButton btnDelete = btn("Xóa", new Color(180, 50, 50));
        JButton btnClear = btn("Dọn trống", new Color(70, 70, 90));
        btnPanel.add(btnCalc);
        btnPanel.add(btnAdd);
        btnPanel.add(btnUpdate);
        btnPanel.add(btnDelete);
        btnPanel.add(btnClear);

        btnCalc.addActionListener(e -> calculateOrbital());
        btnAdd.addActionListener(e -> addSatellite());
        btnUpdate.addActionListener(e -> updateSatellite());
        btnDelete.addActionListener(e -> deleteSatellite());
        btnClear.addActionListener(e -> clearForm());

        gc.gridx = 0;
        gc.gridy = 7;
        gc.gridwidth = 4;
        form.add(btnPanel, gc);

        add(form, BorderLayout.SOUTH);
    }

    private void calculateOrbital() {
        try {
            double alt = Double.parseDouble(txtAlt.getText().trim()) * 1000;
            double v = OrbitalMechanics.circularOrbitalVelocity(currentPlanet, alt);
            double T = OrbitalMechanics.orbitalPeriod(currentPlanet, alt);
            double ve = OrbitalMechanics.escapeVelocity(currentPlanet, alt);
            double w = OrbitalMechanics.angularVelocity(currentPlanet, alt);
            double r = (currentPlanet.getRadius() + alt) / 1000;
            boolean stable = OrbitalMechanics.isStableOrbit(currentPlanet, alt);

            lblVelocity.setText(String.format("%.3f km/s", v / 1000));
            lblPeriod.setText(formatPeriod(T));
            lblEscape.setText(String.format("%.3f km/s", ve / 1000));
            lblRadius.setText(String.format("%.0f km", r));
            lblAngVel.setText(String.format("%.6f rad/s", w));
            lblStable.setText(stable ? "ỔN ĐỊNH" : "QUÁ THẤP");
            lblStable.setForeground(stable ? new Color(80, 220, 80) : new Color(255, 80, 80));
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Giá trị độ cao không hợp lệ.");
        }
    }

    private String formatPeriod(double sec) {
        int h = (int) (sec / 3600), m = (int) ((sec % 3600) / 60), s = (int) (sec % 60);
        return String.format("%dh %dm %ds", h, m, s);
    }

    private void addSatellite() {
        try {
            SatelliteObject sat = buildFromForm();
            db.insertSatellite(sat);
            refreshTable();
            firePropertyChange("satellites_changed", null, sat);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Error: " + e.getMessage());
        }
    }

    private void updateSatellite() {
        int row = table.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn vệ tinh trước.");
            return;
        }
        try {
            SatelliteObject sat = buildFromForm();
            sat.setId((int) tableModel.getValueAt(row, 0));
            db.updateSatellite(sat);
            refreshTable();
            firePropertyChange("satellites_changed", null, sat);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Error: " + e.getMessage());
        }
    }

    private void deleteSatellite() {
        int row = table.getSelectedRow();
        if (row < 0)
            return;
        int id = (int) tableModel.getValueAt(row, 0);
        if (JOptionPane.showConfirmDialog(this, "Xóa vệ tinh ID=" + id + "?", "Xác nhận",
                JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
            try {
                db.deleteSatellite(id);
                refreshTable();
                firePropertyChange("satellites_changed", null, id);
            } catch (SQLException e) {
                JOptionPane.showMessageDialog(this, e.getMessage());
            }
        }
    }

    private SatelliteObject buildFromForm() {
        double alt = Double.parseDouble(txtAlt.getText().trim()) * 1000;
        double v = OrbitalMechanics.circularOrbitalVelocity(currentPlanet, alt);
        SatelliteObject sat = new SatelliteObject(
                txtName.getText().trim(),
                "Vệ tinh",
                currentPlanet.getName(),
                Double.parseDouble(txtLat.getText().trim()),
                Double.parseDouble(txtLon.getText().trim()),
                alt,
                Double.parseDouble(txtInc.getText().trim()),
                true,
                Double.parseDouble(txtCommRange.getText().trim()) * 1000);
        sat.setOrbitalVelocity(v);
        return sat;
    }

    private void fillFormFromSelection() {
        int row = table.getSelectedRow();
        if (row < 0 || satellites == null)
            return;
        SatelliteObject sat = satellites.stream()
                .filter(s -> s.getId() == (int) tableModel.getValueAt(row, 0))
                .findFirst().orElse(null);
        if (sat == null)
            return;
        txtName.setText(sat.getName());
        txtLat.setText(String.valueOf(sat.getLatitude()));
        txtLon.setText(String.valueOf(sat.getLongitude()));
        txtAlt.setText(String.valueOf(sat.getAltitude() / 1000.0));
        txtInc.setText(String.valueOf(sat.getInclination()));
        txtCommRange.setText(String.valueOf(sat.getCommRange() / 1000.0));
        calculateOrbital();
    }

    private void clearForm() {
        txtName.setText("");
        txtLat.setText("0");
        txtLon.setText("0");
        txtAlt.setText("500");
        txtInc.setText("0");
        txtCommRange.setText("3000");
        lblVelocity.setText("–");
        lblPeriod.setText("–");
        lblEscape.setText("–");
        lblStable.setText("–");
        lblRadius.setText("–");
        lblAngVel.setText("–");
        table.clearSelection();
    }

    public void refreshTable() {
        try {
            satellites = db.getSatellitesByPlanet(currentPlanet.getName());
            tableModel.setRowCount(0);
            for (SatelliteObject s : satellites) {
                tableModel.addRow(new Object[] {
                        s.getId(), s.getName(),
                        String.valueOf(s.getLatitude()),
                        String.valueOf(s.getLongitude()),
                        String.valueOf(s.getAltitude() / 1000.0),
                        String.valueOf(s.getInclination()),
                        String.format(java.util.Locale.US, "%.3f", s.getOrbitalVelocity() / 1000),
                        String.valueOf(s.getCommRange() / 1000.0)
                });
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "DB Error: " + e.getMessage());
        }
    }

    public void setPlanet(Planet p) {
        this.currentPlanet = p;
        refreshTable();
    }

    public List<SatelliteObject> getSatellites() {
        return satellites;
    }

    // ── Các hàm định dạng giao diện ──
    private void styleTable(JTable t) {
        t.setBackground(new Color(20, 25, 45));
        t.setForeground(new Color(200, 220, 255));
        t.setGridColor(new Color(40, 55, 90));
        t.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        t.setRowHeight(24);
        t.setSelectionBackground(new Color(50, 80, 150));
        t.setSelectionForeground(Color.WHITE);
        JTableHeader h = t.getTableHeader();
        h.setBackground(new Color(30, 40, 75));
        h.setForeground(new Color(150, 200, 255));
        h.setFont(new Font("Segoe UI", Font.BOLD, 12));
    }

    private JTextField styleField(JTextField f) {
        f.setBackground(new Color(30, 38, 65));
        f.setForeground(new Color(200, 230, 255));
        f.setCaretColor(Color.WHITE);
        f.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(60, 90, 150)),
                BorderFactory.createEmptyBorder(3, 6, 3, 6)));
        return f;
    }

    private JLabel label(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(new Color(150, 180, 220));
        l.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        return l;
    }

    private JLabel infoLabel(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(new Color(100, 220, 255));
        l.setFont(new Font("Segoe UI", Font.BOLD, 12));
        return l;
    }

    private JButton btn(String text, Color bg) {
        JButton b = new JButton(text);
        b.setBackground(bg);
        b.setForeground(Color.WHITE);
        b.setFont(new Font("Segoe UI", Font.BOLD, 12));
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent e) {
                b.setBackground(bg.brighter());
            }

            public void mouseExited(java.awt.event.MouseEvent e) {
                b.setBackground(bg);
            }
        });
        return b;
    }
}
