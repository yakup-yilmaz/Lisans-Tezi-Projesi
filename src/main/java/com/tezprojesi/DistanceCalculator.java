package com.tezprojesi;

import com.tezprojesi.domain.Edge;
import com.tezprojesi.domain.Node;
import com.tezprojesi.domain.NodeType;
import com.tezprojesi.routing.CityGraph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DistanceCalculator {
    private static final double DISTANCE_SCALE = 3.0;

    public static void main(String[] args) {
        CityGraph graph = new CityGraph();
        
        graph.addNode(new Node("MAIN", NodeType.MAIN_DEPOT, 80, 180));
        graph.addNode(new Node("M1", NodeType.MICRO_DEPOT, 620, 90));
        graph.addNode(new Node("M2", NodeType.MICRO_DEPOT, 350, 180));

        List<String> intersections = new ArrayList<>();
        for (int index = 1; index <= 35; index++) {
            String id = "J" + index;
            int col = (index - 1) % 8;
            int row = (index - 1) / 8;
            graph.addNode(new Node(id, NodeType.INTERSECTION, 140 + (col * 70), 60 + (row * 45)));
            intersections.add(id);
        }

        for (int index = 1; index <= 12; index++) {
            String id = "C" + index;
            int anchor = (index - 1) % intersections.size();
            String linkedIntersection = intersections.get(anchor);
            Node base = graph.getNode(linkedIntersection);
            graph.addNode(new Node(id, NodeType.CUSTOMER, base.getX() + 32, base.getY() + 18));
            addBoth(graph, linkedIntersection, id, 0.8 + ((index % 3) * 0.2));
        }

        for (int index = 0; index < intersections.size(); index++) {
            String current = intersections.get(index);
            if (index + 1 < intersections.size()) {
                addBoth(graph, current, intersections.get(index + 1), 0.6 + ((index % 4) * 0.2));
            }
            if (index + 8 < intersections.size()) {
                addBoth(graph, current, intersections.get(index + 8), 0.9 + (((index + 1) % 3) * 0.2));
            }
        }

        addBoth(graph, "MAIN", "J1", 3.8);
        addBoth(graph, "MAIN", "J9", 4.2);
        addBoth(graph, "J16", "M1", 2.9);
        addBoth(graph, "J24", "M1", 2.6);
        addBoth(graph, "J20", "M2", 1.5);
        addBoth(graph, "J28", "J33", 1.2);
        addBoth(graph, "J18", "J27", 1.1);
        addBoth(graph, "J12", "J21", 1.0);

        List<String> nodes = graph.getNodes();
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
                else dist[i][j] = Double.MAX_VALUE / 2; // Prevent overflow on addition
            }
        }

        double totalEdgeDist = 0;
        int edgeCount = 0;
        for (String u : nodes) {
            for (Edge e : graph.getNeighbors(u)) {
                dist[nodeToIndex.get(u)][nodeToIndex.get(e.getToNodeId())] = e.getDistanceKm();
                totalEdgeDist += e.getDistanceKm();
                edgeCount++;
            }
        }

        System.out.println("Average Component Distance: " + (totalEdgeDist / edgeCount));

        // Floyd-Warshall
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

    private static void addBoth(CityGraph graph, String from, String to, double distanceKm) {
        double scaledDistance = distanceKm * DISTANCE_SCALE;
        graph.addEdge(new Edge(from, to, scaledDistance, 1.0));
        graph.addEdge(new Edge(to, from, scaledDistance, 1.0));
    }
}
