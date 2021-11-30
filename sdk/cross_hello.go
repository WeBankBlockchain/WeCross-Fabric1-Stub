package main

import (
	"errors"
	"fmt"
	"github.com/hyperledger/fabric/core/chaincode/shim"
	"github.com/hyperledger/fabric/protos/peer"
)

// TnHelloWorld implements a simple chaincode to manage an asset
type TnHelloWorld struct {
	stub shim.ChaincodeStubInterface
}

// Init is called during chaincode instantiation to initialize any
// data. Note that chaincode upgrade also calls this function to reset
// or to migrate data.
func (p *TnHelloWorld) Init(stub shim.ChaincodeStubInterface) peer.Response {
	p.stub = stub
	p.set(stub, "original")
	return shim.Success(nil)
}

// Invoke is called per transaction on the chaincode. Each transaction is
// either a 'get' or a 'set' on the asset created by Init function. The Set
// method may create a new asset by specifying a new key-value pair.
func (p *TnHelloWorld) Invoke(stub shim.ChaincodeStubInterface) peer.Response {
	// Extract the function and args from the transaction proposal
	fn, args := stub.GetFunctionAndParameters()

	var result string
	var err error
	if fn == "testSendTx" {
		result, err = p.testSendTx(stub, args)
	} else if fn == "testCall" {
		result, err = p.testCall(stub)
	} else if fn == "get" {
		result = p.get(stub)
	} else if fn == "set" {
		p.set(stub, args[0])
	} else if fn == "setCallback" {
		var nonce uint64
		nonce,_,err = ParseCallbackArgs(args)
		if err == nil {
			p.setCallback(stub, nonce)
		}
	} else if fn == "getCallback" {
		var nonce uint64
		var callbackArgs []string
		nonce, callbackArgs, err = ParseCallbackArgs(args)
		if err == nil {
			p.getCallback(stub, nonce, callbackArgs[0])
		}
	} else { // assume 'get' even if fn is nil
		err =  errors.New(fmt.Sprintf("Unsupported method: %s", fn))
	}
	if err != nil {
		return shim.Error(err.Error())
	}

	// Return the result as success payload
	return shim.Success([]byte(result))
}


func (p TnHelloWorld)testSendTx(stub shim.ChaincodeStubInterface, args []string) (string, error) {
	if len(args) != 1 {
		return "", fmt.Errorf("Incorrect arguments. Expecting a value")
	}

	nonce, err := TnSendTransaction(stub, "payment.bcos.HelloWorld", "set", []string{args[0]}, "0x222962196394e2e5ecc3fac11ab99b7446393ca1", "setCallback")

	if err != nil {
		return "", err
	}

	p.set(stub,"")

	return fmt.Sprintf("%d", nonce), nil
}

func (p TnHelloWorld)setCallback(stub shim.ChaincodeStubInterface, nonce uint64) {
	p.set(stub,"callback called")
	p.testCall(stub)
}

func (p TnHelloWorld)testCall(stub shim.ChaincodeStubInterface) (string, error) {
	nonce, err := TnCall(stub, "payment.bcos.HelloWorld", "get", []string{}, "0x222962196394e2e5ecc3fac11ab99b7446393ca1", "getCallback")

	if err != nil {
		return "", err
	}
	return fmt.Sprintf("%d", nonce), nil
}


func (p TnHelloWorld)getCallback(stub shim.ChaincodeStubInterface, nonce uint64, ret0 string) {
	p.set(stub, ret0)
}


func (p TnHelloWorld)set(stub shim.ChaincodeStubInterface, arg string) {
	stub.PutState("test", []byte(arg))
}

func (p TnHelloWorld)get(stub shim.ChaincodeStubInterface) (string) {

	value, _ := stub.GetState("test")

	return string(value)
}



// main function starts up the chaincode in the container during instantiate
func main() {
	if err := shim.Start(new(TnHelloWorld)); err != nil {
		fmt.Printf("Error starting TnHelloWorld chaincode: %s", err)
	}
}
