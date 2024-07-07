package outlierdetection;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import mtree.tests.Data;
import mtree.tests.MesureMemoryThread;
import mtree.utils.Constants;
import mtree.utils.Utils;

public class MicroCluster {

    public static HashMap<Data,ArrayList<MCObject>> microClusters = new HashMap<>();

    public static HashMap<Data,ArrayList<MCObject>> associateObjects = new HashMap<>();

    public static ArrayList<MCObject> PD = new ArrayList<>();

    // store list ob in increasing time arrival order
    public static ArrayList<MCObject> dataList = new ArrayList<>();

    public static MTreeClass mtree = new MTreeClass();

    public static PriorityQueue<MCObject> eventQueue = new PriorityQueue<>(new MCComparator());

    public static ArrayList<MCObject> outlierList = new ArrayList<>();

    public static double numberPointsInClusters = 0;
    public static double numberPointsInClustersAllWindows= 0;
    public static double numberCluster = 0;
    public static double numberPointsInEventQueue = 0;

    public static double avgPointsInRmc = 0;
    public static double avgPointsInRmcAllWindows = 0;
    public static double avgLengthExps = 0;
    public static double avgLengthExpsAllWindows = 0;
    public ArrayList<Data> detectOutlier(ArrayList<Data> data, int currentTime, int W, int slide) {
        // * purge expired objects
        purgeExpiredData(currentTime, slide);
        OutlierTest.computeOutlier(dataList, mtree);
        System.out.println("Outlier detected for expired");
        OutlierTest.compareOutlier(outlierList, dataList, mtree);
        System.out.println("Outlier compared for expired");

        // * process new incoming data
        long startTime = Utils.getCPUTime();
        data.stream()
            .map(MCObject::new)
            .peek((d) -> processData(d, currentTime, false))
            .forEach((d) -> dataList.add(d));
        ArrayList<Data> result = new ArrayList<>(outlierList);
        MesureMemoryThread.timeForNewSlide += Utils.getCPUTime() - startTime;
        OutlierTest.computeOutlier(dataList, mtree);
        System.out.println("Outlier detected for new");
        OutlierTest.compareOutlier(outlierList, dataList, mtree);
        System.out.println("Outlier compared for new");

//        numberCluster += microClusters.size();
//        if(numberPointsInEventQueue < eventQueue.size())
//            numberPointsInEventQueue = eventQueue.size();
//        HashSet<Integer> tempTest = new HashSet<>();
//        for(Data center: microClusters.keySet()){
//            ArrayList<MCObject> l = microClusters.get(center);
//            for(MCObject o:l){
//                if(o.arrivalTime >= currentTime - Constants.W){
//                    tempTest.add(o.arrivalTime);
//                    numberPointsInClusters++;
//                }
//            }
//        }
//        dataList.forEach((o) -> {
//            avgPointsInRmc += o.Rmc.size();
//            avgLengthExps += o.exps.size();
//        });
//        avgPointsInRmc = avgPointsInRmc/dataList.size();
//        avgLengthExps = avgLengthExps/dataList.size();
//        avgLengthExpsAllWindows += avgLengthExps;
//        avgPointsInRmcAllWindows += avgPointsInRmc;
//        numberPointsInClustersAllWindows += tempTest.size();
//        System.out.println("#points in clusters: "+numberPointsInClusters);
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
                ArrayList<MCObject> inCluster_objects2;
                inCluster_objects2 = microClusters.get(d.cluster);
                long startTime2 = Utils.getCPUTime();
                inCluster_objects2.remove(d); // 1.从 cluster 移除该点
                MesureMemoryThread.timeForIndexing += Utils.getCPUTime() - startTime2;
                //* check if size of cluster shrink below k + 1
                if (inCluster_objects2.size() < Constants.k + 1) {
                    processShrinkCluster(inCluster_objects2, currentTime); // 2.若移除之后 cluster 中点不够，则 shrink，部分点放到 eventQueue 中
                }
            } else { // d is in PD
                long startTime2 = Utils.getCPUTime();
                PD.remove(d); // 1.从 PD 移除该点
                d.Rmc.stream()
                    .map((c) -> associateObjects.get(c))
                    .forEach((list_associates) -> list_associates.remove(d)); // 2.从关联该点的 cluster 中移除该点
                MesureMemoryThread.timeForIndexing += Utils.getCPUTime() - startTime2;
            }
        }
        dataList.subList(0, slide).clear();
        processEventQueue(expiredData, currentTime); // ! 之前的是减少对 cluster 的干扰，现在是处理 expiredData 自己
        MesureMemoryThread.timeForExpireSlide += Utils.getCPUTime() - startTime;
    }

    private void processShrinkCluster(ArrayList<MCObject> inCluster_objects, int currentTime) {
        // * 1.PD 中的部分点引用该 cluster，这些 PD 中删除这些引用，确实需借助 associate_objects 2.从 mtree, micro_clusters, associate_objects 中移除 cluster
        // * 3.将这些点下放到 PD 中，看是否能够再凑齐 cluster

        long startTime = Utils.getCPUTime();
        MCObject cluster = inCluster_objects.get(0).cluster;
        ArrayList<MCObject> list_associates = associateObjects.get(cluster);
        if (list_associates != null)
            list_associates.forEach((o) -> o.Rmc.remove(cluster));
        mtree.remove(cluster);
        associateObjects.remove(cluster);
        microClusters.remove(cluster);

        MesureMemoryThread.timeForIndexing += Utils.getCPUTime() - startTime;
        inCluster_objects.forEach((d) -> {
            d.cluster = null;
            d.isInCluster = false;
            d.isCenter = false;
            if(d.arrivalTime > currentTime - Constants.W) {
                processData(d, currentTime, true); // * only need to set its own neighbors, do not need to update other neighbors
                // * actually, need to update neighbors which are from this cluster.
                d.fromShrinkCluster = true;
            }
        });
        inCluster_objects.forEach((d) -> {
            if(d.arrivalTime > currentTime - Constants.W) {
                d.fromShrinkCluster = false;
            }
        });
    }

    // 除了将 d 放到 cluster 里，还要增加 PD 中点的邻居
    public void addObjectToCluster(MCObject d, MCObject cluster, boolean fromCluster) {
        d.cluster = cluster;
        d.isInCluster = true;

        ArrayList<MCObject> list = microClusters.get(cluster);
        if (list.contains(d)) {
            throw new RuntimeException("Duplicate object in micro_cluster");
        }
        list.add(d);
        microClusters.put(cluster, list);

        // * evaluate distance between the new object and objects in PD that associate with cluster
        ArrayList<MCObject> objects = associateObjects.get(cluster);
        if (objects != null) {
            if (fromCluster) {
                List<MCObject> collect = objects.stream().filter(o -> o.fromShrinkCluster).collect(Collectors.toList());
                objects = (ArrayList<MCObject>) collect;
            }
            updateNeighborsOfList(objects, d);
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

    private void rangeQueryInPDAndCluster(MTreeClass.Query query, MCObject d, boolean fromCluster) {
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
            applyEventBasedAlgo(query, neighbor_in_PD, d, fromCluster);
        }
    }

    // 1.设置自己 2.设置自己影响的点，包括 cluster associateObjects 与邻居情况
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
        if (microClusters.containsKey(d))
            throw new RuntimeException("d should not have been a cluster center before");
        microClusters.put(d, neighbor_in_R2);
        mtree.add(d);

        // update Rmc for points in PD
        neighbor_in_3_2Apart_PD.forEach((o) -> o.Rmc.add(d));
        associateObjects.put(d, neighbor_in_3_2Apart_PD); // associate_objects value 是 PD 中的点

        // * 对于新插入的点 d，更新 neighbor_in_3_2Apart_PD 点的邻居
        if (fromCluster) {
            List<MCObject> collect = neighbor_in_3_2Apart_PD.stream().filter(o -> o.fromShrinkCluster).collect(Collectors.toList());
            if (!collect.isEmpty()) {
                updateNeighborsOfList(new ArrayList<>(collect), d);
            }
        } else {
            updateNeighborsOfList(neighbor_in_3_2Apart_PD, d);
        }
    }

    private void applyEventBasedAlgo(MTreeClass.Query query, ArrayList<MCObject> neighbor_in_PD, MCObject d, boolean fromCluster) {
        // * 当成普通的点处理：1.查 cluster 和 PD 中的邻居数，更新 d.exps 与 succeeding 2.更新 d.Rmc
        // 1.查 cluster 中的邻居数，并更新 d.Rmc 和 associate_objects
        ArrayList<MCObject> neighborInMTree = new ArrayList<>();
        for (MTreeClass.ResultItem ri2 : query) {
            d.Rmc.add(new MCObject(ri2.data));
            ArrayList<MCObject> l = associateObjects.getOrDefault(ri2.data, new ArrayList<>());
            l.add(d);
            associateObjects.put(ri2.data, l);
            ArrayList<MCObject> object_in_cluster = microClusters.get(ri2.data);
            if (object_in_cluster == null) throw new RuntimeException("no object in cluster");
            for (MCObject o : object_in_cluster) {
                if (mtree.getDistanceFunction().calculate(d, o) <= Constants.R) {
                    neighborInMTree.add(o); // 记录 d 的在 cluster 中的邻居
                }
            }
        }
        // 2.PD 中的点的 exps 与 succeeding 要因 d 更新：设置 PD 中的点的 exps 与 succeeding + 设置 d 的 exps 与 succeeding
        Stream<MCObject> stream = neighbor_in_PD.stream();
        if (fromCluster) { // wzm: should not to update neighbors when d is from cluster
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

        // 3.d 的 exps 和 succeeding 因 PD 中的点和 cluster 点更新
        neighbor_in_PD.forEach((o) -> {
            updateNeighbors(o, d);
        });
        neighborInMTree.forEach((o) -> {
            updateNeighbors(o, d);
        });
        //4.判别 d 的 outlier 状态，进行对应的处理
        PD.add(d);
        d.exps.sort(Collections.reverseOrder());
        for (int i = d.exps.size() - 1; i >= Constants.k - d.numberOfSucceeding && i >= 0; i--) {
            d.exps.remove(i); // 当 d.exps.size() + d.numberOfSucceeding > k 时，去掉多余的 d.exps 中的点，但是要剩 k - d.numberOfSucceeding 个，即 i = k - d.numberOfSucceeding - 1
        }
        if (d.numberOfSucceeding + d.exps.size() < Constants.k) {
            outlierList.add(d); // outlier 无需再设置 ev，无需添加到 eventQueue 中
        } else if (d.numberOfSucceeding + d.exps.size() >= Constants.k && !d.exps.isEmpty()) {
            // * keep k most recent preceding neighbors
            d.ev = min(d.exps);
            eventQueue.add(d); // eventQueue 看的是 element.ev
        }
    }

    private void updateNeighbors(MCObject neighbor, MCObject updatingObject) {
        if ((updatingObject.arrivalTime - 1) / Constants.slide == (neighbor.arrivalTime - 1) / Constants.slide
                || neighbor.arrivalTime > updatingObject.arrivalTime) { // neighbor 在 updatingObject 的后面
            updatingObject.numberOfSucceeding++; // 操作的都是 PD 中的点，不操作 neighbor
            updatingObject.succeedings.add(neighbor.arrivalTime);
        } else { // neighbor 在 updatingObject 的前面
            updatingObject.exps.add(neighbor.arrivalTime + Constants.W);
        }
    }

    public void processData(MCObject d, int currentTime, boolean fromCluster) {
        if (d.arrivalTime <= currentTime - Constants.W) {
            throw new RuntimeException("d.arrivalTime <= currentTime - Constants.W");
        }
        MTreeClass.Query query = mtree.getNearestByRange(d, Constants.R * 3 / 2);
        double min_distance = Double.MAX_VALUE;
        MTreeClass.ResultItem ri = null;
        if (query.iterator().hasNext()) {
            ri = query.iterator().next();
            min_distance = ri.distance;
            if (microClusters.get(ri.data).isEmpty() || microClusters.get(ri.data) == null) {
                throw new RuntimeException("no object in cluster");
            }
        }

        if (min_distance <= Constants.R / 2) {
            // * assign to this closet cluster
            MCObject closest_cluster = new MCObject(Objects.requireNonNull(ri).data);
            addObjectToCluster(d, closest_cluster, fromCluster);
        } else {
            // * do range query in PD and MTree (distance to center <= 3/2R)
            rangeQueryInPDAndCluster(query, d, fromCluster);
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

class MCObject extends Data {
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