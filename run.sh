#!/bin/sh
# Usage: ./run.sh          -> website at http://localhost:8080
#        ./run.sh console  -> menu in the terminal
set -e
mvn -q -B package
java -jar target/shopsync-mini.jar "$@"
