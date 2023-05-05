SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
java -Xmx3G -jar $SCRIPT_DIR/target/scala-2.13/cep-engine-server-assembly-1.0.jar
