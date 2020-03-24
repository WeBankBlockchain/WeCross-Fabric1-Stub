#!/bin/bash

set -e

bash ./scripts/build_fabric_demo_chain.sh

cp -r certs/accounts/*  src/test/resources/accounts/
cp -r certs/stubs/fabric/* src/test/resources/stubs/fabric/

./gradlew verifyGoogleJavaFormat
./gradlew build -x test
./gradlew test -i
./gradlew jacocoTestReport