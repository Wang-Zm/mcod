package outlierdetection;

import java.lang.reflect.Array;
import java.util.*;

import mtree.tests.Data;
import mtree.tests.MesureMemoryThread;
import mtree.utils.Constants;
import mtree.utils.Utils;

public class MicroCluster {

    public static HashMap<Data,ArrayList<MCObject>> microClusters = new HashMap<>();

    // 影响每个 cluster 的 PD 中的点（是否是 PD 中的？）
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
        //* purge expired objects
        long startTime = Utils.getCPUTime();
        ArrayList<MCObject> expiredData = new ArrayList<>();
        int index = -1;
        for (int i = 0; i < dataList.size(); i++) {
            MCObject d = dataList.get(i);
            if (d.arrivalTime <= currentTime - W) {
                index = i;
                expiredData.add(d);
                if (d.isInCluster) {
                    ArrayList<MCObject> inCluster_objects2;
                    inCluster_objects2 = microClusters.get(d.cluster);
                    long startTime2 = Utils.getCPUTime();
                    inCluster_objects2.remove(d);
                    MesureMemoryThread.timeForIndexing += Utils.getCPUTime() - startTime2;
                    //* check if size of cluster shrink below k + 1
                    if (inCluster_objects2.size() < Constants.k + 1) {
                        processShrinkCluster(inCluster_objects2, currentTime);
                    }
                } else { // d is in PD
                    long startTime2 = Utils.getCPUTime();
                    PD.remove(d);
                    d.Rmc.stream()
                    .map((c) -> associateObjects.get(c))
                    .forEach((list_associates) -> list_associates.remove(d));
                    MesureMemoryThread.timeForIndexing += Utils.getCPUTime() - startTime2;
                }
            } else {
                break;
            }
        }
        processEventQueue(expiredData, currentTime);
        if (index >= 0) {
            dataList.subList(0, index + 1).clear();
        }
        MesureMemoryThread.timeForExpireSlide += Utils.getCPUTime() - startTime;
        OutlierTest.computeOutlier(dataList, mtree);
        System.out.println("Outlier detected for expired");
        OutlierTest.compareOutlier(outlierList);
        System.out.println("Outlier compared for expired");

        // * process new incoming data
        // do range query with MTree of cluster centers
        startTime = Utils.getCPUTime();
        data.stream()
        .map(MCObject::new)
        .peek((d) -> processData(d, currentTime))
        .forEach((d) -> dataList.add(d));
        ArrayList<Data> result = new ArrayList<>(outlierList);
        MesureMemoryThread.timeForNewSlide += Utils.getCPUTime() - startTime;
        OutlierTest.computeOutlier(dataList, mtree);
        System.out.println("Outlier detected for new");
        OutlierTest.compareOutlier(outlierList);
        System.out.println("Outlier compared for new");

