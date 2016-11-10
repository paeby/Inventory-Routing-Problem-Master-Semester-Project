package alns.schedule;

import java.util.ArrayList;
import java.util.HashMap;
import alns.schedule.Graph.*;
import java.util.Collections;
import alns.data.Point;
import alns.data.Data;

/**
 * Class used to compute the clusters of a schedule using Kruskal's algorithm for the 
 * minimum spanning tree problem, stopping when k connected components are left.
 * 
 * @author prisca
 */
public class KruskalClustering {
    // Represents the roots of the subsets of the union-find
    ArrayList<Point> initials;
    // Edges for all the containers
    Graph graph;
    // It stores the clusters (list of containers)
    HashMap<Integer, ArrayList<Point>> clusters;
    // List of the edges in the minimum spanning tree
    ArrayList<Edge> mst;
    
    /**
     * Creates a fully connected graph with the given points
     * and generates the minimum spanning tree until
     * k connected components are left
     * 
     * @param points
     * @param k
     * @param data
     */
    public KruskalClustering(ArrayList<Point> points, int k, Data data){
        initials = new ArrayList<>();
        clusters = new HashMap <>();
        graph = new Graph(points, data);
        getMST(graph, k);
        clusterization(); 
    }

    /**
     * It runs the Kruskal's algorithm for the MST problem until
     * k clusters are generated
     * 
     * @param graph
     * @param k 
     */
    private void getMST(Graph graph, int k){
        UnionFind uf = new UnionFind(graph.getVertexList());
        // Edges of the graph
        ArrayList<Edge> edgeList= graph.getEdgeList();
        mst =  new ArrayList<>();
        // Sort the edges in ascending order
        Collections.sort(edgeList);
        
        int x;
        int y;
        
        for(int i = 0; i < edgeList.size(); i++){
            // Verify if there are k subsets 
            if(k == uf.getNumberSets()){
                break;
            }
            x = edgeList.get(i).getBegin().GetSimpleDWid();
            y = edgeList.get(i).getAdjacent().GetSimpleDWid();
            // If x and y don't belong to the same set, make the union of the two sets
            // and add the edge to the minimum spanning tree
            if(uf.find(x) != uf.find(y)){
                uf.union(x, y);
                mst.add(edgeList.get(i));
            }
        }
        // Add the roots vertexes to the initials list
        ArrayList<Integer> roots = uf.getRoots();
        for(int i = 0; i < roots.size(); i++){
            initials.add(graph.getVertexById(roots.get(i)));
        }
    }
    
    /**
     * Goes through all the initial points of each cluster and 
     * adds them into their corresponding cluster.
     */
    private void clusterization(){
        for(int i = 0; i < initials.size(); i++){
            Point initial = initials.get(i);
            clusters.put(i, new ArrayList<Point>());
            createCluster(initial, i);
        }
    }
    
    /**
     * Recursively add the points of one cluster, following the neighbour points
     * 
     * @param v point to add to the cluster
     * @param cluster corresponding cluster id for the given point v
     */
    private void createCluster(Point v, int cluster){
        // Add the point to its corresponding cluster
        clusters.get(cluster).add(v);
        // Goes through the edges of the minimum spanning tree
        for(int i = 0; i < mst.size(); i++ ){
            // If the edge hasn't been visited yet, 
            // checks if the point is a starting or ending point of an edge in the MST
            if(! mst.get(i).isLabeled()){
                if(v.equals(mst.get(i).getAdjacent())){
                    mst.get(i).setLabeled();
                    // Creates the cluster for its neighbour
                    createCluster(mst.get(i).getBegin(), cluster);
                }
                else if(v.equals(mst.get(i).getBegin())){
                    mst.get(i).setLabeled();
                    // Creates the cluster for its neighbour
                    createCluster(mst.get(i).getAdjacent(), cluster);
                }
            }
        }
    }
    
    /**
     * Returns the cluster given its id
     * 
     * @param c id of the desired cluster
     * @return the corresponding cluster 
     */
    public ArrayList<Point> getCluster(int c){
        return clusters.get(c);
    }
    
    /**
     * Union-find data structure 
     */
    private class UnionFind {
        // Stores the ids of the vertexes with their corresponding root id
	private final HashMap<Integer, Integer> ids; 
        // Stores the number of its elements to balance the trees
	private final HashMap<Integer, Integer> sizeSets;
        
        /**
         * Constructor of the UnionFind datastructure. Each point is inserted
         * in its own cluster at the beginning, so its corresponding id in the ids
         * Map is itself
         * 
         * @param points 
         */
	private UnionFind(ArrayList<Point> points){
            ids = new HashMap<>();
            sizeSets = new HashMap<>();
            
            // Adds each point to its own cluster
            for(Point p: points){
                ids.put(p.GetSimpleDWid(), p.GetSimpleDWid());
                sizeSets.put(p.GetSimpleDWid(), 1);
            }
	}
        
        /**
         * Returns unique representative for the set containing x
         * 
         * @param x 
         * @return unique representative for the set containing x
         */
	private int find(int x){
            // The root has itself as an idea in the ids' Map
            while(x != ids.get(x)){
                // Path compression
                ids.put(x, ids.get(ids.get(x))); 
                x = ids.get(x);
            }
            return x;
	}
        
        /**
         * Makes the union of the set containing x with the set containing y
         * 
         * @param x 
         * @param y 
         */
	private void union(int x, int y){
            int xRoot = find(x);
            int yRoot = find(y);
            
            // If they are not already in the same set
            if(xRoot != yRoot){
                // If x-set is larger than y-set, we add y-set into x-set
                if(sizeSets.get(xRoot) > sizeSets.get(yRoot)){
                    // Update root
                    ids.put(yRoot, xRoot);
                    // Update size
                    sizeSets.put(xRoot, sizeSets.get(xRoot)+sizeSets.get(yRoot));
                }
                // If x-set is smaller than y-set, we add x-set into y-set
                else{
                    ids.put(xRoot, yRoot);
                    sizeSets.put(yRoot, sizeSets.get(xRoot)+sizeSets.get(yRoot));
                }
            }
	}
        
        /**
         * @return number of distinct sets
         */
	private int getNumberSets() {
            int cont = 0;
            for(Integer i: ids.keySet()){
                if(i.equals(ids.get(i))){
                    cont++;
                }
            }
            return cont;
	}
        
        /**
         * @return list of representatives for the sets
         */
	private ArrayList<Integer> getRoots(){
            ArrayList<Integer> roots = new ArrayList<>();
            for(Integer i: ids.keySet()){
                if(i.equals(ids.get(i))){
                    roots.add(i);
                }
            }
            return roots;
	}
    }
}
