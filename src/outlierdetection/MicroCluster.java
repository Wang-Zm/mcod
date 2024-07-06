package outlierdetection;

import java.util.*;

import mtree.tests.Data;
import mtree.tests.MesureMemoryThread;
import mtree.utils.Constants;
import mtree.utils.Utils;

public class MicroCluster {

    public static HashMap<Data,ArrayList<MCObject>> micro_clusters = new HashMap<>();

    // 影响每个 cluster 的 PD 中的点（是否是 PD 中的？）
    public static HashMap<Data,ArrayList<MCObject>> associate_objects = new HashMap<>();

    public static ArrayList<MCObject> PD = new ArrayList<>();

    // store list ob in increasing time arrival order
    public static ArrayList<MCObject> dataList = new ArrayList<>();

    public static MTreeClass mtree = new MTreeClass();

    public static PriorityQueue<MCObject> eventQueue = new PriorityQueue<MCObject>(new MCComparator());

    public static ArrayList<MCObject> outlierList = new ArrayList<MCObject>();

    // public static ArrayList<MCObject> inCluster_objects = new ArrayList<MCObject>();
    
    public static HashSet<Integer> inClusters = new HashSet<>();
    
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
                    if (d.isCenter) {
                        inCluster_objects2 = micro_clusters.get(d);
                    } else {
                        inCluster_objects2 = micro_clusters.get(d.cluster); // update cluster
                    }
                    long startTime2 = Utils.getCPUTime();
                    inCluster_objects2.remove(d);
                    MesureMemoryThread.timeForIndexing += Utils.getCPUTime() - startTime2;
                    /* check if size of cluster shrink below k+1 */
                    if (inCluster_objects2.size() < Constants.k + 1) {
                        process_shrink_cluster(inCluster_objects2, currentTime);
                    }
                } else { // d is in PD
                    long startTime2 = Utils.getCPUTime();
                    PD.remove(d);
                    d.Rmc.stream().map((c) -> associate_objects.get(c)).filter(Objects::nonNull).forEach((list_associates) -> {
                        list_associates.remove(d);
                    });
                    MesureMemoryThread.timeForIndexing += Utils.getCPUTime() - startTime2;
                }
            } else {
                break;
            }
        }
        process_event_queue(expiredData, currentTime);
        if (index >= 0) {
            dataList.subList(0, index + 1).clear();
        }
        MesureMemoryThread.timeForExpireSlide += Utils.getCPUTime() - startTime;
        
        // * process new incoming data
        // do range query with mtree of cluster centers
        startTime = Utils.getCPUTime();
        data.stream()
        .map(MCObject::new)
        // .peek((d) -> process_data(d, currentTime, false))
        .peek((d) -> processDataNew(d, currentTime, false))
        .forEach((d) -> dataList.add(d));
        ArrayList<Data> result = new ArrayList<>(outlierList);
        MesureMemoryThread.timeForNewSlide += Utils.getCPUTime() - startTime;
        
        numberCluster += micro_clusters.size();
        if(numberPointsInEventQueue < eventQueue.size())
            numberPointsInEventQueue = eventQueue.size();
        HashSet<Integer> tempTest = new HashSet<>();
        for(Data center: micro_clusters.keySet()){
            ArrayList<MCObject> l = micro_clusters.get(center);
            for(MCObject o:l){
                if(o.arrivalTime >= currentTime - Constants.W){
                    tempTest.add(o.arrivalTime);
                    numberPointsInClusters++;
                }
            }
        }
        dataList.stream().forEach((o) -> {
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
        micro_clusters.keySet().stream().map((o) -> {
            System.out.println("Center: " + o.values[0]);
            return o;
        }).map((o) -> {
            System.out.print("Member:");
            return o;
        }).map((o) -> {
            micro_clusters.get(o).stream().forEach((o2) -> {
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
        outlierList.stream().forEach((o) -> {
            System.out.print(o.values[0] + " ; ");
        });
        System.out.println();
    }

    public void print_PD() {
        System.out.println();
        System.out.println("PD list: ");
        PD.stream().forEach((o) -> {
            System.out.print(o.values[0] + " ; ");
        });
        System.out.println();
    }

    private void process_shrink_cluster(ArrayList<MCObject> inCluster_objects, int currentTime) {
        // * 1.PD 中的部分点引用该 cluster，这些 PD 中删除这些引用，确实需借助 associate_objects 2.从 mtree, micro_clusters, associate_objects 中移除 cluster
        // * 3.将这些点下放到 PD 中，看是否能够再凑齐 cluster；或参考现在的实现，使用 process_data 方法当作新点处理

        long startTime = Utils.getCPUTime();
        ArrayList<MCObject> list_associates = associate_objects.get(inCluster_objects.get(0).cluster);
        if (list_associates != null)
            list_associates.stream().forEach((o) -> o.Rmc.remove(inCluster_objects.get(0).cluster));
        mtree.remove(inCluster_objects.get(0).cluster);
        associate_objects.remove(inCluster_objects.get(0).cluster);
        micro_clusters.remove(inCluster_objects.get(0).cluster);
        
        MesureMemoryThread.timeForIndexing += Utils.getCPUTime() - startTime;
        inCluster_objects.stream().forEach((d) -> {
            d.cluster = null;
            d.isInCluster = false;
            d.isCenter = false;
            d.numberOfSucceeding = 0;
            d.exps.clear();
            d.ev = 0;
            d.Rmc.clear();
            if(d.arrivalTime > currentTime - Constants.W) {
                // process_data(d, currentTime, true);
                processDataNew(d, currentTime, true);
            }
        });
    }

    public void addObjectToCluster(MCObject d, MCObject cluster, boolean fromCluster) {
        d.cluster = cluster;
        d.isInCluster = true;

        // 放到 micro cluster 中
        ArrayList<MCObject> list = micro_clusters.get(cluster);
        if(!list.contains(d))
            list.add(d);
        micro_clusters.put(cluster, list);
        
        // * evaluate distance between the new object and objects in PD that associate with cluster
        ArrayList<MCObject> objects = associate_objects.get(cluster); // 都是 PD 中的点
        if (objects != null) {
            objects.stream().forEach((o) -> {
                double distance = mtree.getDistanceFunction().calculate(d, o);
                if (distance <= Constants.R) {
                    // increase number if succeeding neighbors
                    if (o.arrivalTime < d.arrivalTime) {
                        if (!fromCluster) {
                            o.numberOfSucceeding++;
                        } else {
                            if((o.arrivalTime-1)/Constants.slide == (d.arrivalTime-1)/Constants.slide)
                                d.numberOfSucceeding++; // d 与 o 处于同一次滑动中
                            else
                                d.exps.add(o.arrivalTime + Constants.W); // 记录前面的邻居的过期时间
                        }
                    } else {
                        if (!fromCluster) {
                            if((o.arrivalTime-1)/Constants.slide == (d.arrivalTime-1)/Constants.slide)
                                o.numberOfSucceeding++;
                            else
                                o.exps.add(d.arrivalTime + Constants.W);
                        }
                        d.numberOfSucceeding++;
                    }
                    // check if o is inlier
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

    public void addNewObjectToCluster(MCObject d, MCObject cluster) {
        d.cluster = cluster;
        d.isInCluster = true; // TODO: 放到 inClusters 中？

        // 放到 micro cluster 中
        ArrayList<MCObject> list = micro_clusters.get(cluster);
        if(!list.contains(d))
            list.add(d);
        micro_clusters.put(cluster, list);

        // * evaluate distance between the new object and objects in PD that associate with cluster
        ArrayList<MCObject> objects = associate_objects.get(cluster); // 都是 PD 中的点
        if (objects != null) {
            objects.stream().forEach((o) -> {
                double distance = mtree.getDistanceFunction().calculate(d, o);
                if (distance <= Constants.R) {
                    o.numberOfSucceeding++;
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

    public void addOldObjectToCluster(MCObject d, MCObject cluster) {
        d.cluster = cluster;
        d.isInCluster = true; // TODO: 放到 inClusters 中？

        // 放到 micro cluster 中
        ArrayList<MCObject> list = micro_clusters.get(cluster);
        if(!list.contains(d))
            list.add(d);
        micro_clusters.put(cluster, list);

        // * evaluate distance between the new object and objects in PD that associate with cluster
        // TODO: 可以提取出来作为统一的部分
        ArrayList<MCObject> objects = associate_objects.get(cluster); // 都是 PD 中的点
        if (objects != null) {
            objects.stream().forEach((o) -> { // 处理方式应有差异
                double distance = mtree.getDistanceFunction().calculate(d, o);
                if (distance <= Constants.R) {
                    if ((o.arrivalTime -1 ) / Constants.slide == (d.arrivalTime-1) / Constants.slide || d.arrivalTime > o.arrivalTime) { // d 在 o 的后面
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

    private void rangeQueryInPDAndCluster(MTreeClass.Query query, MCObject d, boolean fromCluster) {
        ArrayList<MCObject> neighbor_in_PD = new ArrayList<>();
        ArrayList<MCObject> neighbor_in_3_2Apart_PD = new ArrayList<>();
        ArrayList<MCObject> neighbor_in_R2 = new ArrayList<>();
        PD.stream().forEach((m) -> {
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
            if (fromCluster) {
                applyEventBasedAlgoForOldObject(query, neighbor_in_PD, d);
            } else {
                applyEventBasedAlgoForNewObject(query, neighbor_in_PD, d);
            }
        }
    }

    private void formNewCluster(ArrayList<MCObject> neighbor_in_R2, ArrayList<MCObject> neighbor_in_3_2Apart_PD, MCObject d) {
        neighbor_in_R2.add(d);
        for (MCObject o : neighbor_in_R2) {
            if(o.isInCluster && o.arrivalTime != d.arrivalTime)
                throw new RuntimeException("o should not be in cluster if o is not d");
            o.isCenter = false;
            o.cluster = d;
            o.isInCluster = true;
            o.numberOfSucceeding = 0;
            o.exps.clear();
            o.Rmc.clear();
            if(inClusters.contains(o.arrivalTime))
                throw new RuntimeException("o should not be in inClusters");
            inClusters.add(o.arrivalTime);
            PD.remove(o);
            eventQueue.remove(o);
            outlierList.remove(o);
        }
        d.isCenter = true;

        if (micro_clusters.containsKey(d))
            throw new RuntimeException("d should not have been a cluster center before");
        micro_clusters.put(d, neighbor_in_R2);
        mtree.add(d);

        // update Rmc for points in PD
        neighbor_in_3_2Apart_PD.stream().forEach((o) -> o.Rmc.add(d));
        associate_objects.put(d, neighbor_in_3_2Apart_PD); // associate_objects value 是 PD 中的点
    }

    private void applyEventBasedAlgoForNewObject(MTreeClass.Query query, ArrayList<MCObject> neighbor_in_PD, MCObject d) {
        // * 当成普通的新点处理：1.查 cluster 和 PD 中的邻居数，更新 d.exps 与 succeeding 2.更新 d.Rmc
        // 1.查 cluster 中的邻居数，并更新 d.Rmc 和 associate_objects
        ArrayList<MCObject> neighbor_in_mtree = new ArrayList<>();
        for (MTreeClass.ResultItem ri2 : query) {
            if (ri2.distance == 0) d.values[0] += (new Random()).nextDouble() / 1000000; // ? 为什么
            d.Rmc.add((MCObject) ri2.data); // 意味着 d 受这个 cluster 影响
            ArrayList<MCObject> l = associate_objects.getOrDefault(ri2.data, new ArrayList<>());
            l.add(d);
            associate_objects.put(ri2.data, l);
            ArrayList<MCObject> object_in_cluster = micro_clusters.get(ri2.data);
            if (object_in_cluster != null) {
                for (MCObject o : object_in_cluster) {
                    if (mtree.getDistanceFunction().calculate(d, o) <= Constants.R) {
                        neighbor_in_mtree.add(o); // 记录 d 的在 cluster 中的邻居
                    }
                }
            }
        }
        // 2.PD 中的点的 exps 与 succeeding 要因 d 更新：设置 PD 中的点的 exps 与 succeeding + 设置 d 的 exps 与 succeeding
        neighbor_in_PD.stream().peek((o) -> {
            o.numberOfSucceeding++; // 对 o 而言，d 一定位于 o 的后面
            // * check for o becomes inlier
        }).filter((o) -> (o.numberOfSucceeding + o.exps.size() >= Constants.k && outlierList.contains(o))
        ).peek((o) -> outlierList.remove(o)
        ).peek((o) -> {
            if (!o.exps.isEmpty()) o.ev = min(o.exps);
        }).forEach((o) -> {
            eventQueue.add(o);
        });
        // 3.d 的 exps 和 succeeding 因 PD 中的点和 cluster 点更新
        neighbor_in_PD.stream().forEach((o) -> {
            // 对 d 而言，o 可能和 d 处于同一次滑动，或 o 在 d 的前面滑动中
            if((o.arrivalTime-1)/Constants.slide == (d.arrivalTime-1)/Constants.slide) {
                d.numberOfSucceeding++;
            } else {
                d.exps.add(o.arrivalTime + Constants.W);
            }
        });
        neighbor_in_mtree.stream().forEach((o) -> {
            // 对 d 而言，o 可能和 d 处于同一次滑动，或 o 在 d 的前面滑动中
            if((o.arrivalTime-1)/Constants.slide == (d.arrivalTime-1)/Constants.slide) {
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

    private void applyEventBasedAlgoForOldObject(MTreeClass.Query query, ArrayList<MCObject> neighbor_in_PD, MCObject d) {
        // * 当成普通的新点处理：1.查 cluster 和 PD 中的邻居数，更新 d.exps 与 succeeding 2.更新 d.Rmc
        // 1.查 cluster 中的邻居数，并更新 d.Rmc 和 associate_objects
        ArrayList<MCObject> neighbor_in_mtree = new ArrayList<>();
        for (MTreeClass.ResultItem ri2 : query) {
            if (ri2.distance == 0) d.values[0] += (new Random()).nextDouble() / 1000000; // ? 为什么
            d.Rmc.add((MCObject) ri2.data); // 意味着 d 受这个 cluster 影响
            ArrayList<MCObject> l = associate_objects.getOrDefault(ri2.data, new ArrayList<>());
            l.add(d);
            associate_objects.put(ri2.data, l);
            ArrayList<MCObject> object_in_cluster = micro_clusters.get(ri2.data);
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
            if ((o.arrivalTime - 1) / Constants.slide == (d.arrivalTime - 1) / Constants.slide || d.arrivalTime > o.arrivalTime) { // d 在 o 的后面
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
        neighbor_in_PD.stream().forEach((o) -> {
            // 对 d 而言，o 可能和 d 处于同一次滑动，或 o 在 d 的前面滑动中
            if((o.arrivalTime - 1) / Constants.slide == (d.arrivalTime - 1) / Constants.slide || d.arrivalTime < o.arrivalTime) {
                d.numberOfSucceeding++;
            } else {
                d.exps.add(o.arrivalTime + Constants.W);
            }
        });
        neighbor_in_mtree.stream().forEach((o) -> {
            // 对 d 而言，o 可能和 d 处于同一次滑动，或 o 在 d 的前面滑动中
            if((o.arrivalTime-1)/Constants.slide == (d.arrivalTime-1)/Constants.slide || d.arrivalTime < o.arrivalTime) {
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

    public void process_data(MCObject d, int currentTime, boolean fromCluster) {
        if(d.arrivalTime <= currentTime - Constants.W) return;
        long startTime = Utils.getCPUTime();
        MTreeClass.Query query = mtree.getNearestByRange(d, Constants.R * 3 / 2); // 由近到远返回若干个
        MesureMemoryThread.timeForQuerying += Utils.getCPUTime() - startTime;
        double min_distance = Double.MAX_VALUE;
        MTreeClass.ResultItem ri = null;
        boolean isFoundCluster = false;
        if (query.iterator().hasNext()) {
            ri = query.iterator().next();
            min_distance = ri.distance;
            if (micro_clusters.get(ri.data) != null && !micro_clusters.get(ri.data).isEmpty())
                isFoundCluster = true; // 有邻居在 cluster 中
        }

        if (min_distance <= Constants.R / 2 && isFoundCluster && !fromCluster) {
            // assign to this closet cluster；应该是与最近的 cluster 的 center 的距离小于 R / 2 添加到对应的 cluster 中
            MCObject closest_cluster = (MCObject) ri.data;
            long startTime2 = Utils.getCPUTime();
            addObjectToCluster(d, closest_cluster, fromCluster);
            MesureMemoryThread.timeForIndexing += Utils.getCPUTime() - startTime2;
        } else {
            // * do range query in PD and mtree (distance to center <= 3/2R)
            ArrayList<MCObject> neighbor_in_mtree = new ArrayList<>();
            ArrayList<MCObject> neighbor_in_PD = new ArrayList<>();
            ArrayList<MCObject> neighbor_in_3_2Apart_PD = new ArrayList<>();
            ArrayList<MCObject> neighbor_in_R2 = new ArrayList<>();

            // 记录 d 引用的 cluster + 设置 cluster 引用 d  ->  但若 d 在 PD 中的邻居 >= k，这些记录无效
            for (MTreeClass.ResultItem ri2 : query) {
                if (ri2.distance == 0) d.values[0] += (new Random()).nextDouble() / 1000000; // ? 为什么
                // * scan in cluster to find neighbors
                d.Rmc.add((MCObject) ri2.data); // 意味着 d 受这个 cluster 影响
                ArrayList<MCObject> l = associate_objects.getOrDefault(ri2.data, new ArrayList<>());
                l.add(d);
                associate_objects.put(ri2.data, l);
                ArrayList<MCObject> object_in_cluster = micro_clusters.get(ri2.data);
                if (object_in_cluster != null) 
                    for (MCObject o : object_in_cluster) {
                        if (mtree.getDistanceFunction().calculate(d, o) <= Constants.R) {
                            neighbor_in_mtree.add(o); // 记录 d 的在 cluster 中的邻居
                    }
                }
            }

            // 记录 d 在 PD 中的邻居
            PD.stream().forEach((m) -> {
                double distance = mtree.getDistanceFunction().calculate(d, m);
                if (distance <= Constants.R / 2) neighbor_in_R2.add(m);
                if (distance <= Constants.R) {
                    neighbor_in_PD.add(m);
                    neighbor_in_3_2Apart_PD.add(m);
                } else if (distance <= Constants.R * 3 / 2) {
                    neighbor_in_3_2Apart_PD.add(m);
                }
            });

            // 设置 d 在 PD 中的邻居的 exps 与 succeeding + 设置 d 的 exps 与 succeeding
            neighbor_in_PD.stream().peek((o) -> {
                if (o.arrivalTime < d.arrivalTime) {
                    if (!fromCluster)
                        o.numberOfSucceeding++;
                    else {
                        if((o.arrivalTime-1)/Constants.slide == (d.arrivalTime-1)/Constants.slide)
                            d.numberOfSucceeding++;
                        else 
                            d.exps.add(o.arrivalTime + Constants.W);
                    }
                } else {
                    if (!fromCluster) {
                        if((o.arrivalTime-1)/Constants.slide == (d.arrivalTime-1)/Constants.slide)
                            o.numberOfSucceeding++;
                        else
                            o.exps.add(d.arrivalTime + Constants.W);
                    }
                    d.numberOfSucceeding++;
                }
                // * check for o becomes inlier
            }).filter((o) -> (o.numberOfSucceeding + o.exps.size() >= Constants.k && outlierList.contains(o))
            ).peek((o) -> outlierList.remove(o)
            ).peek((o) -> {
                if (!o.exps.isEmpty()) o.ev = min(o.exps);
            }).forEach((o) -> {
                eventQueue.add(o);
            }); // 更新 PD 中的点的 outlier -> inlier，并设置 ev 和 eventQueue (只有 PD 中的点才会设置 ev 和 eventQueue)

            // 此时 d 不会放到已有的 cluster 中，因此和 PD 中的点的待遇一样，也设置 exps 与 succeeding
            neighbor_in_mtree.stream().forEach((o) -> { // 为了更新 d 的邻居
                if (o.arrivalTime < d.arrivalTime) {
                    if((o.arrivalTime-1)/Constants.slide == (d.arrivalTime-1)/Constants.slide)
                        d.numberOfSucceeding++;
                    else 
                        d.exps.add(o.arrivalTime + Constants.W);
                } else {
                    d.numberOfSucceeding++;
                }
            });

            // TODO: 明确在 form cluster 之前的代码的用意：d 和 cluster 中的点的情况
            if (neighbor_in_R2.size() > Constants.k * 1.1 && !fromCluster) { // 凑成了一个新的 cluster
                long startTime2 = Utils.getCPUTime();
                // form cluster
                d.isCenter = true;
                d.isInCluster = true;
                neighbor_in_R2.add(d);
                for (MCObject o : neighbor_in_R2) {
                    if(o.isInCluster && o.arrivalTime != d.arrivalTime)
                        throw new RuntimeException("o should not be in cluster if o is not d");
                    o.isCenter = false;
                    o.cluster = d;
                    o.isInCluster = true;
                    o.numberOfSucceeding = 0;
                    o.exps.clear();
                    if(inClusters.contains(o.arrivalTime))
                        throw new RuntimeException("o should not be in inClusters");
                    inClusters.add(o.arrivalTime);
                    PD.remove(o);
                    eventQueue.remove(o); // ? 不再会处理 o 的事件？只有 PD 中的点才会触发事件？
                    outlierList.remove(o);
                }
                
                if(!micro_clusters.containsKey(d)){ // 之前不应该存在，存在就是异常，
                    micro_clusters.put(d, neighbor_in_R2); // neighbor_in_R2 应该也包含自己
                    mtree.add(d);
                }
                // else micro_clusters.get(d).addAll(neighbor_in_R2);
                
                // update Rmc for points in PD
                neighbor_in_3_2Apart_PD.stream().forEach((o) -> o.Rmc.add(d));
                associate_objects.put(d, neighbor_in_3_2Apart_PD); // associate_objects value 是 PD 中的点
                MesureMemoryThread.timeForIndexing += Utils.getCPUTime() - startTime2;
            } else { // add to event queue and PD
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
                    long startTime2 = Utils.getCPUTime();
                    eventQueue.add(d); // eventQueue 看的是 element.ev
                    MesureMemoryThread.timeForIndexing += Utils.getCPUTime() - startTime2;
                }
            }
        }
        Utils.computeUsedMemory();
    }

    /**
     * @param fromCluster from shrunk cluster or new
     */
    public void processDataNew(MCObject d, int currentTime, boolean fromCluster) {
        if (d.arrivalTime <= currentTime - Constants.W) return;
        MTreeClass.Query query = mtree.getNearestByRange(d, Constants.R * 3 / 2);
        double min_distance = Double.MAX_VALUE;
        MTreeClass.ResultItem ri = null;
        boolean isFoundCluster = false;
        if (query.iterator().hasNext()) {
            ri = query.iterator().next();
            min_distance = ri.distance;
            if (micro_clusters.get(ri.data) != null && !micro_clusters.get(ri.data).isEmpty())
                isFoundCluster = true; // 有邻居在 cluster 中
        }

        if (min_distance <= Constants.R / 2 && isFoundCluster) {
            // * assign to this closet cluster
            MCObject closest_cluster = (MCObject) ri.data;
            if (fromCluster) {
                addOldObjectToCluster(d, closest_cluster);
            } else {
                addNewObjectToCluster(d, closest_cluster);
            }
        } else {
            // * do range query in PD and MTree (distance to center <= 3/2R)
            rangeQueryInPDAndCluster(query, d, fromCluster);
        }
    }

    private void process_event_queue(ArrayList<MCObject> expireData, int currentTime) {
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
        expireData.stream().forEach((p) -> {
            outlierList.remove(p);
        });
        outlierList.stream().map((outlierList1) -> (MCObject) outlierList1).forEach((d) -> {
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
}

class MCComparator implements Comparator<MCObject> {

    @Override
    public int compare(MCObject o1, MCObject o2) {
        if (o1.ev < o2.ev) return -1;
        else if (o1.ev == o2.ev) return 0;
        else return 1;

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

        exps = new ArrayList<>();
        Rmc = new ArrayList<>();
        isCenter = false;
        isInCluster = false;
    }

}