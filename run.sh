#!/bin/bash

bash compile.sh
dir_path=log/overall
check= # NULL or "check_"
echo "processing tao on default parameters, W=10000, slide=500, R=1.9, k=50"
java -cp out mtree.tests.MTTest --algorithm microCluster --W 10000 --slide 500 --R 1.9 --k 50 --datafile tao.txt --numberWindow 1130 > ${dir_path}/${check}tao.log
echo "processing gau on default parameters, W=100000, slide=5000, R=0.028, k=50"
java -cp out mtree.tests.MTTest --algorithm microCluster --W 100000 --slide 5000 --R 0.028 --k 50 --datafile gaussian.txt --numberWindow 180 > ${dir_path}/${check}gau.log
echo "processing stk on default parameters, W=100000, slide=5000, R=0.45, k=50"
java -cp out mtree.tests.MTTest --algorithm microCluster --W 100000 --slide 5000 --R 0.45 --k 50 --datafile stock.txt --numberWindow 189 > ${dir_path}/${check}stk.log
