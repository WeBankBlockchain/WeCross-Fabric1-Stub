#!/bin/bash

set -e

./gradlew verifyGoogleJavaFormat

mkdir -p demo
cd demo
bash ../scripts/build_fabric_demo_chain.sh

cp -r certs/*  ../src/test/resources/

cd -

cp -r src/main/resources/chaincode/WeCrossProxy src/test/resources/chains/fabric/WeCrossProxy
cp -r src/main/resources/chaincode/WeCrossHub src/test/resources/chains/fabric/WeCrossHub

./gradlew build -x test
./gradlew test -i
./gradlew jacocoTestReport