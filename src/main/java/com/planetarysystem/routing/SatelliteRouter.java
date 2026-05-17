package com.planetarysystem.routing;

import com.planetarysystem.core.Planet;
import com.planetarysystem.core.SatelliteObject;
import com.planetarysystem.core.GroundStation;

import java.util.*;

/**
 * Động cơ định tuyến viễn thông vệ tinh.
 *
 * Sử dụng thuật toán Dijkstra trên một đồ thị nơi:
 *   - Các đỉnh = trạm mặt đất + vệ tinh viễn thông
 *   - Các cạnh = liên kết tầm nhìn (tầm nhìn thẳng giữa các đỉnh)
 *   - Trọng số = khoảng cách giữa các đỉnh (tối thiểu hóa tổng khoảng cách)
 *
 * Một trạm mặt đất có thể liên lạc trực tiếp với một vệ tinh nếu
 * vệ tinh ở trên đường chân trời và trong tầm phủ sóng.
 *
 * Hai vệ tinh có thể liên lạc với nhau nếu:
 *   - Chúng nằm trong tầm phủ sóng của nhau
 *   - Không bị che khuất bởi khối lượng hành tinh
 */
public class SatelliteRouter {

    /**
     * Đại diện cho một đỉnh trong đồ thị định tuyến.
     */
    public static class Node {
        public final String id;
        public final String label;
        public final boolean isGroundStation;
        public final double[] pos3D; // Véc-tơ vị trí 3D (tâm hành tinh, đơn vị km)

        public Node(String id, String label, boolean isGroundStation, double[] pos3D) {
            this.id = id;
            this.label = label;
            this.isGroundStation = isGroundStation;
            this.pos3D = pos3D;
        }
    }

    /**
     * Đại diện cho một cạnh (liên kết viễn thông) trong đồ thị.
     */
    public static class Edge {
        public final String fromId;
        public final String toId;
        public final double distance; // km

        public Edge(String fromId, String toId, double distance) {
            this.fromId = fromId;
            this.toId = toId;
            this.distance = distance;
        }
    }

    /**
     * Kết quả của quá trình tính toán định tuyến.
     */
    public static class RouteResult {
        public final List<Node> path;
        public final double totalDistance;
        public final boolean found;
        public final String description;

        public RouteResult(List<Node> path, double totalDistance, boolean found, String description) {
            this.path = path;
            this.totalDistance = totalDistance;
            this.found = found;
            this.description = description;
        }
    }

    private final Planet planet;
    private final List<SatelliteObject> satellites;
    private final List<GroundStation> groundStations;

    private List<Node> nodes;
    private List<Edge> edges;
    private Map<String, Node> nodeMap;

    public SatelliteRouter(Planet planet, List<SatelliteObject> satellites,
                           List<GroundStation> groundStations) {
        this.planet = planet;
        this.satellites = satellites;
        this.groundStations = groundStations;
        buildGraph();
    }

