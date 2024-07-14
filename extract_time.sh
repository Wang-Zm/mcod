#!/bin/bash

dir_path=log/vary_params_final_gc
readarray -t mcod_logs < <(find ${dir_path} -type f -printf '%P\n')

sorted_logs=($(printf '%s\n' "${mcod_logs[@]}" |
               awk -F'_' '{print $0 "\t" $1 "\t" $2 "\t" $3}' |
               sort -k2,2r -k3,3 -k4,4n |
               cut -f1))

contents=("vary window size" "vary slide size" "vary R" "vary K")
row=()
for ((i=0; i<${#sorted_logs[@]}; i++)); do
    if (( i % 15 == 0 )); then
        echo ${contents[$i / 15]}
    fi
    mcod_outliers=$(grep "Average CPU time: " ${dir_path}/${sorted_logs[$i]} | sed -n 's/.*time: \([^[:space:]]*\).*/\1/p')
    row+=(${mcod_outliers})
    if (( (i + 1) % 5 == 0 )); then
        echo ${row[@]} 
        row=()
    fi
done