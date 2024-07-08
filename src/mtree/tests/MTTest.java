package mtree.tests;

import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashSet;

import outlierdetection.Direct_Update_Event;
import outlierdetection.ExactStorm;
import outlierdetection.MESI;
import outlierdetection.MicroCluster;
import mtree.utils.Constants;
import mtree.utils.Utils;
import outlierdetection.MicroCluster_New;

public class MTTest {
    public static int currentTime = 0;

    public static HashSet<Integer> idOutliers = new HashSet<>();

    public static String algorithm;

    public static void main(String[] args) throws IOException, FileNotFoundException, ParseException {
        readArguments(args);
        MesureMemoryThread mesureThread = new MesureMemoryThread();
        mesureThread.start();
        Stream s = Stream.getInstance(Constants.dataFile);
        MicroCluster micro = new MicroCluster();
        int numberWindows = 0;
        double totalTime = 0;
        while (true) {
            if (Constants.numberWindow != -1 && numberWindows > Constants.numberWindow) {
                break;
            }
            numberWindows++;

            ArrayList<Data> incomingData;
            if (numberWindows > 1) {
                incomingData = s.getIncomingData(currentTime, Constants.slide);
                currentTime = currentTime + Constants.slide;
            } else {
                incomingData = s.getIncomingData(currentTime, Constants.W);
                currentTime = currentTime + Constants.W;
            }

            long start = Utils.getCPUTime(); // requires java 1.5
            double elapsedTimeInSec;
            ArrayList<Data> outliers6 = micro.detectOutlier(incomingData, currentTime, Constants.W,
                    Constants.slide);
            elapsedTimeInSec = (Utils.getCPUTime() - start) * 1.0 / 1000000000;
            totalTime += elapsedTimeInSec;
            outliers6.forEach((outlier) -> {
                idOutliers.add(outlier.arrivalTime);
            });
            if (numberWindows == 1) {
                totalTime = 0;
                MesureMemoryThread.timeForIndexing = 0;
                MesureMemoryThread.timeForNewSlide = 0;
                MesureMemoryThread.timeForExpireSlide = 0;
            }
            System.out.println("#window: " + numberWindows);
            System.out.println("Total #outliers: " + idOutliers.size());
            System.out.println("Average Time: " + totalTime * 1.0 / numberWindows);
            System.out.println("Peak memory: " + MesureMemoryThread.maxMemory * 1.0 / 1024 / 1024);
            System.out.println("Time index, remove data from structure: " + MesureMemoryThread.timeForIndexing * 1.0 / 1000000000 / numberWindows);
            System.out.println("Time for querying: " + MesureMemoryThread.timeForQuerying * 1.0 / 1000000000 / numberWindows);
            System.out.println("Time for new slide: " + MesureMemoryThread.timeForNewSlide * 1.0 / 1000000000 / numberWindows);
            System.out.println("Time for expired slide: " + MesureMemoryThread.timeForExpireSlide * 1.0 / 1000000000 / numberWindows);
            System.out.println("------------------------------------");

            if (algorithm.equals("exactStorm")) {
                System.out.println("Avg neighbor list length = " + ExactStorm.avgAllWindowNeighbor / numberWindows);
            } else if (algorithm.equals("mesi")) {
                System.out.println("Avg trigger list = " + MESI.avgAllWindowTriggerList / numberWindows);
                System.out.println("Avg neighbor list = " + MESI.avgAllWindowNeighborList / numberWindows);
            } else if (algorithm.equals("microCluster")) {
                System.out.println("Number clusters = " + MicroCluster.numberCluster / numberWindows);
                System.out.println("Max  Number points in event queue = " + MicroCluster.numberPointsInEventQueue);
                System.out.println("Avg number points in clusters= " + MicroCluster.numberPointsInClustersAllWindows / numberWindows);
                System.out.println("Avg Rmc size = " + MicroCluster.avgPointsInRmcAllWindows / numberWindows);
                System.out.println("Avg Length exps= " + MicroCluster.avgLengthExpsAllWindows / numberWindows);
            } else if (algorithm.equals("due")) {
                Direct_Update_Event.numberPointsInEventQueue = Direct_Update_Event.numberPointsInEventQueue /numberWindows;
                Direct_Update_Event.avgAllWindowNumberPoints = Direct_Update_Event.numberPointsInEventQueue;
                System.out.println("max #points in event queue = " + Direct_Update_Event.avgAllWindowNumberPoints);
            }
            if (algorithm.equals("microCluster_new")) {
                System.out.println("avg points in clusters = " + MicroCluster_New.avgNumPointsInClusters * 1.0 / numberWindows);
                System.out.println("Avg points in event queue = " + MicroCluster_New.avgNumPointsInEventQueue * 1.0 / numberWindows);
                System.out.println("avg neighbor list length = " + MicroCluster_New.avgNeighborListLength * 1.0 / numberWindows);
            }

            // reset
            idOutliers.clear();
        }
        System.out.printf("Average Time for a slide: %f ms\n", totalTime / Constants.numberWindow * 1000);
        ExactStorm.avgAllWindowNeighbor = ExactStorm.avgAllWindowNeighbor / numberWindows;
        MESI.avgAllWindowTriggerList = MESI.avgAllWindowTriggerList / numberWindows;
        MicroCluster.numberCluster = MicroCluster.numberCluster / numberWindows;
        MicroCluster.avgPointsInRmcAllWindows = MicroCluster.avgPointsInRmcAllWindows / numberWindows;
        MicroCluster.avgLengthExpsAllWindows = MicroCluster.avgLengthExpsAllWindows / numberWindows;
        MicroCluster.numberPointsInClustersAllWindows = MicroCluster.numberPointsInClustersAllWindows / numberWindows;
        MicroCluster_New.avgNumPointsInClusters = MicroCluster_New.avgNumPointsInClusters / numberWindows;
        mesureThread.averageTime = totalTime * 1.0 / (numberWindows - 1);
        mesureThread.writeResult();
        mesureThread.stop();
        mesureThread.interrupt();
    }

    public static void readArguments(String[] args) {
        for (int i = 0; i < args.length; i++) {
            //check if arg starts with --
            String arg = args[i];
            if (arg.indexOf("--") == 0) {
                switch (arg) {
                    case "--algorithm":
                        algorithm = args[i + 1];
                        break;
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
                    case "--output":
                        Constants.outputFile = args[i + 1];
                        break;
                    case "--numberWindow":
                        Constants.numberWindow = Integer.valueOf(args[i + 1]);
                        break;
                    case "--slide":
                        Constants.slide = Integer.valueOf(args[i + 1]);
                        break;
                    case "--resultFile":
                        Constants.resultFile = args[i + 1];
                        break;
                    case "--samplingTime":
                        Constants.samplingPeriod = Integer.valueOf(args[i + 1]);
                        break;
                    case "--likely":
                        Constants.likely = Double.valueOf(args[i + 1]);
                        break;
                }
            }
        }
    }

    public static void writeResult() {
        try (PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(Constants.resultFile, true)))) {
            for (Integer time : idOutliers) {
                out.println(time);
            }
        } catch (IOException e) {
        }
    }
}
