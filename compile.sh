#!/bash/bin

output_dir=out
if [ ! -d "$output_dir" ]; then
    mkdir -p "$output_dir"
    echo "Directory created: $output_dir"
else
    echo "Directory already exists: $output_dir"
fi

javac -d out -cp src src/mtree/tests/MTTest.java
