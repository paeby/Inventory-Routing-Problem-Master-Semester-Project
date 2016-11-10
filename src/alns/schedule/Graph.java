package alns.schedule;

import java.util.ArrayList;
import java.util.HashMap;
import alns.data.Point;
import alns.data.Data;

/**
 * Class that represents a Graph for the KruskalClustering algorithm
 * 
 * @author prisca
 */
public class Graph {
    // For each point, its neighbours are stored in a map
    private final HashMap<Point, ArrayList<Edge>> adjacents;
    // Stores points of the graph
    private final ArrayList<Point> vertexes;
    
    /**
     * Creates a graph from the given points. An edge is added between all the
     * points. 
     * 
     * @param points list of points to create the graph
     * @param data contains the distance between the points
     */
    public Graph(ArrayList<Point> points, Data data) {
        adjacents = new HashMap<>();
        vertexes = points;
        
        for(Point p: points) {
            adjacents.put(p, new ArrayList<Edge>());
        }
        
        // Generate edges between all vertices
        for(int i = 0; i < vertexes.size(); i++){
            for(int j = i+1; j < vertexes.size(); j++){
                double distance = data.GetDistance(vertexes.get(i), vertexes.get(j));
                addEdge(vertexes.get(i), vertexes.get(j), distance);
            }
        }
    }

    /**
     * Create a new Edge between two vertexes, in both direction 
     * 
     * @param a begin/end of edge 
     * @param b begin/end of edge
     * @param distance between a and b
     */
    private void addEdge(Point a, Point b, double distance){
        adjacents.get(a).add(new Edge(a,b, distance));
        adjacents.get(b).add(new Edge(b, a, distance));
    }
    
    /**
     * @return points of the graph
     */
    public ArrayList<Point> getVertexList(){
        return vertexes;
    }

    /**
     * @return list of all edges
     */
    public ArrayList<Edge> getEdgeList(){
        ArrayList<Edge> tmp = new ArrayList<>();
        for(int i = 0; i < vertexes.size(); i++){
            tmp.addAll(adjacents.get(vertexes.get(i)));
        }
        return tmp;
    }
    
    /**
     * Returns a point given its id
     * 
     * @param i id
     * @return point given its id i
     */
    public Point getVertexById(int i){
        for(Point p: vertexes){
            if(p.GetSimpleContWid() == i){
                return p;
            }
        }
        return null;
    }
    
    /**
     * Return the neighbours of a point
     * 
     * @param v point
     * @return the neighbours of point v
     */
    public ArrayList<Edge> getAdjacents(Point v){
        return adjacents.get(v);
    }
    
    /**
     * @return number of points in the graph
     */
    public int getGraphSize(){
            return vertexes.size();
    }
    
    /**
     * Describes an edge of the graph: its starting point begin, ending point adjacent
     * and the distance between those two points
     */
    public class Edge implements Comparable<Edge>{
        private final Point begin;
        private final Point adjacent;
        private final double distance;
        // Used to create the clusters in order to know which edges have been already visited
        private boolean labeled = false;
        
        /**
         * Constructor of an edge between begin and adjacent with a known distance
         * 
         * @param begin starting point
         * @param adjacent ending point
         * @param distance between begin and adjacent
         */
        public Edge(Point begin, Point adjacent, double distance) {
            this.begin = begin;
            this.adjacent = adjacent;
            this.distance = distance;
        }
        
        /**
         * @return adjacent point
         */
        public Point getAdjacent(){
            return adjacent;
        }
        
        /**
         * @return begin point
         */
        public Point getBegin(){
            return begin;
        }

        /**
         * @return distance of edge
         */
        public Double getDistance(){
            return distance;
        }

        /**
         * @return if the edge has been visited
         */        
        public boolean isLabeled(){
            return labeled;
        }
        
        /**
         * set the edge as visited
         */
        public void setLabeled(){
            labeled = true;
        }

        @Override
        public int compareTo(Edge arg) {
            if(this.getDistance() < arg.getDistance()){
                return -1;
            }
            if(this.getDistance() > arg.getDistance()){
                return 1;
            }
            return 0;
        }
    }
}