#!/bin/bash

dir_path=log/vary_params_nogc
# 1.读取某一目录下所有文件 2.逆序排列 w s r k 3.数据集顺序排列 4.提取结果
readarray -t mcod_logs < <(find ${dir_path} -type f -printf '%P\n')
# sorted_logs=($(printf '%s\n' "${mcod_logs[@]}" | sort))
# echo "${sorted_logs[@]}"

# 使用 sort 和 awk 来根据 x1, x2, 和 x3 进行排序
sorted_logs=($(printf '%s\n' "${mcod_logs[@]}" |
               awk -F'_' '{print $0 "\t" $1 "\t" $2 "\t" $3}' |
               sort -k2,2 -k3,3 -k4,4n |
               cut -f1))

# 输出排序后的数组
# printf 'Sorted array:\n'
# printf '%s\n' "${sorted_logs[@]}"
# exit

row=()
for ((i=0; i<${#sorted_logs[@]}; i++)); do
    mcod_outliers=$(grep "Average CPU time: " ${dir_path}/${sorted_logs[$i]} | sed -n 's/.*time: \([^[:space:]]*\).*/\1/p')
    row+=(${mcod_outliers})
    if (( (i + 1) % 5 == 0 )); then
        echo ${row[@]} 
        row=()
    fi
done