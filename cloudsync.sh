#!/bin/bash

cd `dirname $0`

JAVA="java"

VERSION=$("$JAVA" -version 2>&1 | awk -F '"' '/version/ {print $2}')

if [[ "$VERSION" < 1.7 ]]; then
    echo Found unsupported java version. Please install jdk 7 or newer
else
    TIMESTAMP=`date +"%Y.%m.%d %H:%M:%S"`
    if [ -z "$LANG" ]; then
         export LANG="en_US.UTF-8"
         echo "[$TIMESTAMP] - WARNING - no \$LANG environment detected"
         echo "[$TIMESTAMP] - WARNING - set \"$LANG\" environment for local filename encoding"
    else
         echo "[$TIMESTAMP] - INFO - \"$LANG\" environment detected and used for local filename encoding"
    fi
    
    export LC_ALL=$LANG
    
    CMD="$JAVA -cp \"./lib/*\" cloudsync.Cloudsync $*"
    eval $CMD
fi
