#!/bin/bash

dir_path=log/vary_params_final_gc
check= # NULL or "check_"

if [ ! -d "$dir_path" ]; then
    mkdir -p "$dir_path"
    echo "Directory created: $dir_path"
else
    echo "Directory already exists: $dir_path"
fi

# TAO
function run_tao() {
    window_list=(1000 5000 10000 15000 20000)
    slide_list=(0.05 0.1 0.2 0.5 1.0)
    R_list=(0.25 0.5 1.0 5.0 10.0)
    K_list=(10 30 50 70 100)
    for w in ${window_list[*]} 
    do
        echo "processing tao, vary window, window = ${w}"
        java -cp out mtree.tests.MTTest --W ${w} --slide 500 --R 1.9 --k 50 --datafile tao.txt > ${dir_path}/${check}w_tao_${w}.log
    done
    for s in ${slide_list[*]}
    do
        real_s=`echo "scale=0; ${s}*10000/1" | bc`
        echo "processing tao, vary slide, slide = ${real_s}"
        java -cp out mtree.tests.MTTest --W 10000 --slide ${real_s} --R 1.9 --k 50 --datafile tao.txt > ${dir_path}/${check}s_tao_${real_s}.log
    done
    for r in ${R_list[*]}
    do
        real_r=`echo "scale=3; ${r}*1.9" | bc`
        echo "processing tao, vary r, r = ${real_r}"
        java -cp out mtree.tests.MTTest --W 10000 --slide 500 --R ${real_r} --k 50 --datafile tao.txt > ${dir_path}/${check}r_tao_${real_r}.log
    done
    for k in ${K_list[*]}
    do
        echo "processing tao, vary k, k = ${k}"
        java -cp out mtree.tests.MTTest --W 10000 --slide 500 --R 1.9 --k ${k} --datafile tao.txt > ${dir_path}/${check}k_tao_${k}.log
    done
}

# GAU
function run_gau() {
    window_list=(10000 50000 100000 150000 200000)
    slide_list=(0.05 0.1 0.2 0.5 1.0)
    R_list=(0.25 0.5 1.0 5.0 10.0)
    K_list=(10 30 50 70 100)
    for w in ${window_list[*]} 
    do
        echo "processing gau, vary window, window = ${w}"
        java -cp out mtree.tests.MTTest --W ${w} --slide 5000 --R 0.028 --k 50 --datafile gaussian.txt > ${dir_path}/${check}w_gau_${w}.log
    done
    for s in ${slide_list[*]}
    do
        real_s=`echo "scale=0; ${s}*100000/1" | bc`
        echo "processing gau, vary slide, slide = ${real_s}"
        java -cp out mtree.tests.MTTest --W 100000 --slide ${real_s} --R 0.028 --k 50 --datafile gaussian.txt > ${dir_path}/${check}s_gau_${real_s}.log
    done
    for r in ${R_list[*]}
    do
        real_r=`echo "scale=3; ${r}*0.028" | bc`
        echo "processing gau, vary r, r = ${real_r}"
        java -cp out mtree.tests.MTTest --W 100000 --slide 5000 --R ${real_r} --k 50 --datafile gaussian.txt > ${dir_path}/${check}r_gau_${real_r}.log
    done
    for k in ${K_list[*]}
    do
        echo "processing gau, vary k, k = ${k}"
        java -cp out mtree.tests.MTTest --W 100000 --slide 5000 --R 0.028 --k ${k} --datafile gaussian.txt > ${dir_path}/${check}k_gau_${k}.log
    done
}

# STK
function run_stk() {
    window_list=(10000 50000 100000 150000 200000)
    slide_list=(0.05 0.1 0.2 0.5 1.0)
    R_list=(0.25 0.5 1.0 5.0 10.0)
    K_list=(10 30 50 70 100)
    for w in ${window_list[*]} 
    do
        echo "processing stk, vary window, window = ${w}"
        java -cp out mtree.tests.MTTest --W ${w} --slide 5000 --R 0.45 --k 50 --datafile stock.txt > ${dir_path}/${check}w_stk_${w}.log
    done
    for s in ${slide_list[*]}
    do
        real_s=`echo "scale=0; ${s}*100000/1" | bc`
        echo "processing stk, vary slide, slide = ${real_s}"
        java -cp out mtree.tests.MTTest --W 100000 --slide ${real_s} --R 0.45 --k 50 --datafile stock.txt > ${dir_path}/${check}s_stk_${real_s}.log
    done
    for r in ${R_list[*]}
    do
        real_r=`echo "scale=4; ${r}*0.45" | bc`
        echo "processing stk, vary r, r = ${real_r}"
        java -cp out mtree.tests.MTTest --W 100000 --slide 5000 --R ${real_r} --k 50 --datafile stock.txt > ${dir_path}/${check}r_stk_${real_r}.log
    done
    for k in ${K_list[*]}
    do
        echo "processing gau, vary k, k = ${k}"
        java -cp out mtree.tests.MTTest --W 100000 --slide 5000 --R 0.45 --k ${k} --datafile stock.txt > ${dir_path}/${check}k_stk_${k}.log
    done
}

bash compile.sh
run_tao
run_gau
run_stk