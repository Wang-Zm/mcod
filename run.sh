#!/bin/bash

java -cp out mtree.tests.MTTest --algorithm microCluster --W 10000 --slide 500 --R 1.9 --k 50 --datafile /Users/wangziming/Code/Luan/Outlier/CountBasedWindow/DODDS/tao.txt --numberWindow 30
