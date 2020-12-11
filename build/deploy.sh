#!/bin/bash

cd "$( dirname "$0" )"
cd ..

current_dir=`pwd`
echo "current_dir : $current_dir"

# VERSION
VERSION=`git tag |grep -E "gateway-[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+" |tail -1`
echo "${VERSION}" > $current_dir/VERSION

mvn clean package -DskipTests
