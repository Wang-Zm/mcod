package mtree.tests;

import java.util.logging.Level;
import java.util.logging.Logger;
import mtree.utils.Constants;

public class MeasureMemoryThread extends Thread {
    public static long maxMemory = 0;

    public double averageTime = 0;

    public static double timeForIndexing = 0;
    public static double timeForDetecting = 0; 
    
    public static double timeForReporting = 0;
    public static double timeForQuerying = 0; 
    
    public static double timeForShrinkCluster = 0;
    public static double timeForExpireDataInCluster = 0;
    public static double timeForExpireDataInPD = 0;
    public static double timeForProcessEventQueue = 0;
    public static double timeForExpireSlide = 0;
    public static double timeForAddObjectToClusterUpdateNeighbors = 0;
    public static double timeForAddObjectToCluster = 0;
    public static double timeForRangeQueryInPDAndCluster = 0;
    public static double timeForNewSlide = 0;

    public static long removeTimesForObjectsInClusterWhenExpiring = 0;
    
    public void computeMemory() {
        Runtime.getRuntime().gc();
        long used = Runtime.getRuntime().totalMemory()- Runtime.getRuntime().freeMemory();
        if(maxMemory < used)
            maxMemory = used;
    }

    @Override
    public void run() {
        while (true) {
            computeMemory();
            try {
                Thread.sleep(Constants.samplingPeriod);
            } catch (InterruptedException ex) {
                Logger.getLogger(MeasureMemoryThread.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    public void writeResult() {
        System.out.printf("Peak memory: %.3f MB\n", maxMemory * 1.0 / 1024 / 1024);
        System.out.printf("Average CPU time: %.3f ms\n", averageTime);
    }
}
