#!/bin/bash

set -e

bash gradlew verifyGoogleJavaFormat

mkdir -p demo
cd demo
bash ../scripts/build_fabric_demo_chain.sh

cp -r certs/*  ../src/test/resources/
cp -r certs/* ../src/test/resources/luyu/

cd -

cp -r src/main/resources/chaincode src/test/resources/chains/fabric/
cp -r src/test/resources/accounts src/test/resources/luyu/chains/fabric/

bash gradlew build -x test
bash gradlew test -i
bash gradlew jacocoTestReport