package main

import (
	"bytes"
	"encoding/json"
	"errors"
	"fmt"
	"math/rand"
	"github.com/hyperledger/fabric/core/chaincode/shim"
	"strconv"
)

type TnEvent struct {
	Path string `json:"path"`
	Method string `json:"method"`
	Args []string `json:"args"`
	Nonce uint64 `json:"nonce"`
	Identity string `json:"identity"`
	CallbackMethod string `json:"callbackMethod"`
	Sender string `json:"sender"`
}

func TnSendTransaction(stub shim.ChaincodeStubInterface, path string, method string, args []string, identity string, callbackMethod string) (uint64, error) {
	nonce := uint64(rand.Uint32())
	tx := TnEvent {
		Path: path,
		Method: method,
		Args: args,
		Identity: identity,
		CallbackMethod: callbackMethod,
		Nonce: nonce,
		Sender: getIdentity(stub),
	}

	txBytes, err := json.Marshal(tx)
	if err != nil {
		return nonce, fmt.Errorf("error")
	}

	stub.SetEvent("_event_sendTransaction", txBytes)

	return nonce, nil
}

func TnCall(stub shim.ChaincodeStubInterface, path string, method string, args []string, identity string, callbackMethod string) (uint64, error) {
	nonce := uint64(rand.Uint32())
	tx := TnEvent {
		Path: path,
		Method: method,
		Args: args,
		Identity: identity,
		CallbackMethod: callbackMethod,
		Nonce: nonce,
		Sender: getIdentity(stub),
	}

	txBytes, err := json.Marshal(tx)
	if err != nil {
		return nonce, fmt.Errorf("error")
	}

	stub.SetEvent("_event_call", txBytes)

	return nonce, nil
}

func checkError(err error) {
	if err != nil {
		panic(err)
	}
}

func getIdentity(stub shim.ChaincodeStubInterface) string {
	creator, err := stub.GetCreator()
	checkError(err)

	certStart := bytes.IndexAny(creator, "-----BEGIN")
	if certStart == -1 {
		panic("no certificate found")
	}

	return string(creator[certStart:])
}

func ParseCallbackArgs(args []string) (uint64, []string, error){
	if len(args) == 0 {
		return 0, nil, errors.New("Error: Callback args[] must start with nonce")
	}
	nonce, err := strconv.ParseUint(args[0], 10, 32)
	if err != nil {
		return 0, nil, err
	}
	return nonce, args[1:], nil
}

