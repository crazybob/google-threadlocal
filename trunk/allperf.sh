# Runs ./perf.sh for [1-8]

for ((i=1;i<=8;i+=1)); do
    ./perf.sh $i
done
