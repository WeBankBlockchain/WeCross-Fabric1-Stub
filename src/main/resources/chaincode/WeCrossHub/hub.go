/*
*   v1.0.0
*   hub contract for WeCross
*   main entrance of interchain call
 */

package main

import (
	"bytes"
	"encoding/json"
	"fmt"
	"github.com/hyperledger/fabric/core/chaincode/shim"
	"github.com/hyperledger/fabric/protos/peer"
	"strconv"
)

const (
	Version        = "v1.0.0"
	NilFlag        = "null"
	CallTypeQuery  = "0"
	CallTypeInvoke = "1"

	ChannelKey         = "channel"
	CurrentIndexKey    = "current_index"
	IncrementKey       = "increment"
	RequestsKey        = "request_%s"          // %s: uid
	CallbackResultsKey = "callback_results_%s" // %s: uid
)

type Hub struct {
}

func (h *Hub) Init(stub shim.ChaincodeStubInterface) (res peer.Response) {
	defer func() {
		if r := recover(); r != nil {
			res = shim.Error(fmt.Sprintf("%v", r))
		}
	}()

	_, args := stub.GetFunctionAndParameters()
	if len(args) != 1 {
		return shim.Error("invalid arguments, [channel] expected")
	}

	err := stub.PutState(ChannelKey, []byte(args[0]))
	checkError(err)

	err = stub.PutState(IncrementKey, []byte("0"))
	checkError(err)

	err = stub.PutState(CurrentIndexKey, []byte("0"))
	checkError(err)

	return shim.Success(nil)
}

func (h *Hub) Invoke(stub shim.ChaincodeStubInterface) (res peer.Response) {
	defer func() {
		if r := recover(); r != nil {
			res = shim.Error(fmt.Sprintf("%v", r))
		}
	}()

	fcn, args := stub.GetFunctionAndParameters()

	switch fcn {
	case "getVersion":
		res = h.getVersion()
	case "getIncrement":
		res = h.getIncrement(stub)
	case "getInterchainRequests":
		res = h.getInterchainRequests(stub, args)
	case "updateCurrentRequestIndex":
		res = h.updateCurrentRequestIndex(stub, args)
	case "interchainInvoke":
		res = h.interchainInvoke(stub, args)
	case "interchainQuery":
		res = h.interchainQuery(stub, args)
	case "registerCallbackResult":
		res = h.registerCallbackResult(stub, args)
	case "selectCallbackResult":
		res = h.selectCallbackResult(stub, args)
	default:
		res = shim.Error("invalid function name")
	}

	return
}

func (h *Hub) getVersion() peer.Response {
	return shim.Success([]byte(Version))
}

func (h *Hub) getIncrement(stub shim.ChaincodeStubInterface) peer.Response {
	increment, err := stub.GetState(IncrementKey)
	checkError(err)

	return shim.Success(increment)
}

/*
 * invoke other chain
 * @args path || method || args || callbackPath || callbackMethod
 */
func (h *Hub) interchainInvoke(stub shim.ChaincodeStubInterface, args []string) peer.Response {
	if len(args) != 5 {
		return shim.Error("incorrect number of arguments, expecting 5")
	}

	uid := handleRequest(stub, CallTypeInvoke, args[0], args[1], args[2], args[3], args[4])

	return shim.Success(uint64ToBytes(uid))
}

// query other chain, not support right now
func (h *Hub) interchainQuery(stub shim.ChaincodeStubInterface, args []string) peer.Response {
	if len(args) != 5 {
		return shim.Error("incorrect number of arguments, expecting 5")
	}

	uid := handleRequest(stub, CallTypeQuery, args[0], args[1], args[2], args[3], args[4])

	return shim.Success(uint64ToBytes(uid))
}

