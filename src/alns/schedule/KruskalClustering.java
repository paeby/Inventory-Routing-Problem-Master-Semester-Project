package alns.schedule;

import java.util.ArrayList;
import java.util.HashMap;
import alns.schedule.Graph.*;
import java.util.Collections;
import alns.data.Point;
import alns.data.Data;
/**
 * Class used to compute the clusters of a schedule
 * 
 * @author prisca
 */
public class KruskalClustering {
    // represent the roots of the subsets of the union-find
    ArrayList<Point> initials;
    Graph graph;
    HashMap<Integer, ArrayList<Point>> clusters;
    ArrayList<Edge> mst;
    
    public KruskalClustering(ArrayList<Point> points, int k, Data data){
        initials = new ArrayList<>();
        clusters = new HashMap <>();
        graph = new Graph(points, data);
        getMST(graph, k);
        clusterization(); 
    }

    /** Return the Minimum Spanning Tree */
    private void getMST(Graph graph, int k){
        UnionFind uf = new UnionFind(graph.getVertexList());
        ArrayList<Edge> edgeList= graph.getEdgeList();
        mst =  new ArrayList<>();
        Collections.sort(edgeList);// sort the edges in ascending order

        int x;
        int y;
        for(int i = 0; i < edgeList.size(); i++){
            if(k == uf.getNumberSets()){// verify if there are k subsets 
                break;
            }
            x = edgeList.get(i).getBegin().GetSimpleDWid();
            y = edgeList.get(i).getAdjacent().GetSimpleDWid();
            if(uf.find(x) != uf.find(y)){
                uf.union(x, y);
                mst.add(edgeList.get(i));
            }
        }

        ArrayList<Integer> roots = uf.getRoots();
        for(int i = 0; i < roots.size(); i++){
            initials.add(graph.getVertexById(roots.get(i)));// add the roots vertexes
        }
    }
    
    /** Create the clusters	 */
    private void clusterization(){
        for(int i = 0; i < initials.size(); i++){
            Point initial = initials.get(i);
            clusters.put(i, new ArrayList<Point>());
            createCluster(initial, i);
        }
    }

    /** Grouped the elements of one cluster	 */
    private void createCluster(Point v, int cluster){
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
        return clusters.get(c);
    }
    
    private class UnionFind {
	private HashMap<Integer, Integer> ids; // stores the ids of the vertexes
	private HashMap<Integer, Integer> sizeSets;// stores the number of its elements, useful to balance the trees

	public UnionFind(ArrayList<Point> points){
            ids = new HashMap<>();
            sizeSets = new HashMap<>();

            for(Point p: points){
                ids.put(p.GetSimpleDWid(), p.GetSimpleDWid());
                sizeSets.put(p.GetSimpleDWid(), 1);
            }
	}

	/** Find the root of a element */
	public int find(int x){
            while(x != ids.get(x)){
                ids.put(x, ids.get(ids.get(x))); // path compression
                x = ids.get(x);
            }
            return x;
	}

	/** Joins two elements if them not belong to the same subset */
	public void union(int x, int y){
            int xRoot = find(x);
            int yRoot = find(y);

            if(xRoot != yRoot){
                if(sizeSets.get(xRoot) > sizeSets.get(yRoot)){// to balance the trees
                    ids.put(yRoot, xRoot);
                    sizeSets.put(xRoot, sizeSets.get(xRoot)+sizeSets.get(yRoot));
                }
                else{
                    ids.put(xRoot, yRoot);
                    sizeSets.put(yRoot, sizeSets.get(xRoot)+sizeSets.get(yRoot));
                }
            }
	}
	
	/** Return the number of subsets */
	public int getNumberSets() {
            int cont = 0;
            for(Integer i: ids.keySet()){
                if(i.equals(ids.get(i))){
                    cont++;
                }
            }
            return cont;
	}

	/** Return the roots of the subsets of the union-find */
	public ArrayList<Integer> getRoots(){
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
