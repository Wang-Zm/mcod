package outlierdetection;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import mtree.tests.Data;
import mtree.tests.MeasureMemoryThread;
import mtree.utils.Constants;
import mtree.utils.Utils;

public class MicroCluster {

    public static HashMap<Data,ArrayList<MCObject>> microClusters = new HashMap<>();

    public static HashMap<Data,ArrayList<MCObject>> associateObjects = new HashMap<>();

    public static ArrayList<MCObject> PD = new ArrayList<>();

    // store list ob in increasing time arrival order
    public static ArrayList<MCObject> dataList = new ArrayList<>();

    public static MTreeClass mtree = new MTreeClass();
    public static HashSet<Data> mTreeNodeList = new HashSet<>();

    public static PriorityQueue<MCObject> eventQueue = new PriorityQueue<>(new MCComparator());

    public static ArrayList<MCObject> outlierList = new ArrayList<>();

    public static MCObject deleteData = null;
    
    public static double numberPointsInClusters = 0;
    public static double numberPointsInClustersAllWindows= 0;
    public static double numberCluster = 0;
    public static double numberPointsInEventQueue = 0;

    public static double avgPointsInRmc = 0;
    public static double avgPointsInRmcAllWindows = 0;
    public static double avgLengthExps = 0;
    public static double avgLengthExpsAllWindows = 0;
    public ArrayList<Data> detectOutlier(ArrayList<Data> data, int currentTime, int W, int slide) {        
        // * purge expired data
        purgeExpiredData(currentTime, slide);

        // * process new incoming data
        long startTime = Utils.getCPUTime();
        data.stream()
            .map(MCObject::new)
            .peek((d) -> processData(d, currentTime, false))
            .forEach((d) -> dataList.add(d));
        ArrayList<Data> result = new ArrayList<>(outlierList);
        MeasureMemoryThread.timeForNewSlide += Utils.getCPUTime() - startTime;
        // OutlierTest.computeOutlier(dataList, mtree);
        // System.out.println("Outlier detected for new");
        // OutlierTest.compareOutlier(outlierList, dataList, mtree);
        // System.out.println("Outlier compared for new");
        
        numberCluster += microClusters.size();
        numberPointsInEventQueue += eventQueue.size();
        numberPointsInClusters += W - PD.size();
        return result;
    }

    private void purgeExpiredData(int currentTime, int slide) {
        if (dataList.isEmpty()) return;
        long startTime = Utils.getCPUTime();
        ArrayList<MCObject> expiredData = new ArrayList<>();
        for (int i = 0; i < slide; i++) {
            MCObject d = dataList.get(i);
            expiredData.add(d);
            if (d.isInCluster) {
                long startTime2 = Utils.getCPUTime();
                ArrayList<MCObject> inClusterObjects = microClusters.get(d.cluster);
                MeasureMemoryThread.removeTimesForObjectsInClusterWhenExpiring++;
                inClusterObjects.remove(d);
                if (inClusterObjects.size() < Constants.k + 1) {
                    long startShrink = Utils.getCPUTime();
                    processShrinkCluster(inClusterObjects, currentTime);
                    MeasureMemoryThread.timeForShrinkCluster += Utils.getCPUTime() - startShrink;
                }
                MeasureMemoryThread.timeForExpireDataInCluster += Utils.getCPUTime() - startTime2;
            } else { // d is in PD
                long startTime2 = Utils.getCPUTime();
                PD.remove(d);
                d.Rmc.stream()
                    .map((c) -> associateObjects.get(c))
                    .forEach((list_associates) -> list_associates.remove(d));
                MeasureMemoryThread.timeForExpireDataInPD += Utils.getCPUTime() - startTime2;
            }
        }
        dataList.subList(0, slide).clear();
        long startTime2 = Utils.getCPUTime();
        processEventQueue(expiredData, currentTime);
        MeasureMemoryThread.timeForProcessEventQueue += Utils.getCPUTime() - startTime2;
        MeasureMemoryThread.timeForExpireSlide += Utils.getCPUTime() - startTime;
    }

