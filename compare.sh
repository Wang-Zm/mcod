#!/bin/bash

# rtod_outliers=$(grep "# outliers:" 240701-235405-TAO.log | sed 's/.*outliers: \([0-9]*\).*/\1/')
# echo ${rtod_outliers[*]}

readarray -t rtod_outliers < <(grep "# outliers:" 240701-235405-TAO.log | sed 's/.*outliers: \([0-9]*\).*/\1/')
echo "Array length: ${#rtod_outliers[*]}"

# TODO: 使得 RTOD 那边生成相同格式的日志文件
# TODO: 比较
# 1.获取某一目录下的所有文件的名字 2.从两个文件夹中读取相同名字的文件，收集 outliers 的数量级 3.对比 outliers 的数量，并直接对比
mcod_dir=log/vary_params
readarray -t mcod_logs < <(find ${mcod_dir} -type f ! -name 'check*' -printf '%P\n')
echo "Number of log files for MCOD: ${#mcod_logs[*]}"

rtod_dir=/home/wzm/Code/rtod/log/vary_params
readarray -t rtod_logs < <(find ${rtod_dir} -type f)
echo "Number of log files for RTOD: ${#rtod_logs[*]}"

for ((i=0; i<${#mcod_logs[*]}; i++)); do
    # echo "Index: $i, Value: ${mcod_logs[$i]}"
    readarray -t mcod_outliers < <(grep "#outliers:" ${mcod_dir}/${mcod_logs[$i]} | sed 's/.*outliers: \([0-9]*\).*/\1/')
    readarray -t rtod_outliers < <(grep "# outliers:" ${rtod_dir}/${mcod_logs[$i]} | sed 's/.*outliers: \([0-9]*\).*/\1/')
    # 从 mcod 第二个开始搞
    consistent=1
    for ((j=0; j<${#rtod_outliers[*]}; j++)); do
        if [ ${mcod_outliers[$j+1]} -ne ${rtod_outliers[$j]} ]; then
            echo "mcod outlier: ${#mcod_outliers[*]}"
            echo "rtod outlier: ${#rtod_outliers[*]}"
            echo "log_file=${mcod_logs[$i]}, mcod_outliers[$((j+2))]=${mcod_outliers[$j+1]}, rtod_outliers[$((j+1))]=${rtod_outliers[$j]}"
            consistent=0
            break
        fi
    done
    if [ ${consistent} ]; then
        echo "[${mcod_logs[$i]}] is checked"
    fi
    # break
done