package outlierdetection;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;

import mtree.tests.Data;

public class MTreeTestW {
    public static void main(String[] args) {
        MTreeClass mtree = new MTreeClass();
        String filename = "/home/wzm/Code/MCOD/cluster.txt";
        ArrayList<MCObject> dataList = new ArrayList<>();
        MCObject deleteData = null;
        try {
            BufferedReader bfr = new BufferedReader(new FileReader(new File(filename)));
            String line;
            int time = 0;
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
                    MCObject mc = new MCObject(data);
                    dataList.add(mc);
                    if (d[0] == 3.326) {
                        deleteData = mc;
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        System.out.println("dataList.size=" + dataList.size());
        for (MCObject d : dataList) {
            mtree.add(d);
        }

        System.out.println("deleteData.values: " + deleteData.values[0]);

        Data query = new Data(new double[]{3.326});
        MCObject queryData = new MCObject(query);
        MTreeClass.Query result = mtree.getNearest(queryData);
        if (result.iterator().hasNext()) {
            MTreeClass.ResultItem ri = result.iterator().next();
            System.out.println(ri.data.values.length + " " + ri.data.values[0] + " distance=" + ri.distance);
        }

        if (!mtree.remove(deleteData)) { // 使用 new 的话无法删除
            System.out.print("!mtree.remove(deleteData), deleteData.values: ");
            for (double v : deleteData.values) {
                System.out.print(v + " ");
            }
            System.out.println();
            result = mtree.getNearest(queryData);
            if (result.iterator().hasNext()) {
                MTreeClass.ResultItem ri = result.iterator().next();
                System.out.println(ri.data.values.length + " " + ri.data.values[0] + " distance=" + ri.distance);
            }
        } else {
            System.out.println("remove successfully");
        }
    }
}