func handleRequest(stub shim.ChaincodeStubInterface, callType, path, method, args, callbackPath, callbackMethod string) uint64 {
	increment, err := stub.GetState(IncrementKey)
	checkError(err)

	uid := bytesToUint64(increment) + 1
	creator, err := stub.GetCreator()
	checkError(err)

	certStart := bytes.IndexAny(creator, "-----BEGIN")
	if certStart == -1 {
		panic("no certificate found")
	}

	request := []string{string(uint64ToBytes(uid)), callType, path, method, args, callbackPath, callbackMethod, string(creator[certStart:])}

	requestData, err := json.Marshal(request)
	checkError(err)

	err = stub.PutState(IncrementKey, uint64ToBytes(uid))
	checkError(err)

	err = stub.PutState(getRequestsKey(string(uint64ToBytes(uid))), requestData)
	checkError(err)

	return uid
}

/*
 * @args uid || tid || seq || errorCOde || errorMsg || result
 */
func (h *Hub) registerCallbackResult(stub shim.ChaincodeStubInterface, args []string) peer.Response {
	if len(args) != 6 {
		return shim.Error("incorrect number of arguments, expecting 6")
	}

	result := []string{args[1], args[2], args[3], args[4], args[5]}

	resultData, err := json.Marshal(result)
	checkError(err)

	err = stub.PutState(getCallbackResultsKey(args[0]), resultData)
	checkError(err)

	return shim.Success(nil)
}

func (h *Hub) selectCallbackResult(stub shim.ChaincodeStubInterface, args []string) peer.Response {
	if len(args) != 1 {
		return shim.Error("incorrect number of arguments, expecting 1")
	}

	uid := args[0]
	result, err := stub.GetState(getCallbackResultsKey(uid))
	checkError(err)

	return shim.Success(result)
}

func (h *Hub) getInterchainRequests(stub shim.ChaincodeStubInterface, args []string) peer.Response {
	if len(args) != 1 {
		return shim.Error("incorrect number of arguments, [num] expected")
	}

	increment, err := stub.GetState(IncrementKey)
	checkError(err)

	currentIndex, err := stub.GetState(CurrentIndexKey)
	checkError(err)

	total := bytesToUint64(increment)
	current := bytesToUint64(currentIndex)
	if total == current {
		return shim.Success([]byte(NilFlag))
	}

	num := bytesToUint64([]byte(args[0]))
	var realNum uint64

	if num < (total - current) {
		realNum = num
	} else {
		realNum = total - current
	}

	var tempRequests []string
	var i uint64
	for i = 0; i < realNum; i++ {
		request, err := stub.GetState(getRequestsKey(string(uint64ToBytes(current + i + 1))))
		checkError(err)
		tempRequests = append(tempRequests, string(request))
	}

	requestsData, err := json.Marshal(tempRequests)
	checkError(err)

	return shim.Success(requestsData)
}

func (h *Hub) updateCurrentRequestIndex(stub shim.ChaincodeStubInterface, args []string) peer.Response {
	if len(args) != 1 {
		return shim.Error("incorrect number of arguments, [uid] expected")
	}

	increment, err := stub.GetState(IncrementKey)
	checkError(err)

	currentIndex, err := stub.GetState(CurrentIndexKey)
	checkError(err)

	total := bytesToUint64(increment)
	current := bytesToUint64(currentIndex)
	index := bytesToUint64([]byte(args[0]))

	if current < index && index <= total {
		err = stub.PutState(CurrentIndexKey, []byte(args[0]))
		checkError(err)
	}
	return shim.Success(nil)
}

func getRequestsKey(uid string) string {
	return fmt.Sprintf(RequestsKey, uid)
}

func getCallbackResultsKey(uid string) string {
	return fmt.Sprintf(CallbackResultsKey, uid)
}

func bytesToUint64(bts []byte) uint64 {
	u, err := strconv.ParseUint(string(bts), 10, 64)
	checkError(err)

	return u
}

func uint64ToBytes(u uint64) []byte {
	return []byte(strconv.FormatUint(u, 10))
}

func checkError(err error) {
	if err != nil {
		panic(err)
	}
}

func main() {
	err := shim.Start(new(Hub))
	if err != nil {
		fmt.Printf("Error: %s", err)
	}
}