    private void processShrinkCluster(ArrayList<MCObject> inCluster_objects, int currentTime) {
        long startTime = Utils.getCPUTime();
        MCObject cluster = inCluster_objects.get(0).cluster;
        ArrayList<MCObject> list_associates = associateObjects.get(cluster);
        if (list_associates != null)
            list_associates.forEach((o) -> o.Rmc.remove(cluster));
        mtree.remove(cluster);
        mTreeNodeList.remove(cluster);
        associateObjects.remove(cluster);
        microClusters.remove(cluster);

        MeasureMemoryThread.timeForIndexing += Utils.getCPUTime() - startTime;
        inCluster_objects.forEach((d) -> {
            d.cluster = null;
            d.isInCluster = false;
            d.isCenter = false;
            if(d.arrivalTime > currentTime - Constants.W) {
                processData(d, currentTime, true);
                d.fromShrinkCluster = true;
            }
        });
        inCluster_objects.forEach((d) -> {
            if(d.arrivalTime > currentTime - Constants.W) {
                d.fromShrinkCluster = false;
            }
        });
    }

    public void addObjectToCluster(MCObject d, MCObject cluster, boolean fromCluster) {
        d.cluster = cluster;
        d.isInCluster = true;

        ArrayList<MCObject> list = microClusters.get(cluster);
        list.add(d);
        microClusters.put(cluster, list);

        // * evaluate distance between the new object and objects in PD that associate with cluster
        ArrayList<MCObject> objects = associateObjects.get(cluster);
        if (objects != null) {
            if (fromCluster) {
                List<MCObject> collect = objects.stream().filter(o -> o.fromShrinkCluster).collect(Collectors.toList());
                objects = (ArrayList<MCObject>) collect;
            }
            long startTime = Utils.getCPUTime();
            updateNeighborsOfList(objects, d);
            if (!fromCluster) {
                MeasureMemoryThread.timeForAddObjectToClusterUpdateNeighbors += Utils.getCPUTime() - startTime;
            }
        }
    }

    private void updateNeighborsOfList(ArrayList<MCObject> objects, MCObject d) {
        objects.forEach((o) -> {
            double distance = mtree.getDistanceFunction().calculate(d, o);
            if (distance <= Constants.R) {
                updateNeighbors(d, o);
                // check if o is an inlier
                if (o.exps.size() + o.numberOfSucceeding >= Constants.k && outlierList.contains(o)) {
                    outlierList.remove(o);
                    // add o to event queue
                    if (!o.exps.isEmpty()) {
                        o.ev = min(o.exps);
                        eventQueue.add(o);
                    }
                }
            }
        });
    }

    private void rangeQueryInPDAndCluster(ArrayList<MTreeClass.ResultItem> results, MCObject d, boolean fromCluster) {
        ArrayList<MCObject> neighbor_in_PD = new ArrayList<>();
        ArrayList<MCObject> neighbor_in_3_2Apart_PD = new ArrayList<>();
        ArrayList<MCObject> neighbor_in_R2 = new ArrayList<>();
        PD.forEach((m) -> {
            double distance = mtree.getDistanceFunction().calculate(d, m);
            if (distance <= Constants.R / 2) {
                neighbor_in_R2.add(m);
                neighbor_in_PD.add(m);
            } else if (distance <= Constants.R) {
                neighbor_in_PD.add(m);
                neighbor_in_3_2Apart_PD.add(m);
            } else if (distance <= Constants.R * 3 / 2) {
                neighbor_in_3_2Apart_PD.add(m);
            }
        });
        if (neighbor_in_R2.size() > Constants.k * 1.1) {
            formNewCluster(neighbor_in_R2, neighbor_in_3_2Apart_PD, d, fromCluster);
        } else {
            applyEventBasedAlgo(results, neighbor_in_PD, d, fromCluster);
        }
    }

