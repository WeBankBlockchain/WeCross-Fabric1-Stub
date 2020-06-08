#!/bin/bash

set -e

./gradlew verifyGoogleJavaFormat

mkdir -p demo
cd demo
bash ../scripts/build_fabric_demo_chain.sh

cp -r certs/accounts/*  ../src/test/resources/accounts/
cp -r certs/chains/fabric/* ../src/test/resources/chains/fabric/

cd -

./gradlew build -x test
./gradlew test -i
./gradlew jacocoTestReport