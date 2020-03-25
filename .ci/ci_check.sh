#!/bin/bash

set -e

mkdir -p demo
cd demo
bash ../scripts/build_fabric_demo_chain.sh

cp -r certs/accounts/*  ../src/test/resources/accounts/
cp -r certs/stubs/fabric/* ../src/test/resources/stubs/fabric/

cd -

./gradlew verifyGoogleJavaFormat
./gradlew build -x test
./gradlew test -i
./gradlew jacocoTestReport