    private void formNewCluster(ArrayList<MCObject> neighbor_in_R2, ArrayList<MCObject> neighbor_in_3_2Apart_PD, MCObject d, boolean fromCluster) {
        neighbor_in_R2.add(d);
        for (MCObject o : neighbor_in_R2) {
            if(o.isInCluster && o.arrivalTime != d.arrivalTime)
                throw new RuntimeException("o should not be in cluster if o is not d");
            o.cluster = d;
            o.isInCluster = true;
            o.isCenter = false;
            o.numberOfSucceeding = 0;
            o.succeedings.clear();
            o.exps.clear();
            o.ev = -1;
            PD.remove(o);
            eventQueue.remove(o);
            outlierList.remove(o);
        }
        for (MCObject o : neighbor_in_R2) {
            for (MCObject cluster : o.Rmc) {
                if (!associateObjects.get(cluster).remove(o)) {
                    throw new RuntimeException("cluster " + cluster.arrivalTime + " is not associated with object " + o);
                }
            }
            o.Rmc.clear();
        }
        d.isCenter = true;
        microClusters.put(d, neighbor_in_R2);
        mtree.add(d);
        mTreeNodeList.add(d);

        // update Rmc for points in PD
        neighbor_in_3_2Apart_PD.forEach((o) -> o.Rmc.add(d));
        associateObjects.put(d, neighbor_in_3_2Apart_PD);

        if (fromCluster) {
            List<MCObject> collect = neighbor_in_3_2Apart_PD.stream().filter(o -> o.fromShrinkCluster).collect(Collectors.toList());
            if (!collect.isEmpty()) {
                updateNeighborsOfList(new ArrayList<>(collect), d);
            }
        } else {
            updateNeighborsOfList(neighbor_in_3_2Apart_PD, d);
        }
    }

    private void applyEventBasedAlgo(ArrayList<MTreeClass.ResultItem> results, ArrayList<MCObject> neighbor_in_PD, MCObject d, boolean fromCluster) {
        ArrayList<MCObject> neighborInMTree = new ArrayList<>();
        if (!results.isEmpty()) {
            for (MTreeClass.ResultItem ri2 : results) {
                d.Rmc.add((MCObject) ri2.data);
                ArrayList<MCObject> l = associateObjects.getOrDefault(ri2.data, new ArrayList<>());
                l.add(d);
                associateObjects.put(ri2.data, l);
                ArrayList<MCObject> object_in_cluster = microClusters.get(ri2.data);
                if (object_in_cluster == null) throw new RuntimeException("no object in cluster");
                for (MCObject o : object_in_cluster) {
                    if (mtree.getDistanceFunction().calculate(d, o) <= Constants.R) {
                        neighborInMTree.add(o);
                    }
                }
            }        
        }
        
        Stream<MCObject> stream = neighbor_in_PD.stream();
        if (fromCluster) {
            stream = stream.filter(o -> o.fromShrinkCluster);
        }
        stream.peek((o) -> {
            updateNeighbors(d, o);
            // * check for o becomes inlier
        }).filter((o) -> (o.numberOfSucceeding + o.exps.size() >= Constants.k && outlierList.contains(o))
        ).peek((o) -> outlierList.remove(o)
        ).peek((o) -> {
            if (!o.exps.isEmpty()) o.ev = min(o.exps);
        }).forEach((o) -> {
            eventQueue.add(o);
        });

        neighbor_in_PD.forEach((o) -> {
            updateNeighbors(o, d);
        });
        neighborInMTree.forEach((o) -> {
            updateNeighbors(o, d);
        });
        PD.add(d);
        d.exps.sort(Collections.reverseOrder());
        for (int i = d.exps.size() - 1; i >= Constants.k - d.numberOfSucceeding && i >= 0; i--) {
            d.exps.remove(i);
        }
        if (d.numberOfSucceeding + d.exps.size() < Constants.k) {
            outlierList.add(d);
        } else if (d.numberOfSucceeding + d.exps.size() >= Constants.k && !d.exps.isEmpty()) {
            // * keep k most recent preceding neighbors
            d.ev = min(d.exps);
            eventQueue.add(d);
        }
    }

    private void updateNeighbors(MCObject neighbor, MCObject updatingObject) {
        if ((updatingObject.arrivalTime - 1) / Constants.slide == (neighbor.arrivalTime - 1) / Constants.slide
                || neighbor.arrivalTime > updatingObject.arrivalTime) {
            updatingObject.numberOfSucceeding++;
            updatingObject.succeedings.add(neighbor.arrivalTime);
        } else {
            updatingObject.exps.add(neighbor.arrivalTime + Constants.W);
        }
    }

