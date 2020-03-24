#!/bin/bash

bash ./scripts/build_fabric_demo_chain.sh

cp -r certs/accounts/*  src/integTest/resources/accounts/
cp -r certs/stubs/fabric/* src/integTest/resources/stubs/fabric/

./gradlew integTest
./gradlew jacocoTestReport