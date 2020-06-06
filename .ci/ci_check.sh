#!/bin/bash

set -e

./gradlew verifyGoogleJavaFormat

mkdir -p demo
cd demo
bash ../scripts/build_fabric_demo_chain.sh
# initialize sacc
docker exec -it cli peer chaincode install -n sacc -v 1.0 -p github.com/chaincode/sacc/
docker exec -it cli peer chaincode instantiate -o orderer.example.com:7050 --tls true --cafile /opt/gopath/src/github.com/hyperledger/fabric/peer/crypto/ordererOrganizations/example.com/orderers/orderer.example.com/msp/tlscacerts/tlsca.example.com-cert.pem -C mychannel -n sacc -l golang -v 1.0 -c '{"Args":["a","10"]}' -P 'OR ('\''Org1MSP.peer'\'','\''Org2MSP.peer'\'')'


cp -r certs/accounts/*  ../src/test/resources/accounts/
cp -r certs/chains/fabric/* ../src/test/resources/chains/fabric/
cat >>../src/test/resources/chains/fabric/stub.toml<<EOF
[[resources]]
    # name cannot be repeated
    name = 'sacc'
    type = 'FABRIC_CONTRACT'
    chainCodeName = 'sacc'
    chainLanguage = 'go'
    peers=['org1']"
EOF

cd -

./gradlew build -x test
./gradlew test -i
./gradlew jacocoTestReport