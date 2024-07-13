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
    # window_list=(10000 50000 100000 150000 200000)
    # slide_list=(0.05 0.1 0.2 0.5 1.0)
    # R_list=(0.25 0.5 1.0 5.0 10.0)
    # K_list=(10 30 50 70 100)
    # for w in ${window_list[*]} 
    # do
    #     echo "processing gau, vary window, window = ${w}"
    #     java -cp out mtree.tests.MTTest --W ${w} --slide 5000 --R 0.028 --k 50 --datafile gaussian.txt > ${dir_path}/${check}w_gau_${w}.log
    # done
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
    # for w in ${window_list[*]} 
    # do
    #     echo "processing stk, vary window, window = ${w}"
    #     java -cp out mtree.tests.MTTest --W ${w} --slide 5000 --R 0.45 --k 50 --datafile stock.txt > ${dir_path}/${check}w_stk_${w}.log
    # done
    # for s in ${slide_list[*]}
    # do
    #     real_s=`echo "scale=0; ${s}*100000/1" | bc`
    #     echo "processing stk, vary slide, slide = ${real_s}"
    #     java -cp out mtree.tests.MTTest --W 100000 --slide ${real_s} --R 0.45 --k 50 --datafile stock.txt > ${dir_path}/${check}s_stk_${real_s}.log
    # done
    for r in ${R_list[*]}
    do
        real_r=`echo "scale=4; ${r}*0.45" | bc`
        echo "processing stk, vary r, r = ${real_r}"
        java -cp out mtree.tests.MTTest --W 100000 --slide 5000 --R ${real_r} --k 50 --datafile stock.txt > ${dir_path}/${check}r_stk_${real_r}.log
    done
    # for k in ${K_list[*]}
    # do
    #     echo "processing gau, vary k, k = ${k}"
    #     java -cp out mtree.tests.MTTest --W 100000 --slide 5000 --R 0.45 --k ${k} --datafile stock.txt > ${dir_path}/${check}k_stk_${k}.log
    # done
}

function run_gau_for_error() {
    window_list=(50000 150000)
    slide_list=(0.5 1.0)
    R_list=(0.25 0.5)
    for w in ${window_list[*]} 
    do
        echo "processing gau, vary window, window = ${w}"
        java -cp out mtree.tests.MTTest --W ${w} --slide 5000 --R 0.028 --k 50 --datafile gaussian.txt > ${dir_path}/w_gau_${w}.log
    done
    for s in ${slide_list[*]}
    do
        real_s=`echo "scale=0; ${s}*100000/1" | bc`
        echo "processing gau, vary slide, slide = ${real_s}"
        java -cp out mtree.tests.MTTest --W 100000 --slide ${real_s} --R 0.028 --k 50 --datafile gaussian.txt > ${dir_path}/s_gau_${real_s}.log
    done
    for r in ${R_list[*]}
    do
        real_r=`echo "scale=3; ${r}*0.028" | bc`
        echo "processing gau, vary r, r = ${real_r}"
        java -cp out mtree.tests.MTTest --W 100000 --slide 5000 --R ${real_r} --k 50 --datafile gaussian.txt > ${dir_path}/r_gau_${real_r}.log
    done
}

function run_stk_for_error() {
    R_list=(0.25)
    K_list=(70)
    for r in ${R_list[*]}
    do
        real_r=`echo "scale=4; ${r}*0.45" | bc`
        echo "processing stk, vary r, r = ${real_r}"
        java -cp out mtree.tests.MTTest --W 100000 --slide 5000 --R ${real_r} --k 50 --datafile stock.txt > ${dir_path}/r_stk_${real_r}.log
    done
    for k in ${K_list[*]}
    do
        echo "processing gau, vary k, k = ${k}"
        java -cp out mtree.tests.MTTest --W 100000 --slide 5000 --R 0.45 --k ${k} --datafile stock.txt > ${dir_path}/k_stk_${k}.log
    done
}

function run_check_if_error() {
    # k_tao_100.log
    # r_tao_.475.log
    # k_gau_100.log
    # k_stk_70.log
    # k_stk_100.log
    # r_stk_2.250.log
    # r_stk_4.500.log

    # tao
    java -cp out mtree.tests.MTTest --W 10000 --slide 500 --R 0.475 --k 50 --datafile tao.txt > ${dir_path}/${check}r_tao_.475.log
    # gau
    java -cp out mtree.tests.MTTest --W 100000 --slide 5000 --R 0.028 --k 100 --datafile gaussian.txt > ${dir_path}/${check}k_gau_100.log
    # stk
    java -cp out mtree.tests.MTTest --W 100000 --slide 5000 --R 0.45 --k 70 --datafile stock.txt > ${dir_path}/${check}k_stk_70.log
    java -cp out mtree.tests.MTTest --W 100000 --slide 5000 --R 0.45 --k 100 --datafile stock.txt > ${dir_path}/${check}k_stk_100.log
    java -cp out mtree.tests.MTTest --W 100000 --slide 5000 --R 2.25 --k 50 --datafile stock.txt > ${dir_path}/${check}r_stk_2.250.log
    java -cp out mtree.tests.MTTest --W 100000 --slide 5000 --R 4.5 --k 50 --datafile stock.txt > ${dir_path}/${check}r_stk_4.500.log
}

bash compile.sh
# run_tao
# run_gau
run_stk
# run_gau_for_error
# run_stk_for_error
# run_check_if_error

# echo "processing tao, vary k, k = 50"
# java -cp out mtree.tests.MTTest --W 10000 --slide 500 --R 1.9 --k 50 --datafile tao.txt > k_tao_50.log

# window_list=(50000)
# window_list=(10000 50000 100000 150000 200000)
# slide_list=(0.05 0.1 0.2 0.5 1.0)
# R_list=(0.25 0.5 1.0 5.0 10.0)
# K_list=(10 30 50 70 100)
# for w in ${window_list[*]} 
# do
#     echo "processing gau, vary window, window = ${w}"
#     java -cp out mtree.tests.MTTest --W ${w} --slide 5000 --R 0.028 --k 50 --datafile gaussian.txt > ${dir_path}/w_gau_${w}.log
# done

# R_list=(0.25)
# for r in ${R_list[*]}
# do
#     real_r=`echo "scale=3; ${r}*0.028" | bc`
#     echo "processing gau, vary r, r = ${real_r}"
#     java -cp out mtree.tests.MTTest --W 100000 --slide 5000 --R ${real_r} --k 50 --datafile gaussian.txt > log/bugfix/r_gau_${real_r}.log
# done