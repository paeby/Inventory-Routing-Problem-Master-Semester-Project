package alns.schedule;

import java.util.ArrayList;
import java.util.HashMap;
import alns.schedule.Graph.*;
import java.util.Collections;
import alns.data.Point;
/**
 * Class used to compute the clusters of a schedule
 * 
 * @author prisca
 */
public class KruskalClustering {
    // represent the roots of the subsets of the union-find
    ArrayList<Vertex> initials;
    Graph graph;
    HashMap<Integer, ArrayList<Vertex>> clusters;
    ArrayList<Edge> mst;
    
    public KruskalClustering(ArrayList<Point> points, int k){
        initials = new ArrayList<>();
        clusters = new HashMap <>();
        graph = new Graph(points);
        mst = getMST(graph, k);
        clusterization(); 
    }

    /** Return the Minimum Spanning Tree */
    private ArrayList<Edge> getMST(Graph graph, int k){
        UnionFind uf = new UnionFind(graph.getGraphSize());
        ArrayList<Edge> edgeList= graph.getEdgeList();
        ArrayList<Edge> mst =  new ArrayList<>();
        Collections.sort(edgeList);// sort the edges in ascending order
        int x;
        int y;
        for(int i = 0; i < edgeList.size(); i++){
            if(k == uf.getNumberSets())// verify if there are k subsets 
                break;
            x = edgeList.get(i).getBegin().getId();
            y = edgeList.get(i).getAdjacent().getId();
            if(uf.find(x) != uf.find(y)){
                uf.union(x, y);
                mst.add(edgeList.get(i));
            }
        }

        ArrayList<Integer> roots = uf.getRoots();
        for(int i = 0; i < roots.size(); i++){
            initials.add(graph.getVertexById(roots.get(i)));// add the roots vertexes
        }
        
        return mst;
    }
    
    /** Create the clusters	 */
    private void clusterization(){
        for(int i = 0; i < initials.size(); i++){
            Vertex initial = initials.get(i);
            clusters.put(i, new ArrayList<Vertex>());
            createCluster(initial, i);
        }
    }

    /** Grouped the elements of one cluster	 */
    private void createCluster(Vertex v, int cluster){
        clusters.get(cluster).add(v);
        for(int i = 0; i < mst.size(); i++ ){
            if(! mst.get(i).isLabeled()){
                if(v.equals(mst.get(i).getAdjacent())){
                    mst.get(i).setLabeled();
                    createCluster(mst.get(i).getBegin(), cluster);
                }
                else if(v.equals(mst.get(i).getBegin())){
                    mst.get(i).setLabeled();
                    createCluster(mst.get(i).getAdjacent(), cluster);
                }
            }
        }
    }
    
    public ArrayList<Point> getCluster(int c){
        ArrayList<Vertex> vertices = clusters.get(c);
        ArrayList<Point> pointIds = new ArrayList<>();
        for(Vertex v: vertices){
            pointIds.add(graph.getIdsMap().get(v.getId()));
        }
        return pointIds;
    }
    
    private class UnionFind {
	private int [] ids; // stores the ids of the vertexes
	private int [] sizeSets;// stores the number of its elements, useful to balance the trees

	public UnionFind(int numElements){
            ids = new int[numElements];
            sizeSets = new int[numElements];

            for(int i=0; i < numElements; i++){
                ids[i] = i;
                sizeSets[i] = 1;
            }
	}

	/** Find the root of a element */
	public int find(int x){
            while(x != ids[x]){
                ids[x] = ids[ids[x]]; // path compression
                x = ids[x];
            }
            return x;
	}

	/** Joins two elements if them not belong to the same subset */
	public void union(int x, int y){
            int xRoot = find(x);
            int yRoot = find(y);

            if(xRoot != yRoot){
                if(sizeSets[xRoot] > sizeSets[yRoot]){// to balance the trees
                    ids[yRoot] = xRoot;
                    sizeSets[xRoot] += sizeSets[yRoot];
                }
                else{
                    ids[xRoot] = yRoot;
                    sizeSets[yRoot] += sizeSets[xRoot];
                }
            }
	}
	
	/** Return the number of subsets */
	public int getNumberSets() {
            int cont = 0;
            for(int i=0; i < ids.length; i++){
                if(i == ids[i])
                    cont += 1;
            }
            return cont;
	}

	/** Return the roots of the subsets of the union-find */
	public ArrayList<Integer> getRoots(){
            ArrayList<Integer> roots = new ArrayList<>();
            for(int i=0; i < ids.length; i++){
                if(i == ids[i])
                    roots.add(i);
            }
            return roots;
	}
    }
}
