import re
import heapq

def calculate_distances():
    file_path = r'c:\Users\Yakup Yılmaz\Desktop\tez\tezprojesi-project\tezprojesi-project\tezprojesi-project\src\main\java\com\tezprojesi\api\config\DevDataSeeder.java'
    with open(file_path, 'r', encoding='utf-8') as f:
        content = f.read()

    # Regex to find addBoth(graph, "From", "To", distance)
    # and addTollBoth(graph, "From", "To", distance, toll)
    edge_pattern = re.compile(r'add(?:Toll)?Both\(graph,\s*"([^"]+)",\s*"([^"]+)",\s*([\d.]+)')
    
    adj = {}
    nodes = set()
    
    for match in edge_pattern.finditer(content):
        u, v, dist = match.groups()
        dist = float(dist)
        nodes.add(u)
        nodes.add(v)
        if u not in adj: adj[u] = []
        if v not in adj: adj[v] = []
        adj[u].append((v, dist))
        adj[v].append((u, dist))

    def dijkstra(start):
        dists = {node: float('inf') for node in nodes}
        dists[start] = 0
        pq = [(0, start)]
        while pq:
            d, u = heapq.heappop(pq)
            if d > dists[u]: continue
            if u in adj:
                for v, weight in adj[u]:
                    if dists[u] + weight < dists[v]:
                        dists[v] = dists[u] + weight
                        heapq.heappush(pq, (dists[v], v))
        return dists

    targets = ["MAIN", "M1", "M2", "M3", "M4", "M5"]
    matrix = {}
    for t in targets:
        matrix[t] = dijkstra(t)

    print("Distance Matrix (KM):")
    print("      " + " ".join(f"{t:>6}" for t in targets))
    for t1 in targets:
        row = f"{t1:5}"
        for t2 in targets:
            d = matrix[t1].get(t2, -1)
            row += f" {d:6.1f}"
        print(row)

if __name__ == "__main__":
    calculate_distances()
