package alns.schedule;

import java.util.ArrayList;
import java.util.HashMap;
import alns.data.Point;
import alns.data.Data;

/**
 * Class that represents a Graph for the Kruskal algorithm
 * 
 * @author https://github.com/roquelopez/Prim-and-Kruskal-Algorithm-for-Clustering/blob/master/src/code/Clustering.java
 * adapted by Prisca
 */
public class Graph {
    private HashMap<Point, ArrayList<Edge>> adjacents;
    private ArrayList<Point> vertexes;

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

    /** Create a new Edge between two vertexes */
    private void addEdge(Point a, Point b, double distance){
        adjacents.get(a).add(new Edge(a,b, distance));
        adjacents.get(b).add(new Edge(b, a, distance));
    }

    public ArrayList<Point> getVertexList(){
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
    
    public Point getVertexById(int i){
        for(Point p: vertexes){
            if(p.GetSimpleContWid() == i){
                return p;
            }
        }
        return null;
    }

    public ArrayList<Edge> getAdjacents(Point v){
        return adjacents.get(v);
    }

    public int getGraphSize(){
            return vertexes.size();
    }


    /**
     * Class that represents a edge in the graph
     */
    public class Edge implements Comparable<Edge>{
        private final Point begin;
        private final Point adjacent;
        private final double distance;
        private boolean labeled = false;

        public Edge(Point begin, Point adjacent, double distance) {
            this.begin = begin;
            this.adjacent = adjacent;
            this.distance = distance;
        }

        public Point getAdjacent(){
            return adjacent;
        }

        public Point getBegin(){
            return begin;
        }

        public Double getDistance(){
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