    public void processData(MCObject d, int currentTime, boolean fromCluster) {
        if (d.arrivalTime <= currentTime - Constants.W) {
            throw new RuntimeException("d.arrivalTime <= currentTime - Constants.W");
        }
        long startTime = Utils.getCPUTime();
        MTreeClass.Query query = mtree.getNearestByRange(d, Constants.R * 3 / 2);
        double min_distance = Double.MAX_VALUE;
        MTreeClass.ResultItem ri = null;
        ArrayList<MTreeClass.ResultItem> results = new ArrayList<>();
        for (MTreeClass.ResultItem node : query) {
            if (mTreeNodeList.contains(node.data)) {
                results.add(node);
            }
        }
        if (!results.isEmpty()) {
            ri = results.get(0);
            min_distance = ri.distance;
        }
        if (!fromCluster) {
            MeasureMemoryThread.timeForQuerying += Utils.getCPUTime() - startTime;
        }

        if (min_distance <= Constants.R / 2) {
            // * assign to this closet cluster
            long startTime2 = Utils.getCPUTime();
            MCObject closest_cluster = (MCObject) (ri.data);
            addObjectToCluster(d, closest_cluster, fromCluster);
            if (!fromCluster) {
                MeasureMemoryThread.timeForAddObjectToCluster += Utils.getCPUTime() - startTime2;
            }
        } else {
            // * do range query in PD and MTree (distance to center <= 3/2R)
            long startTime2 = Utils.getCPUTime();
            rangeQueryInPDAndCluster(results, d, fromCluster);
            if (!fromCluster) {
                MeasureMemoryThread.timeForRangeQueryInPDAndCluster += Utils.getCPUTime() - startTime2;
            }
        }
    }

    private void processEventQueue(ArrayList<MCObject> expireData, int currentTime) {
        expireData.forEach((p) -> outlierList.remove(p));
        MCObject x = eventQueue.peek();
        while (x != null && x.ev <= currentTime) {
            x = eventQueue.poll();
            for (int i = x.exps.size() - 1; i >= 0; i--) {
                if (x.exps.get(i) <= currentTime) x.exps.remove(i);
            }
            if (x.exps.size() + x.numberOfSucceeding < Constants.k) {
                outlierList.add(x);
            } else if (!x.exps.isEmpty()) {
                x.ev = min(x.exps);
                eventQueue.add(x);
            }
            x = eventQueue.peek();
        }
        outlierList.forEach((d) -> {
            for (int k = d.exps.size() - 1; k >= 0; k--) {
                if (d.exps.get(k) <= currentTime) d.exps.remove(k);
            }
        });
    }

    private int min(ArrayList<Integer> exps) {
        int min = exps.get(0);
        for (Integer i : exps)
            if (i < min) min = i;
        return min;
    }

    private static class OutlierTest {
        private static final int[] numNeighbors = new int[Constants.W];
        private static final ArrayList<MCObject> outlierList = new ArrayList<>();
        private static final ArrayList<Integer> numNeighborsOfOutliers = new ArrayList<>();

        public static void computeOutlier(ArrayList<MCObject> dataList, MTreeClass mTree) {
            Arrays.fill(numNeighbors, 0);
            for (int i = 0; i < dataList.size(); i++) {
                for (int j = i + 1; j < dataList.size(); j++) {
                    if (mTree.getDistanceFunction().calculate(dataList.get(i), dataList.get(j)) <= Constants.R) {
                        numNeighbors[i]++;
                        numNeighbors[j]++;
                    }
                }
            }
            outlierList.clear();
            numNeighborsOfOutliers.clear();
            for (int i = 0; i < dataList.size(); i++) {
                if (numNeighbors[i] < Constants.k) {
                    outlierList.add(dataList.get(i));
                    numNeighborsOfOutliers.add(numNeighbors[i]);
                }
            }
        }

        public static void compareOutlier(ArrayList<MCObject> MCODOutlier, ArrayList<MCObject> dataList, MTreeClass mTree) {
            if (MCODOutlier.size() != outlierList.size()) {
                System.out.println("MCODOutlier.size() != outlierList.size(), MCODOutlier.size()=" + MCODOutlier.size() + ", outlierList.size()=" + outlierList.size());
                MCODOutlier.sort(Comparator.comparingInt((MCObject o) -> o.arrivalTime));
                outlierList.sort(Comparator.comparingInt((MCObject o) -> o.arrivalTime));
                for (int i = 1; i < MCODOutlier.size(); i++) {
                    if (MCODOutlier.get(i).arrivalTime < MCODOutlier.get(i - 1).arrivalTime) {
                        throw new RuntimeException("MCODOutlier.get(i).arrivalTime < MCODOutlier.get(i - 1).arrivalTime");
                    }
                }
                for (int i = 0; i < MCODOutlier.size() && i < outlierList.size(); i++) {
                    if (MCODOutlier.get(i) != outlierList.get(i)) {
                        printNeighbors(i, MCODOutlier);

                        System.out.println("MCODOutlier:");
                        printMCODOutlier(MCODOutlier);
                        System.out.println("outlierList:");
                        printOutlier();

                        printRealNumNeighbors(MCODOutlier.get(i), mTree, "MCODOutlier");
                        printRealNumNeighbors(outlierList.get(i), mTree, "outlierList");
                        printExps(i, mTree);
                        printSucceeding(i, mTree);
                        System.exit(-1);
                    }
                }
            }
            MCODOutlier.sort(Comparator.comparingInt((MCObject o) -> o.arrivalTime));
            outlierList.sort(Comparator.comparingInt((MCObject o) -> o.arrivalTime));
            for (int i = 0; i < MCODOutlier.size(); i++) {
                if (MCODOutlier.get(i) != outlierList.get(i)) {
                    printNeighbors(i, MCODOutlier);
                    System.exit(-1);
                }
            }
        }

