package mtree.tests;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;

public class Stream {

    public static Stream streamInstance;
    public ArrayList<Data> dataList;

    public static Stream getInstance(String file) {
        streamInstance = new Stream();
        streamInstance.getData(file);
        return streamInstance;
    }

    public ArrayList<Data> getIncomingData(int currentTime, int length) {
        if (currentTime + length <= dataList.size()) {
            return new ArrayList<>(dataList.subList(currentTime, currentTime + length));
        }
        return new ArrayList<>(dataList.subList(currentTime, dataList.size()));
    }

    public void getData(String filename) {
        try {
            BufferedReader bfr = new BufferedReader(new FileReader(new File(filename)));
            String line;
            int time = 0;
            dataList = new ArrayList<>();
            try {
                while ((line = bfr.readLine()) != null) {
                    time++;
                    String[] attrs = line.split(",");
                    double[] d = new double[attrs.length];
                    for (int i = 0; i < d.length; i++) {
                        d[i] = Double.parseDouble(attrs[i]);
                    }
                    Data data = new Data(d);
                    data.arrivalTime = time;
                    dataList.add(data);
                }

            } catch (IOException e) {
                e.printStackTrace();
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }
}