        numberCluster += microClusters.size();
        if(numberPointsInEventQueue < eventQueue.size())
            numberPointsInEventQueue = eventQueue.size();
        HashSet<Integer> tempTest = new HashSet<>();
        for(Data center: microClusters.keySet()){
            ArrayList<MCObject> l = microClusters.get(center);
            for(MCObject o:l){
                if(o.arrivalTime >= currentTime - Constants.W){
                    tempTest.add(o.arrivalTime);
                    numberPointsInClusters++;
                }
            }
        }
        dataList.forEach((o) -> {
            avgPointsInRmc += o.Rmc.size();
            avgLengthExps += o.exps.size();
        });
        avgPointsInRmc = avgPointsInRmc/dataList.size();
        avgLengthExps = avgLengthExps/dataList.size();
        avgLengthExpsAllWindows += avgLengthExps;
        avgPointsInRmcAllWindows += avgPointsInRmc;
        numberPointsInClustersAllWindows += tempTest.size();
        System.out.println("#points in clusters: "+numberPointsInClusters);
        return result;
    }

    public void print_cluster() {
        microClusters.keySet().stream().map((o) -> {
            System.out.println("Center: " + o.values[0]);
            return o;
        }).map((o) -> {
            System.out.print("Member:");
            return o;
        }).map((o) -> {
            microClusters.get(o).forEach((o2) -> {
                System.out.print(o2.values[0] + " ; ");
            });
            return o;
        }).forEach((_item) -> {
            System.out.println();
        });
        System.out.println();
    }

    public void print_outlier() {
        System.out.println("Outliers: ");
        outlierList.forEach((o) -> {
            System.out.print(o.values[0] + " ; ");
        });
        System.out.println();
    }

    public void print_PD() {
        System.out.println();
        System.out.println("PD list: ");
        PD.forEach((o) -> {
            System.out.print(o.values[0] + " ; ");
        });
        System.out.println();
    }

    private void processShrinkCluster(ArrayList<MCObject> inCluster_objects, int currentTime) {
        // * 1.PD 中的部分点引用该 cluster，这些 PD 中删除这些引用，确实需借助 associate_objects 2.从 mtree, micro_clusters, associate_objects 中移除 cluster
        // * 3.将这些点下放到 PD 中，看是否能够再凑齐 cluster；或参考现在的实现，使用 process_data 方法当作新点处理

        long startTime = Utils.getCPUTime();
        ArrayList<MCObject> list_associates = associateObjects.get(inCluster_objects.get(0).cluster);
        if (list_associates != null)
            list_associates.forEach((o) -> o.Rmc.remove(inCluster_objects.get(0).cluster));
        mtree.remove(inCluster_objects.get(0).cluster);
        associateObjects.remove(inCluster_objects.get(0).cluster);
        microClusters.remove(inCluster_objects.get(0).cluster);

        MesureMemoryThread.timeForIndexing += Utils.getCPUTime() - startTime;
        inCluster_objects.forEach((d) -> {
            d.cluster = null;
            d.isInCluster = false;
            d.isCenter = false;
            d.numberOfSucceeding = 0;
            d.exps.clear();
            d.ev = -1;
            d.Rmc.clear();
            if(d.arrivalTime > currentTime - Constants.W) {
                processData(d, currentTime);
            }
        });
    }

    public void addObjectToCluster(MCObject d, MCObject cluster) {
        d.cluster = cluster;
        d.isInCluster = true;

        // 放到 micro cluster 中
        ArrayList<MCObject> list = microClusters.get(cluster);
        if (list.contains(d)) {
            throw new RuntimeException("Duplicate object in micro_cluster");
        }
        list.add(d);
        microClusters.put(cluster, list);

        // * evaluate distance between the new object and objects in PD that associate with cluster
        ArrayList<MCObject> objects = associateObjects.get(cluster); // 都是 PD 中的点
        if (objects != null) {
            objects.forEach((o) -> {
                double distance = mtree.getDistanceFunction().calculate(d, o);
                if (distance <= Constants.R) {
                    if (o.arrivalTime / Constants.slide == d.arrivalTime / Constants.slide || d.arrivalTime > o.arrivalTime) { // d 在 o 的后面
                        o.numberOfSucceeding++; // 操作的都是 PD 中的点，不操作 d
                    } else { // d 在 o 的前面
                        o.exps.add(d.arrivalTime + Constants.W);
                    }
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
    }

    private void rangeQueryInPDAndCluster(MTreeClass.Query query, MCObject d) {
        ArrayList<MCObject> neighbor_in_PD = new ArrayList<>();
        ArrayList<MCObject> neighbor_in_3_2Apart_PD = new ArrayList<>();
        ArrayList<MCObject> neighbor_in_R2 = new ArrayList<>();
        PD.forEach((m) -> {
            double distance = mtree.getDistanceFunction().calculate(d, m);
            if (distance <= Constants.R / 2) neighbor_in_R2.add(m);
            if (distance <= Constants.R) {
                neighbor_in_PD.add(m);
                neighbor_in_3_2Apart_PD.add(m);
            } else if (distance <= Constants.R * 3 / 2) {
                neighbor_in_3_2Apart_PD.add(m);
            }
        });
        if (neighbor_in_R2.size() > Constants.k * 1.1) {
            formNewCluster(neighbor_in_R2, neighbor_in_3_2Apart_PD, d);
        } else {
            applyEventBasedAlgo(query, neighbor_in_PD, d);
        }
    }

    private void formNewCluster(ArrayList<MCObject> neighbor_in_R2, ArrayList<MCObject> neighbor_in_3_2Apart_PD, MCObject d) {
        neighbor_in_R2.add(d);
        for (MCObject o : neighbor_in_R2) {
            if(o.isInCluster && o.arrivalTime != d.arrivalTime)
                throw new RuntimeException("o should not be in cluster if o is not d");
            o.cluster = d;
            o.isInCluster = true;
            o.isCenter = false;
            o.numberOfSucceeding = 0;
            o.exps.clear();
            o.ev = -1;
            o.Rmc.clear();
            PD.remove(o);
            eventQueue.remove(o);
            outlierList.remove(o);
        }
        d.isCenter = true;
        if (microClusters.containsKey(d))
            throw new RuntimeException("d should not have been a cluster center before");
        microClusters.put(d, neighbor_in_R2);
        mtree.add(d);

        // update Rmc for points in PD
        neighbor_in_3_2Apart_PD.forEach((o) -> o.Rmc.add(d));
        associateObjects.put(d, neighbor_in_3_2Apart_PD); // associate_objects value 是 PD 中的点
    }

    private void applyEventBasedAlgo(MTreeClass.Query query, ArrayList<MCObject> neighbor_in_PD, MCObject d) {
        // * 当成普通的点处理：1.查 cluster 和 PD 中的邻居数，更新 d.exps 与 succeeding 2.更新 d.Rmc
        // 1.查 cluster 中的邻居数，并更新 d.Rmc 和 associate_objects
        ArrayList<MCObject> neighbor_in_mtree = new ArrayList<>();
        for (MTreeClass.ResultItem ri2 : query) {
            if (ri2.distance == 0) d.values[0] += (new Random()).nextDouble() / 1000000; // TODO: why
            d.Rmc.add((MCObject) ri2.data); // 意味着 d 受这个 cluster 影响
            ArrayList<MCObject> l = associateObjects.getOrDefault(ri2.data, new ArrayList<>());
            l.add(d);
            associateObjects.put(ri2.data, l);
            ArrayList<MCObject> object_in_cluster = microClusters.get(ri2.data);
            if (object_in_cluster != null) {
                for (MCObject o : object_in_cluster) {
                    if (mtree.getDistanceFunction().calculate(d, o) <= Constants.R) {
                        neighbor_in_mtree.add(o); // 记录 d 的在 cluster 中的邻居
                    }
                }
            }
        }
        // 2.PD 中的点的 exps 与 succeeding 要因 d 更新：设置 PD 中的点的 exps 与 succeeding + 设置 d 的 exps 与 succeeding
        neighbor_in_PD.stream().peek((o) -> { // 此时 d 不一定在 o 的前面
            if (o.arrivalTime / Constants.slide == d.arrivalTime / Constants.slide || d.arrivalTime > o.arrivalTime) { // d 在 o 的后面
                o.numberOfSucceeding++; // 操作的都是 PD 中的点，不操作 d
            } else { // d 在 o 的前面
                o.exps.add(d.arrivalTime + Constants.W);
            }
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
            // 对 d 而言，o 可能和 d 处于同一次滑动，或 o 在 d 的前面滑动中
            if(o.arrivalTime / Constants.slide == d.arrivalTime / Constants.slide || d.arrivalTime < o.arrivalTime) {
                d.numberOfSucceeding++;
            } else {
                d.exps.add(o.arrivalTime + Constants.W);
            }
        });
        neighbor_in_mtree.forEach((o) -> {
            // 对 d 而言，o 可能和 d 处于同一次滑动，或 o 在 d 的前面滑动中
            if(o.arrivalTime / Constants.slide == d.arrivalTime / Constants.slide || d.arrivalTime < o.arrivalTime) {
                d.numberOfSucceeding++;
            } else {
                d.exps.add(o.arrivalTime + Constants.W);
            }
        });
        //4.判别 d 的 outlier 状态，进行对应的处理
        PD.add(d); // d 一定在 PD 中
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

    public void processData(MCObject d, int currentTime) {
        if (d.arrivalTime <= currentTime - Constants.W) {
            throw new RuntimeException("d.arrivalTime <= currentTime - Constants.W");
        }
        MTreeClass.Query query = mtree.getNearestByRange(d, Constants.R * 3 / 2);
        double min_distance = Double.MAX_VALUE;
        MTreeClass.ResultItem ri = null;
        boolean isFoundCluster = false;
        if (query.iterator().hasNext()) {
            ri = query.iterator().next();
            min_distance = ri.distance;
            if (microClusters.get(ri.data) != null && !microClusters.get(ri.data).isEmpty())
                isFoundCluster = true; // 有邻居在 cluster 中
        }

        if (min_distance <= Constants.R / 2 && isFoundCluster) {
            // * assign to this closet cluster
            MCObject closest_cluster = (MCObject) ri.data;
            addObjectToCluster(d, closest_cluster);
        } else {
            // * do range query in PD and MTree (distance to center <= 3/2R)
            rangeQueryInPDAndCluster(query, d);
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

    /*
    1.每次滑动重新计算 outlier 是多少
    2.增加移除点后的 outlier 检查
    3.增加点进来后的 outlier 检查
    4.维护几份 dataList
     */
    private static class OutlierTest {
        private static final ArrayList<MCObject> outlierList = new ArrayList<>();
        private static final int[] numNeighbors = new int[Constants.W];

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
            for (int i = 0; i < dataList.size(); i++) {
                if (numNeighbors[i] < Constants.k) {
                    outlierList.add(dataList.get(i));
                }
            }
        }

        public static void compareOutlier(ArrayList<MCObject> MCODOutlier) {
            if (MCODOutlier.size() != outlierList.size()) {
                System.out.println("MCODOutlier.size() != outlierList.size(), MCODOutlier.size()=" + MCODOutlier.size() + ", outlierList.size()=" + outlierList.size());
                System.exit(-1);
            }
            for (int i = 0; i < MCODOutlier.size(); i++) {
                if (MCODOutlier.get(i) != outlierList.get(i)) {
                    System.out.println("MCODOutlier.get(i) != outlierList.get(i), i=" + i);
                    System.exit(-1);
                }
            }
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

    public MCObject(Data d) {
        super();
        this.arrivalTime = d.arrivalTime;
        this.values = d.values;

        cluster = null;
        isInCluster = false;
        isCenter = false;
        exps = new ArrayList<>();
        numberOfSucceeding = 0;
        ev = -1;
        Rmc = new ArrayList<>();
    }
}