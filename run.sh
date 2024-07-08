#!/bin/bash

java -cp out mtree.tests.MTTest --algorithm microCluster --W 10000 --slide 500 --R 1.9 --k 50 --datafile tao.txt --numberWindow 1130 > stdout_tao.log
java -cp out mtree.tests.MTTest --algorithm microCluster --W 100000 --slide 5000 --R 0.028 --k 50 --datafile gaussian.txt --numberWindow 180 > stdout_gau.log
java -cp out mtree.tests.MTTest --algorithm microCluster --W 100000 --slide 5000 --R 0.45 --k 50 --datafile stock.txt --numberWindow 189 > stdout_stk.log
