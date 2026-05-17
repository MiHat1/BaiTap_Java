package com.planetarysystem.gui;

import com.planetarysystem.core.*;
import com.planetarysystem.db.DatabaseManager;
import com.planetarysystem.routing.SatelliteRouter;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.sql.SQLException;
import java.util.List;

/** Panel quản lý trạm mặt đất + định tuyến vệ tinh */
public class RoutingPanel extends JPanel {

    private DatabaseManager db;
    private Planet currentPlanet;
    private List<SatelliteObject> satellites;
    private List<GroundStation> groundStations;

    private DefaultTableModel gsTableModel;
    private JTable gsTable;

    private JTextField txtGsName, txtGsLat, txtGsLon, txtGsDesc;
    private JComboBox<GroundStation> cmbFrom, cmbTo;
    private JTextArea txtRouteResult;
    private JLabel lblStatus;

    public RoutingPanel(DatabaseManager db, Planet planet,
            List<SatelliteObject> satellites) {
        this.db = db;
        this.currentPlanet = planet;
        this.satellites = satellites;
        setLayout(new BorderLayout(8, 8));
        setBackground(new Color(18, 22, 38));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        buildUI();
        refreshData();
    }

    private void buildUI() {
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        split.setDividerLocation(350);
        split.setBackground(new Color(18, 22, 38));

        // ── TRÁI: Quản lý Trạm Mặt Đất ──
        JPanel left = new JPanel(new BorderLayout(6, 6));
        left.setBackground(new Color(18, 22, 38));

        String[] cols = { "ID", "Tên", "Vĩ độ°", "Kinh độ°", "Mô tả" };
        gsTableModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };
        gsTable = new JTable(gsTableModel);
        styleTable(gsTable);
        gsTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting())
                fillGsForm();
        });
        JScrollPane scroll = new JScrollPane(gsTable);
        scroll.getViewport().setBackground(new Color(25, 30, 50));

        JPanel gsForm = buildGsForm();
        left.add(scroll, BorderLayout.CENTER);
        left.add(gsForm, BorderLayout.SOUTH);

        // ── PHẢI: Định tuyến ──
        JPanel right = new JPanel(new BorderLayout(6, 6));
        right.setBackground(new Color(18, 22, 38));

        JPanel routeForm = buildRouteForm();
        txtRouteResult = new JTextArea(18, 30);
        txtRouteResult.setEditable(false);
        txtRouteResult.setFont(new Font("Consolas", Font.PLAIN, 13));
        txtRouteResult.setBackground(new Color(15, 20, 35));
        txtRouteResult.setForeground(new Color(100, 220, 150));
        txtRouteResult.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        txtRouteResult.setText(
                "Chọn trạm nguồn và trạm đích,\nsau đó nhấn 'Tìm đường'.\n\nHệ thống sử dụng thuật toán Dijkstra để tìm\nđường đi ngắn nhất qua các vệ tinh viễn thông.");

        lblStatus = new JLabel(" ");
        lblStatus.setForeground(new Color(100, 200, 255));
        lblStatus.setFont(new Font("Segoe UI", Font.ITALIC, 12));

        right.add(routeForm, BorderLayout.NORTH);
        right.add(new JScrollPane(txtRouteResult), BorderLayout.CENTER);
        right.add(lblStatus, BorderLayout.SOUTH);

        split.setLeftComponent(left);
        split.setRightComponent(right);
        add(split, BorderLayout.CENTER);
    }

    private JPanel buildGsForm() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(new Color(25, 30, 50));
        form.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(60, 100, 180)),
                "Trạm mặt đất",
                0, 0, new Font("Segoe UI", Font.BOLD, 13), new Color(120, 180, 255)));

        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(4, 6, 4, 6);
        gc.fill = GridBagConstraints.HORIZONTAL;

        txtGsName = field("Station-A");
        txtGsLat = field("0.0");
        txtGsLon = field("0.0");
        txtGsDesc = field("Ground Station");

        addRow(form, gc, 0, "Tên:", txtGsName, "Vĩ độ (°):", txtGsLat);
        addRow(form, gc, 1, "Kinh độ (°):", txtGsLon, "Mô tả:", txtGsDesc);

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        btns.setBackground(new Color(25, 30, 50));
        JButton bAdd = btn("Thêm", new Color(40, 140, 70));
        JButton bUpd = btn("Cập nhật", new Color(140, 100, 20));
        JButton bDel = btn("Xóa", new Color(160, 40, 40));
        JButton bClr = btn("Dọn trống", new Color(60, 60, 80));
        btns.add(bAdd);
        btns.add(bUpd);
        btns.add(bDel);
        btns.add(bClr);

        bAdd.addActionListener(e -> addGs());
        bUpd.addActionListener(e -> updateGs());
        bDel.addActionListener(e -> deleteGs());
        bClr.addActionListener(e -> clearGsForm());

        gc.gridx = 0;
        gc.gridy = 2;
        gc.gridwidth = 4;
        form.add(btns, gc);
        return form;
    }

    private JPanel buildRouteForm() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBackground(new Color(25, 30, 55));
        p.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(new Color(60, 180, 120)),
            "Định tuyến Viễn thông",
            0, 0, new Font("Segoe UI", Font.BOLD, 13), new Color(80, 220, 150)));

        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(5, 8, 5, 8);
        gc.fill = GridBagConstraints.HORIZONTAL;

        cmbFrom = new JComboBox<>(); styleCombo(cmbFrom);
        cmbTo   = new JComboBox<>(); styleCombo(cmbTo);

        JButton bRoute = btn("Tìm đường", new Color(30, 120, 200));
        JButton bClear = btn("Xóa đường", new Color(80, 60, 100));

        JLabel lblFrom = new JLabel("Trạm Nguồn:");
        lblFrom.setForeground(Color.WHITE);
        lblFrom.setFont(new Font("Segoe UI", Font.BOLD, 12));

        JLabel lblTo = new JLabel("Trạm Đích:");
        lblTo.setForeground(Color.WHITE);
        lblTo.setFont(new Font("Segoe UI", Font.BOLD, 12));

        gc.gridx=0; gc.gridy=0; p.add(lblFrom, gc);
        gc.gridx=1; p.add(cmbFrom, gc);
        gc.gridx=2; p.add(lblTo, gc);
        gc.gridx=3; p.add(cmbTo, gc);
        gc.gridx=4; p.add(bRoute, gc);
        gc.gridx=5; p.add(bClear, gc);

        bRoute.addActionListener(e -> findRoute());
        bClear.addActionListener(e -> clearRoute());

        return p;
    }

    private void findRoute() {
        GroundStation from = (GroundStation) cmbFrom.getSelectedItem();
        GroundStation to = (GroundStation) cmbTo.getSelectedItem();
        if (from == null || to == null) {
            txtRouteResult.setText("Vui lòng chọn cả hai trạm.");
            return;
        }
        if (from.getId() == to.getId()) {
            txtRouteResult.setText("Trạm nguồn và đích trùng nhau.");
            return;
        }

        SatelliteRouter router = new SatelliteRouter(currentPlanet, satellites, groundStations);
        SatelliteRouter.RouteResult result = router.findRoute(from, to);

        txtRouteResult.setText(result.description);
        lblStatus.setText(result.found ? String.format("Đã tìm thấy: %d chặng, tổng cộng %.0f km",
                result.path.size() - 1, result.totalDistance) : "Không tìm thấy đường đi.");

        if (result.found) {
            // Thông báo cho trình kết xuất
            firePropertyChange("route_computed", null, result);
        } else {
            firePropertyChange("route_cleared", null, null);
        }
    }

    private void clearRoute() {
        txtRouteResult.setText("Đã xóa đường đi.");
        lblStatus.setText(" ");
        firePropertyChange("route_cleared", null, null);
    }

    private void addGs() {
        try {
            db.insertGroundStation(buildGs());
            refreshData();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Error: " + e.getMessage());
        }
    }

    private void updateGs() {
        int row = gsTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn trạm trước.");
            return;
        }
        try {
            GroundStation gs = buildGs();
            gs.setId((int) gsTableModel.getValueAt(row, 0));
            db.updateGroundStation(gs);
            refreshData();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Error: " + e.getMessage());
        }
    }

    private void deleteGs() {
        int row = gsTable.getSelectedRow();
        if (row < 0)
            return;
        int id = (int) gsTableModel.getValueAt(row, 0);
        if (JOptionPane.showConfirmDialog(this, "Xóa trạm ID=" + id + "?", "Xác nhận",
                JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
            try {
                db.deleteGroundStation(id);
                refreshData();
            } catch (SQLException e) {
                JOptionPane.showMessageDialog(this, e.getMessage());
            }
        }
    }

    private GroundStation buildGs() {
        return new GroundStation(
                txtGsName.getText().trim(), currentPlanet.getName(),
                Double.parseDouble(txtGsLat.getText().trim()),
                Double.parseDouble(txtGsLon.getText().trim()),
                txtGsDesc.getText().trim());
    }

    private void fillGsForm() {
        int row = gsTable.getSelectedRow();
        if (row < 0)
            return;
        txtGsName.setText((String) gsTableModel.getValueAt(row, 1));
        txtGsLat.setText((String) gsTableModel.getValueAt(row, 2));
        txtGsLon.setText((String) gsTableModel.getValueAt(row, 3));
        txtGsDesc.setText((String) gsTableModel.getValueAt(row, 4));
    }

    private void clearGsForm() {
        txtGsName.setText("");
        txtGsLat.setText("0");
        txtGsLon.setText("0");
        txtGsDesc.setText("");
        gsTable.clearSelection();
    }

    public void refreshData() {
        try {
            groundStations = db.getGroundStationsByPlanet(currentPlanet.getName());
            gsTableModel.setRowCount(0);
            for (GroundStation gs : groundStations) {
                gsTableModel.addRow(new Object[] {
                        gs.getId(), gs.getName(),
                        String.valueOf(gs.getLatitude()),
                        String.valueOf(gs.getLongitude()),
                        gs.getDescription()
                });
            }
            cmbFrom.removeAllItems();
            cmbTo.removeAllItems();
            for (GroundStation gs : groundStations) {
                cmbFrom.addItem(gs);
                cmbTo.addItem(gs);
            }
            if (cmbTo.getItemCount() > 1)
                cmbTo.setSelectedIndex(1);
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "DB Error: " + e.getMessage());
        }
    }

    public void setPlanet(Planet p) {
        this.currentPlanet = p;
        refreshData();
    }

    public void setSatellites(List<SatelliteObject> s) {
        this.satellites = s;
    }

    public List<GroundStation> getGroundStations() {
        return groundStations;
    }

    // ── Các hàm định dạng giao diện ──
    private void styleTable(JTable t) {
        t.setBackground(new Color(20, 25, 45));
        t.setForeground(new Color(200, 220, 255));
        t.setGridColor(new Color(40, 55, 90));
        t.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        t.setRowHeight(24);
        t.setSelectionBackground(new Color(50, 80, 150));
        JTableHeader h = t.getTableHeader();
        h.setBackground(new Color(30, 40, 75));
        h.setForeground(new Color(150, 200, 255));
        h.setFont(new Font("Segoe UI", Font.BOLD, 12));
    }

    private JTextField field(String v) {
        JTextField f = new JTextField(v, 12);
        f.setBackground(new Color(30, 38, 65));
        f.setForeground(new Color(200, 230, 255));
        f.setCaretColor(Color.WHITE);
        f.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(60, 90, 150)),
                BorderFactory.createEmptyBorder(3, 6, 3, 6)));
        return f;
    }

    private void styleCombo(JComboBox<?> c) {
        c.setBackground(Color.WHITE);
        c.setForeground(Color.BLACK);
        c.setFont(new Font("Segoe UI", Font.BOLD, 12));
        c.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                Component comp = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (index == -1) {
                    comp.setForeground(Color.BLACK);
                    comp.setBackground(Color.WHITE);
                } else {
                    comp.setBackground(isSelected ? new Color(200, 220, 255) : Color.WHITE);
                    comp.setForeground(Color.BLACK);
                }
                return comp;
            }
        });
    }

    private JLabel lbl(String t) {
        JLabel l = new JLabel(t);
        l.setForeground(new Color(150, 180, 220));
        l.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        return l;
    }

    private JButton btn(String t, Color bg) {
        JButton b = new JButton(t);
        b.setBackground(bg);
        b.setForeground(Color.WHITE);
        b.setFont(new Font("Segoe UI", Font.BOLD, 11));
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    private void addRow(JPanel p, GridBagConstraints gc, int row, String l1, JComponent c1, String l2, JComponent c2) {
        gc.gridy = row;
        gc.gridx = 0;
        p.add(lbl(l1), gc);
        gc.gridx = 1;
        p.add(c1, gc);
        gc.gridx = 2;
        p.add(lbl(l2), gc);
        gc.gridx = 3;
        p.add(c2, gc);
    }
}
