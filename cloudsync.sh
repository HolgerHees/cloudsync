#!/bin/sh

cd `dirname $0`

LANG="de_DE.UTF-8"
JAVA="java"

VERSION=$("$JAVA" -version 2>&1 | awk -F '"' '/version/ {print $2}')

if [[ "$VERSION" < 1.7 ]]; then
    echo Found unsupported java version. Please install jdk 7 or newer
else
    export LANG=$LANG
    CMD="$JAVA -cp "./bin/:./lib/*:./lib/drive/*:./lib/drive/libs/*" cloudsync.Cloudsync $*"
    eval $CMD
fi
