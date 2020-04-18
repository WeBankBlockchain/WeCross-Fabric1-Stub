#!/bin/bash

set -e

mkdir -p demo
cd demo
bash ../scripts/build_fabric_demo_chain.sh

./gradlew verifyGoogleJavaFormat
./gradlew build -x test

cp -r certs/accounts/*  ../src/test/resources/accounts/
cp -r certs/chains/fabric/* ../src/test/resources/chains/fabric/

cd -


./gradlew test -i
./gradlew jacocoTestReport