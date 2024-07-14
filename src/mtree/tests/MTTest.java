package mtree.tests;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashSet;

import outlierdetection.MicroCluster;
import mtree.utils.Constants;
import mtree.utils.Utils;

public class MTTest {
    public static int currentTime = 0;
    public static HashSet<Integer> idOutliers = new HashSet<>();

    public static void main(String[] args) throws IOException, FileNotFoundException, ParseException {
        readArguments(args);
        MeasureMemoryThread mesureThread = new MeasureMemoryThread();
        Stream s = Stream.getInstance(Constants.dataFile);
        MicroCluster micro = new MicroCluster();
        int numberWindows = 0;
        double totalTime = 0;
        while (true) {
            numberWindows++;
            ArrayList<Data> incomingData;
            if (numberWindows > 1) {
                incomingData = s.getIncomingData(currentTime, Constants.slide);
                currentTime = currentTime + Constants.slide;
            } else {
                incomingData = s.getIncomingData(currentTime, Constants.W);
                currentTime = currentTime + Constants.W;
            }
            if (incomingData.size() < Constants.slide) {
                break;
            }
            long start = Utils.getCPUTime(); // requires java 1.5
            ArrayList<Data> outliers6 = micro.detectOutlier(incomingData, currentTime, Constants.W, Constants.slide);
            double elapsedTimeInSec = (Utils.getCPUTime() - start) * 1.0 / 1000000000;
            totalTime += elapsedTimeInSec;
            outliers6.forEach((outlier) -> idOutliers.add(outlier.arrivalTime));
            if (numberWindows == 1) {
                totalTime = 0;
                MeasureMemoryThread.timeForIndexing = 0;
                MeasureMemoryThread.timeForNewSlide = 0;
                MeasureMemoryThread.timeForExpireSlide = 0;
                MicroCluster.numberCluster = 0;
                MicroCluster.numberPointsInClusters = 0;
                MicroCluster.numberPointsInEventQueue = 0;
            }
            System.out.println("#window: " + numberWindows);
            System.out.println("Total #outliers: " + idOutliers.size());
            if (numberWindows == 1) continue;
            System.out.printf("Average Time: %.3f ms\n", totalTime / (numberWindows - 1) * 1000);
            System.out.printf("Peak memory: %.3f MB\n", MeasureMemoryThread.maxMemory * 1.0 / 1024 / 1024);
            // System.out.printf("Time index, remove data from structure: %.3f ms\n", MesureMemoryThread.timeForIndexing / 1000000 / (numberWindows - 1));
            System.out.printf("Time for shrinking cluster: %.3f ms\n", MeasureMemoryThread.timeForShrinkCluster / 1000000 / (numberWindows - 1));
            System.out.printf("Time for expiring data in cluster: %.3f ms\n", MeasureMemoryThread.timeForExpireDataInCluster / 1000000 / (numberWindows - 1));
            System.out.printf("Time for expiring data in PD: %.3f ms\n", MeasureMemoryThread.timeForExpireDataInPD / 1000000 / (numberWindows - 1));
            System.out.printf("Time for processing event queue: %.3f ms\n", MeasureMemoryThread.timeForProcessEventQueue / 1000000 / (numberWindows - 1));
            System.out.printf("Time for expired slide: %.3f ms\n", MeasureMemoryThread.timeForExpireSlide / 1000000 / (numberWindows - 1));
            System.out.printf("Time for querying: %.3f ms\n", MeasureMemoryThread.timeForQuerying / 1000000 / (numberWindows - 1));
            System.out.printf("Time for updating neighbors when adding object to cluster: %.3f ms\n", MeasureMemoryThread.timeForAddObjectToClusterUpdateNeighbors / 1000000 / (numberWindows - 1));
            System.out.printf("Time for adding object to cluster: %.3f ms\n", MeasureMemoryThread.timeForAddObjectToCluster / 1000000 / (numberWindows - 1));
            System.out.printf("Time for range query in PD and cluster: %.3f ms\n", MeasureMemoryThread.timeForRangeQueryInPDAndCluster / 1000000 / (numberWindows - 1));
            System.out.printf("Time for new slide: %.3f ms\n", MeasureMemoryThread.timeForNewSlide / 1000000 / (numberWindows - 1));
            System.out.printf("Number clusters = %.3f\n", MicroCluster.numberCluster / (numberWindows - 1));
            System.out.printf("Avg Number points in event queue = %.3f\n", MicroCluster.numberPointsInEventQueue / (numberWindows - 1));
            System.out.printf("Avg number points in clusters = %.3f\n", MicroCluster.numberPointsInClusters / (numberWindows - 1));
            System.out.printf("Avg remove times for objects in clusters when expiring = %.3f\n", 1.0 * MeasureMemoryThread.removeTimesForObjectsInClusterWhenExpiring / (numberWindows - 1));
            // System.out.println("Avg Rmc size = " + MicroCluster.avgPointsInRmcAllWindows / (numberWindows - 1));
            // System.out.println("Avg Length exps= " + MicroCluster.avgLengthExpsAllWindows / (numberWindows - 1));
            System.out.println("------------------------------------");

            // reset
            idOutliers.clear();
        }
        MicroCluster.numberCluster = MicroCluster.numberCluster / (numberWindows - 2);
        MicroCluster.avgPointsInRmcAllWindows = MicroCluster.avgPointsInRmcAllWindows / (numberWindows - 2);
        MicroCluster.avgLengthExpsAllWindows = MicroCluster.avgLengthExpsAllWindows / (numberWindows - 2);
        MicroCluster.numberPointsInClustersAllWindows = MicroCluster.numberPointsInClustersAllWindows / (numberWindows - 2);
        mesureThread.averageTime = totalTime / (numberWindows - 2) * 1000;
        mesureThread.computeMemory();
        mesureThread.writeResult();
        mesureThread.interrupt();
    }

    public static void readArguments(String[] args) {
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.indexOf("--") == 0) {
                switch (arg) {
                    case "--R":
                        Constants.R = Double.valueOf(args[i + 1]);
                        break;
                    case "--W":
                        Constants.W = Integer.valueOf(args[i + 1]);
                        break;
                    case "--k":
                        Constants.k = Integer.valueOf(args[i + 1]);
                        break;
                    case "--datafile":
                        Constants.dataFile = args[i + 1];
                        break;
                    case "--slide":
                        Constants.slide = Integer.valueOf(args[i + 1]);
                        break;
                    case "--samplingTime":
                        Constants.samplingPeriod = Integer.valueOf(args[i + 1]);
                        break;
                }
            }
        }
    }
}
