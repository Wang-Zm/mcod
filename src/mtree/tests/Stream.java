package mtree.tests;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

import mtree.utils.Constants;

public class Stream {

    public static Stream streamInstance;
    public ArrayList<Data> dataList;

    public static Stream getInstance(String file) {
        // 根据传入的文件名，生成一个数组，每次返回指定区间的数据
        streamInstance = new Stream();
        // 读取文件中的数据
        streamInstance.getData(file);
        return streamInstance;
    }

    public ArrayList<Data> getIncomingData(int currentTime, int length) {
        if (currentTime + length <= dataList.size()) {
            return new ArrayList<>(dataList.subList(currentTime, currentTime + length));
        }
        return new ArrayList<>(dataList.subList(currentTime + length, dataList.size()));
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
