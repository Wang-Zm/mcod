# MCOD implementation in [RTOD: Efficient Outlier Detection with Ray Tracing Cores]

This is the MCOD implementation mentioned in paper RTOD. In this repository, MCOD is implemented based on the paper [Continuous monitoring of distance-based outliers over data streams](https://ieeexplore.ieee.org/document/5767923) and the [code implementation](https://infolab.usc.edu/Luan/Outlier/CountBasedWindow/DODDS/) from [Distance-based Outlier Detection in Data Streams](https://www.vldb.org/pvldb/vol9/p1089-tran.pdf). We have reorginized the original code and added outlier-check functions to verify the identified outliers.



## Prerequisites
- jdk 1.8

## Run

- Run experiments under default parameters
    ```
    $ bash run.sh
    ```

- Vary parameters
    ```
    $ bash vary_params.sh
    ```