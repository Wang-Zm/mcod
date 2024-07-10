package outlierdetection;

import java.util.ArrayList;

import mtree.tests.Data;

public class MCObject extends Data {
    public MCObject cluster;
    public ArrayList<Integer> exps;
    public ArrayList<MCObject> Rmc;

    public int ev;
    public boolean isInCluster;
    public boolean isCenter;

    public int numberOfSucceeding;
    public ArrayList<Integer> succeedings;
    public boolean fromShrinkCluster;

    public MCObject(Data d) {
        super();
        this.arrivalTime = d.arrivalTime;
        this.values = d.values;

        cluster = null;
        isInCluster = false;
        isCenter = false;
        exps = new ArrayList<>();
        succeedings = new ArrayList<>();
        numberOfSucceeding = 0;
        ev = -1;
        Rmc = new ArrayList<>();
        fromShrinkCluster = false;
    }
}