#!/bin/bash

set -e

./gradlew verifyGoogleJavaFormat

mkdir -p demo
cd demo
bash ../scripts/build_fabric_demo_chain.sh

cp -r certs/*  ../src/test/resources/

cd -

cp -r src/main/resources/chaincode/WeCrossProxy src/test/resources/chains/fabric/WeCrossProxy

./gradlew build -x test
./gradlew test -i
./gradlew jacocoTestReport