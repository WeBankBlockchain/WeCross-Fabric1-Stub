#! /bin/bash

version=${1}
rm /home/jimmyshi/wecross-demo/fabric/fabric-samples-1.4.4/chaincode/tnhello/*
cp *.go /home/jimmyshi/wecross-demo/fabric/fabric-samples-1.4.4/chaincode/tnhello/
docker exec -it cli peer chaincode install -n tnhello${version} -v ${version} -p github.com/chaincode/tnhello/
docker exec -it cli peer chaincode instantiate -o orderer.example.com:7050 --tls true --cafile /opt/gopath/src/github.com/hyperledger/fabric/peer/crypto/ordererOrganizations/example.com/orderers/orderer.example.com/msp/tlscacerts/tlsca.example.com-cert.pem -C mychannel -n tnhello${version} -l golang -v ${version} -c '{"Args":["init","a","100"]}' -P "OR ('Org1MSP.peer','Org2MSP.peer')"
