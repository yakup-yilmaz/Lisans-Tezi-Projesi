import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DistanceCalculator {
    private static final double DISTANCE_SCALE = 3.0;

    static class Node { String id; Node(String id) { this.id = id; } }
    static class Edge { String from, to; double distance; Edge(String f, String t, double d) { from = f; to = t; distance = d; } }
    
    static class Graph {
        List<String> nodes = new ArrayList<>();
        Map<String, List<Edge>> adjacency = new HashMap<>();
        
        void addNode(String id) { nodes.add(id); adjacency.put(id, new ArrayList<>()); }
        void addEdge(String from, String to, double dist) {
            adjacency.get(from).add(new Edge(from, to, dist * DISTANCE_SCALE));
            adjacency.get(to).add(new Edge(to, from, dist * DISTANCE_SCALE));
        }
    }

    public static void main(String[] args) {
        Graph graph = new Graph();
        
        graph.addNode("MAIN");
        graph.addNode("M1");
        graph.addNode("M2");

        List<String> intersections = new ArrayList<>();
        for (int index = 1; index <= 35; index++) {
            String id = "J" + index;
            graph.addNode(id);
            intersections.add(id);
        }

        for (int index = 1; index <= 12; index++) {
            String id = "C" + index;
            graph.addNode(id);
            int anchor = (index - 1) % intersections.size();
            String linkedIntersection = intersections.get(anchor);
            graph.addEdge(linkedIntersection, id, 0.8 + ((index % 3) * 0.2));
        }

        for (int index = 0; index < intersections.size(); index++) {
            String current = intersections.get(index);
            if (index + 1 < intersections.size()) {
                graph.addEdge(current, intersections.get(index + 1), 0.6 + ((index % 4) * 0.2));
            }
            if (index + 8 < intersections.size()) {
                graph.addEdge(current, intersections.get(index + 8), 0.9 + (((index + 1) % 3) * 0.2));
            }
        }

        graph.addEdge("MAIN", "J1", 3.8);
        graph.addEdge("MAIN", "J9", 4.2);
        graph.addEdge("J16", "M1", 2.9);
        graph.addEdge("J24", "M1", 2.6);
        graph.addEdge("J20", "M2", 1.5);
        graph.addEdge("J28", "J33", 1.2);
        graph.addEdge("J18", "J27", 1.1);
        graph.addEdge("J12", "J21", 1.0);

        List<String> nodes = graph.nodes;
        int n = nodes.size();
        System.out.println("Total Nodes: " + n);

        Map<String, Integer> nodeToIndex = new HashMap<>();
        for (int i = 0; i < n; i++) {
            nodeToIndex.put(nodes.get(i), i);
        }

        double[][] dist = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i == j) dist[i][j] = 0;
                else dist[i][j] = Double.MAX_VALUE / 2;
            }
        }

        double totalEdgeDist = 0;
        int edgeCount = 0;
        for (String u : nodes) {
            for (Edge e : graph.adjacency.get(u)) {
                dist[nodeToIndex.get(u)][nodeToIndex.get(e.to)] = e.distance;
                totalEdgeDist += e.distance;
                edgeCount++;
            }
        }

        System.out.println("Average Edge Distance: " + (totalEdgeDist / edgeCount));

        for (int k = 0; k < n; k++) {
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    if (dist[i][k] + dist[k][j] < dist[i][j]) {
                        dist[i][j] = dist[i][k] + dist[k][j];
                    }
                }
            }
        }

        double maxDist = 0;
        String farthestA = "";
        String farthestB = "";

        double totalShortestPathDist = 0;
        int pathCount = 0;
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (dist[i][j] < Double.MAX_VALUE / 2) {
                    if (dist[i][j] > maxDist) {
                        maxDist = dist[i][j];
                        farthestA = nodes.get(i);
                        farthestB = nodes.get(j);
                    }
                    totalShortestPathDist += dist[i][j];
                    pathCount++;
                }
            }
        }

        System.out.println(String.format("Max Distance: %.2f km (Between %s and %s)", maxDist, farthestA, farthestB));
        System.out.println(String.format("Average Nodes Distance: %.2f km", (totalShortestPathDist / pathCount)));
    }
}