    /**
     * Xây dựng đồ thị viễn thông từ các vị trí hiện tại của vệ tinh và trạm mặt đất.
     */
    public void buildGraph() {
        nodes = new ArrayList<>();
        edges = new ArrayList<>();
        nodeMap = new HashMap<>();

        double R = planet.getRadius() / 1000.0; // chuyển đổi sang km

        // Thêm các đỉnh trạm mặt đất
        for (GroundStation gs : groundStations) {
            if (!gs.getPlanetName().equals(planet.getName())) continue;
            double[] norm = gs.getSurfaceNormal();
            double[] pos = {norm[0] * R, norm[1] * R, norm[2] * R};
            Node n = new Node("GS_" + gs.getId(), gs.getName(), true, pos);
            nodes.add(n);
            nodeMap.put(n.id, n);
        }

        // Thêm các đỉnh vệ tinh
        for (SatelliteObject sat : satellites) {
            if (!sat.getPlanetName().equals(planet.getName())) continue;
            double[] cart = sat.getCartesianPosition(planet.getRadius());
            double[] posKm = {cart[0] / 1000.0, cart[1] / 1000.0, cart[2] / 1000.0};
            Node n = new Node("SAT_" + sat.getId(), sat.getName(), false, posKm);
            nodes.add(n);
            nodeMap.put(n.id, n);
        }

        // Xây dựng các cạnh: kiểm tra tầm nhìn và tầm phủ sóng
        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                Node a = nodes.get(i);
                Node b = nodes.get(j);

                // Trạm mặt đất với Trạm mặt đất: không có liên kết trực tiếp
                if (a.isGroundStation && b.isGroundStation) continue;

                double dist = distance3D(a.pos3D, b.pos3D);

                // Kiểm tra tầm phủ sóng
                double maxRange = getCommRange(a, b);
                if (dist > maxRange) continue;

                // Kiểm tra tầm nhìn (không bị che khuất bởi hành tinh)
                if (!hasLineOfSight(a.pos3D, b.pos3D, R)) continue;

                edges.add(new Edge(a.id, b.id, dist));
                edges.add(new Edge(b.id, a.id, dist));
            }
        }
    }

    /**
     * Tìm đường đi từ trạm mặt đất A đến trạm mặt đất B sử dụng thuật toán Dijkstra.
     */
    public RouteResult findRoute(GroundStation from, GroundStation to) {
        buildGraph(); // xây dựng lại với vị trí hiện tại

        String startId = "GS_" + from.getId();
        String endId = "GS_" + to.getId();

        if (!nodeMap.containsKey(startId) || !nodeMap.containsKey(endId)) {
            return new RouteResult(null, 0, false,
                "Trạm mặt đất không tìm thấy trong biểu đồ định tuyến. Đảm bảo cả hai trạm đều nằm trên hành tinh: " + planet.getName());
        }

        // Thuật toán Dijkstra
        Map<String, Double> dist = new HashMap<>();
        Map<String, String> prev = new HashMap<>();

        class QueueNode implements Comparable<QueueNode> {
            String id;
            double dist;
            QueueNode(String id, double dist) { this.id = id; this.dist = dist; }
            public int compareTo(QueueNode other) { return Double.compare(this.dist, other.dist); }
        }

        PriorityQueue<QueueNode> pq = new PriorityQueue<>();

        for (Node n : nodes) {
            dist.put(n.id, Double.MAX_VALUE);
        }
        dist.put(startId, 0.0);
        pq.add(new QueueNode(startId, 0.0));

        while (!pq.isEmpty()) {
            QueueNode current = pq.poll();
            String currentId = current.id;
            double currentDist = current.dist;

            if (currentDist > dist.get(currentId)) continue;

            if (currentId.equals(endId)) break;

            for (Edge e : edges) {
                if (!e.fromId.equals(currentId)) continue;
                double newDist = currentDist + e.distance;
                if (newDist < dist.getOrDefault(e.toId, Double.MAX_VALUE)) {
                    dist.put(e.toId, newDist);
                    prev.put(e.toId, currentId);
                    pq.add(new QueueNode(e.toId, newDist));
                }
            }
        }

        // Tái tạo đường đi
        if (dist.get(endId) == Double.MAX_VALUE) {
            return new RouteResult(null, 0, false,
                "Không tìm thấy đường đi giữa " + from.getName() + " và " + to.getName() +
                ".\nHãy kiểm tra xem có đủ vệ tinh viễn thông và tầm phủ sóng không.");
        }

        List<Node> path = new ArrayList<>();
        String current = endId;
        while (current != null) {
            path.add(0, nodeMap.get(current));
            current = prev.get(current);
        }

        double totalDist = dist.get(endId);
        String desc = buildRouteDescription(path, totalDist, from.getName(), to.getName());

        return new RouteResult(path, totalDist, true, desc);
    }

    private String buildRouteDescription(List<Node> path, double totalDist,
                                          String fromName, String toName) {
        StringBuilder sb = new StringBuilder();
        sb.append("📡 Đường đi: ").append(fromName).append(" → ").append(toName).append("\n");
        sb.append("Tổng khoảng cách: ").append(String.format("%.0f km", totalDist)).append("\n\n");
        sb.append("Các chặng:\n");
        for (int i = 0; i < path.size(); i++) {
            Node n = path.get(i);
            String icon = n.isGroundStation ? "📍" : "🛰️";
            sb.append(String.format("  %d. %s %s\n", i + 1, icon, n.label));
        }
        sb.append("\nTổng số chặng: ").append(path.size() - 1);
        return sb.toString();
    }

    /**
     * Trả về tầm phủ sóng tối đa giữa hai đỉnh (km).
     * Nếu một trong hai là vệ tinh, sử dụng tầm phủ sóng của nó. Lấy giá trị nhỏ nhất của cả hai.
     */
    private double getCommRange(Node a, Node b) {
        double rangeA = Double.MAX_VALUE;
        double rangeB = Double.MAX_VALUE;

        if (a.id.startsWith("SAT_")) {
            int satId = Integer.parseInt(a.id.substring(4));
            SatelliteObject sat = getSatById(satId);
            if (sat != null) rangeA = sat.getCommRange() / 1000.0;
        }
        if (b.id.startsWith("SAT_")) {
            int satId = Integer.parseInt(b.id.substring(4));
            SatelliteObject sat = getSatById(satId);
            if (sat != null) rangeB = sat.getCommRange() / 1000.0;
        }

        // Giả định các trạm mặt đất có tầm phủ sóng vô hạn (chỉ bị giới hạn bởi vệ tinh)
        if (a.isGroundStation) rangeA = Double.MAX_VALUE;
        if (b.isGroundStation) rangeB = Double.MAX_VALUE;

        return Math.min(rangeA, rangeB);
    }

    /**
     * Kiểm tra xem hai vị trí 3D có tầm nhìn thẳng hay không (không bị che khuất bởi khối cầu hành tinh bán kính R).
     */
    private boolean hasLineOfSight(double[] a, double[] b, double R) {
        // Đường thẳng tham số: P(t) = A + t*(B-A), t trong [0,1]
        // Tìm khoảng cách ngắn nhất từ tâm (0,0) đến đoạn thẳng
        double dx = b[0] - a[0];
        double dy = b[1] - a[1];
        double dz = b[2] - a[2];

        double len2 = dx * dx + dy * dy + dz * dz;
        if (len2 == 0) return true;

        double t = -(a[0] * dx + a[1] * dy + a[2] * dz) / len2;
        t = Math.max(0, Math.min(1, t));

        double px = a[0] + t * dx;
        double py = a[1] + t * dy;
        double pz = a[2] + t * dz;

        double minDist2 = px * px + py * py + pz * pz;
        // Trừ đi một sai số epsilon nhỏ (0.1 km^2) để xử lý sai số làm tròn số thực
        // khi t đúng bằng 0 hoặc 1.
        return minDist2 >= (R * R - 0.1);
    }

    private double distance3D(double[] a, double[] b) {
        double dx = b[0] - a[0];
        double dy = b[1] - a[1];
        double dz = b[2] - a[2];
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private SatelliteObject getSatById(int id) {
        for (SatelliteObject s : satellites) {
            if (s.getId() == id) return s;
        }
        return null;
    }

    public List<Node> getNodes() { return nodes; }
    public List<Edge> getEdges() { return edges; }
}
