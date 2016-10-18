package alns.schedule;

import java.util.ArrayList;
import java.util.HashMap;
import alns.data.Point;
        
/**
 * Class that represents a Graph for the Kruskal algorithm
 * 
 * @author https://github.com/roquelopez/Prim-and-Kruskal-Algorithm-for-Clustering/blob/master/src/code/Clustering.java
 * adapted by Prisca
 */
public class Graph {
    private HashMap<Integer, Point> vertexIds;
    private HashMap<Vertex, ArrayList<Edge>> adjacents;
    private ArrayList<Vertex> vertexes;

    public Graph(ArrayList<Point> points) {
        adjacents = new HashMap<>();
        vertexes = new ArrayList<>();
        vertexIds = new HashMap<>();
        
        for(int i = 0; i < points.size(); i++){
            addVertex(i, points.get(i).GetLat(), points.get(i).GetLon());
            vertexIds.put(i, points.get(i));
        }
        
        // Generate edges between all vertices
        for(int i = 0; i < vertexes.size(); i++){
            for(int j = i+1; j < vertexes.size(); j++){
                addEdge(vertexes.get(i), vertexes.get(j));
            }
        }
    }

    /** Create a new Vertex */
    private void addVertex(int id, double x, double y){
        Vertex node = new Vertex(id, x, y);
        adjacents.put(node, new ArrayList<Edge>());
        vertexes.add(node);
    }

    /** Create a new Edge between two vertexes */
    private void addEdge(Vertex a, Vertex b){
        double distance = euclidianDistance(a, b);
        adjacents.get(a).add(new Edge(a,b, distance));
        adjacents.get(b).add(new Edge(b, a, distance));
    }

    public ArrayList<Vertex> getVertexList(){
        return vertexes;
    }

    /** Return a list of all edges */
    public ArrayList<Edge> getEdgeList(){
        ArrayList<Edge> tmp = new ArrayList<>();
        for(int i = 0; i < vertexes.size(); i++){
            tmp.addAll(adjacents.get(vertexes.get(i)));
        }
        return tmp;
    }
    
    public HashMap<Integer, Point> getIdsMap(){
        return vertexIds;
    }
    
    public Vertex getVertexById(int i){
        return vertexes.get(i);
    }

    public ArrayList<Edge> getAdjacents(Vertex v){
        return adjacents.get(v);
    }

    public int getGraphSize(){
            return vertexes.size();
    }

    /** Return the Euclidian distance between two vertexes */
    public double euclidianDistance(Vertex a, Vertex b){
            return Math.sqrt(Math.pow((a.getX() - b.getX()), 2) + Math.pow((a.getY() - b.getY()), 2));
    }

    /**
     * Class that represents a vertex in the graph
     */
    public class Vertex{
        private int id;
        private double x, y;// the priority attribute is used in the Heap

        public Vertex(int id, double x, double y){
            this.id = id;
            this.x = x;
            this.y = y;
        }

        @Override
        public boolean equals(Object obj){
            if(obj == null) 
                return false;
            if (this.getClass() == obj.getClass()){
                Vertex myValueObject = (Vertex) obj;
                if (myValueObject.getId() == this.getId()){
                    return true;
                }
            }
            return false;
        }

        public int getId(){
            return id;
        }

        public double getX(){
            return x;
        }

        public double getY(){
            return y;
        }
    }

    /**
     * Class that represents a edge in the graph
     */
    public class Edge implements Comparable<Edge>{
        private Vertex begin;
        private Vertex adjacent;
        private double distance;
        private boolean labeled = false;

        public Edge(Vertex begin, Vertex adjacent, double distance) {
            this.begin = begin;
            this.adjacent = adjacent;
            this.distance = distance;
        }

        public Vertex getAdjacent(){
            return adjacent;
        }

        public Vertex getBegin(){
            return begin;
        }

        public double getDistance(){
            return distance;
        }

        public boolean isLabeled(){
            return labeled;
        }

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