        public static void printMCODOutlier(ArrayList<MCObject> outliers) {
            for (int i = 0; i < outliers.size(); i++) {
                System.out.printf("%d, %d, exps.size()=%d, numberOfSucceeding=%d\n", i, outliers.get(i).arrivalTime, outliers.get(i).exps.size(), outliers.get(i).numberOfSucceeding);
            }
        }

        private static void printOutlier() {
            for (int i = 0; i < outlierList.size(); i++) {
                System.out.printf("%d, %d, numNeighborsOfOutliers=%d, exps.size()=%d, numberOfSucceeding=%d\n", i, outlierList.get(i).arrivalTime, numNeighborsOfOutliers.get(i), outlierList.get(i).exps.size(), outlierList.get(i).numberOfSucceeding);
            }
        }

        private static void printNeighbors(int i, ArrayList<MCObject> MCODOutlier) {
            System.out.println("MCODOutlier.get(i) != outlierList.get(i), i=" + i + ", MCODOutlier.get(i).arrivalTime=" + MCODOutlier.get(i).arrivalTime + ", outlierList.get(i).arrivalTime=" + outlierList.get(i).arrivalTime);
            System.out.println("MCODOutlier.get(i).exps.size()=" + MCODOutlier.get(i).exps.size() + ", MCODOutlier.get(i).numberOfSucceeding=" + MCODOutlier.get(i).numberOfSucceeding);
            System.out.println("numNeighborsOfOutliers.get(i)=" + numNeighborsOfOutliers.get(i));
        }

        private static void printExps(int i, MTreeClass mTree) {
            ArrayList<Integer> exps = outlierList.get(i).exps;
            for (int i1 = 0; i1 < exps.size(); i1++) {
                for (MCObject mcObject : dataList) {
                    if (mcObject.arrivalTime == exps.get(i1)) {
                        double dist = mTree.getDistanceFunction().calculate(mcObject, outlierList.get(i));
                        System.out.printf("%d, %d, dist=%f\n", i1, exps.get(i1), dist);
                    }
                }
            }
            HashSet<Integer> set = new HashSet<>(exps);
            System.out.println("expsSet.size()=" + set.size()); // succeeding 中有重复的
        }

        private static void printSucceeding(int i, MTreeClass mTree) {
            ArrayList<Integer> succ = outlierList.get(i).succeedings;
            for (int i1 = 0; i1 < succ.size(); i1++) {
                for (MCObject mcObject : dataList) {
                    if (mcObject.arrivalTime == succ.get(i1)) {
                        double dist = mTree.getDistanceFunction().calculate(mcObject, outlierList.get(i));
                        System.out.printf("%d, %d, dist=%f\n", i1, succ.get(i1), dist);
                    }
                }
            }
            HashSet<Integer> set = new HashSet<>(succ);
            System.out.println("succSet.size()=" + set.size()); // succeeding 中有重复的
        }

        private static void printRealNumNeighbors(MCObject d, MTreeClass mTree, String name) {
            int _numNeighbors = 0;
            for (MCObject mcObject : dataList) {
                if (mTree.getDistanceFunction().calculate(d, mcObject) <= Constants.R) {
                    _numNeighbors++;
                }
            }
            _numNeighbors--;
            System.out.println(name + "_realNumNeighbors=" + _numNeighbors);
        }
    }
}

class MCComparator implements Comparator<MCObject> {
    @Override
    public int compare(MCObject o1, MCObject o2) {
        return Integer.compare(o1.ev, o2.ev);
    }
}