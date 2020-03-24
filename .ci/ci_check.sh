#!/bin/bash

set -e

cd src/test/resources
curl -LO https://github.com/WeBankFinTech/WeCross-Fabric-Stub/releases/download/resources/test_resources.tar.gz
tar -zxvf test_resources.tar.gz
cd -

./gradlew verifyGoogleJavaFormat
./gradlew build -x test
./gradlew test -i
./gradlew jacocoTestReport
