/*
*   v1.0.0
*   proxy contract for WeCross
*   main entrance of all contract call
 */

package main

import (
	"bytes"
	"encoding/json"
	"fmt"
	"github.com/hyperledger/fabric/core/chaincode/shim"
	"github.com/hyperledger/fabric/protos/peer"
	"strconv"
	"strings"
)

const (
	Version            = "v1.0.0"
	RevertFlag         = "_revert"
	Separator          = "."
	NullFlag           = "null"
	SuccessFlag        = "success"
	XAStatusProcessing = "processing"
	XAStatusCommitted  = "committed"
	XAStatusRolledback = "rolledback"

	XATransactionListLenKey = "XATransactionLen"
	XATaskHeadKey           = "XATransactionTaskHead"
	ChannelKey              = "Channel"
	LockContractKey         = "Contract-%s"           // %s: chaincode name
	XATransactionKey        = "XATransaction-%s-info" // %s: xa transaction id
	XATransactionTaskKey    = "XATransaction-%d-task" // %d: index
)

type XATransactionStep struct {
	Seq       uint64 `json:"xaTransactionSeq"`
	Identity  string `json:"accountIdentity"`
	Path      string `json:"path"`
	Timestamp uint64 `json:"timestamp"`
	Method    string `json:"method"`
	Args      string `json:"args"`
}

type XATransaction struct {
	TransactionID      string              `json:"xaTransactionID"`
	Identity           string              `json:"accountIdentity"`
	Contracts          []string            `json:"contracts"`
	Paths              []string            `json:"paths"` // all paths related to this transaction
	Status             string              `json:"status"`
	StartTimestamp     uint64              `json:"startTimestamp"`
	CommitTimestamp    uint64              `json:"commitTimestamp"`
	RollbackTimestamp  uint64              `json:"rollbackTimestamp"`
	Seqs               []uint64            `json:"seqs"`
	XATransactionSteps []XATransactionStep `json:"xaTransactionSteps"`
}

type LockedContract struct {
	//Path           string  `json:"path"`
	XATransactionID string `json:"xaTransactionID"`
}

type Proxy struct {
}

func (p *Proxy) Init(stub shim.ChaincodeStubInterface) (res peer.Response) {
	defer func() {
		if r := recover(); r != nil {
			res = shim.Error(fmt.Sprintf("%v", r))
		}
	}()
	fn, args := stub.GetFunctionAndParameters()

	switch fn {
	case "init":
		res = p.init(stub, args)
	default:
		res = shim.Success(nil)
	}
	return
}

func (p *Proxy) Invoke(stub shim.ChaincodeStubInterface) (res peer.Response) {
	defer func() {
		if r := recover(); r != nil {
			res = shim.Error(fmt.Sprintf("%v", r))
		}
	}()

	fn, args := stub.GetFunctionAndParameters()

	switch fn {
	case "init":
		res = p.init(stub, args)
	case "getVersion":
		res = p.getVersion()
	case "constantCall":
		res = p.constantCall(stub, args)
	case "sendTransaction":
		res = p.sendTransaction(stub, args)
	case "startXATransaction":
		res = p.startXATransaction(stub, args)
	case "commitXATransaction":
		res = p.commitXATransaction(stub, args)
	case "rollbackXATransaction":
		res = p.rollbackXATransaction(stub, args)
	case "getXATransactionNumber":
		res = p.getXATransactionNumber(stub)
	case "listXATransactions":
		res = p.listXATransactions(stub, args)
	case "getXATransaction":
		res = p.getXATransaction(stub, args)
	case "getLatestXATransaction":
		res = p.getLatestXATransaction(stub)
	case "rollbackAndDeleteXATransactionTask":
		res = p.rollbackAndDeleteXATransactionTask(stub, args)
	case "getXATransactionState":
		res = p.getXATransactionState(stub, args)
	default:
		res = shim.Error("invalid function name")
	}

	return
}

// set channel
func (p *Proxy) init(stub shim.ChaincodeStubInterface, args []string) peer.Response {
	if len(args) != 1 {
		return shim.Error("invalid arguments, [channel] expected")
	}

	channel := args[0]
	err := stub.PutState(ChannelKey, []byte(channel))
	checkError(err)
	err = stub.PutState(XATransactionListLenKey, []byte("0"))
	checkError(err)
	err = stub.PutState(XATaskHeadKey, []byte("0"))
	checkError(err)

	return shim.Success([]byte(SuccessFlag))
}

func (p *Proxy) getVersion() peer.Response {
	return shim.Success([]byte(Version))
}

// query
func (p *Proxy) constantCall(stub shim.ChaincodeStubInterface, args []string) peer.Response {
	if len(args) != 4 {
		return shim.Error("invalid arguments")
	}
	xaTransactionID, path, method, thisArgs := args[0], args[1], args[2], args[3]

	chaincodeName := getNameFromPath(path)

	var lockedContract LockedContract
	isLocked := getLockedContract(stub, chaincodeName, &lockedContract)

	if xaTransactionID == "0" {
		if isLocked {
			return shim.Error("resource is locked by unfinished xa transaction: " + lockedContract.XATransactionID)
		}
		return callContract(stub, chaincodeName, method, thisArgs)
	}

	if !isExistedXATransaction(stub, xaTransactionID) {
		return shim.Error("xa transaction id not found")
	}

	if lockedContract.XATransactionID != xaTransactionID {
		return shim.Error(path + "is unregistered in xa transaction " + xaTransactionID)
	}

	return callContract(stub, chaincodeName, method, thisArgs)
}

// invoke
func (p *Proxy) sendTransaction(stub shim.ChaincodeStubInterface, args []string) peer.Response {
	if len(args) != 6 {
		return shim.Error("invalid arguments")
	}
	uniqueID, xaTransactionID, xaTransactionSeq, path, method, realArgs := args[0], args[1], stringToUint64(args[2]), args[3], args[4], args[5]

	res, err := stub.GetState(uniqueID)
	checkError(err)
	if res != nil {
		return shim.Success(res)
	}

	chaincodeName := getNameFromPath(path)

	var lockedContract LockedContract
	isLocked := getLockedContract(stub, chaincodeName, &lockedContract)

	if xaTransactionID == "0" {
		if isLocked {
			return shim.Error(path + " is locked by unfinished xa transaction: " + lockedContract.XATransactionID)
		}
		return callContract(stub, chaincodeName, method, realArgs)
	}

	if !isExistedXATransaction(stub, xaTransactionID) {
		return shim.Error("xa transaction not found")
	}

	var xaTransaction XATransaction
	getXATransaction(stub, xaTransactionID, &xaTransaction)
	if xaTransaction.Status == XAStatusCommitted {
		return shim.Error("xa transaction has been committed")
	}

	if xaTransaction.Status == XAStatusRolledback {
		return shim.Error("xa transaction has been rolledback")
	}

	if lockedContract.XATransactionID != xaTransactionID {
		return shim.Error(path + "is unregistered in xa transaction " + xaTransactionID)
	}

	if !isValidSeq(stub, xaTransactionID, xaTransactionSeq) {
		return shim.Error("xaTransactionSeq should be greater than before")
	}

	timeStamp, err := stub.GetTxTimestamp()
	checkError(err)

	// recode transactionStep
	var xaTransactionStep = XATransactionStep{
		Seq:       xaTransactionSeq,
		Identity:  getIdentity(stub),
		Path:      path,
		Timestamp: uint64(timeStamp.Seconds),
		Method:    method,
		Args:      realArgs,
	}

	xaTransaction.Seqs = append(xaTransaction.Seqs, xaTransactionSeq)
	xaTransaction.XATransactionSteps = append(xaTransaction.XATransactionSteps, xaTransactionStep)

	// recode xaTransaction
	xa, err := json.Marshal(&xaTransaction)
	checkError(err)
	err = stub.PutState(getXATransactionKey(xaTransactionID), xa)
	checkError(err)

	response := callContract(stub, chaincodeName, method, realArgs)
	if response.Status == shim.OK {
		err = stub.PutState(uniqueID, response.Payload)
		checkError(err)
	}
	return response
}

/*
 * @args transactionID || selfPaths || otherPaths
 * result: success
 */
func (p *Proxy) startXATransaction(stub shim.ChaincodeStubInterface, args []string) peer.Response {
	argsLen := len(args)
	if argsLen != 3 {
		return shim.Error("invalid arguments")
	}

	xaTransactionID := args[0]
	if isExistedXATransaction(stub, xaTransactionID) {
		return shim.Error("xa transaction " + xaTransactionID + " already exists")
	}

	var selfPaths, otherPaths, allPaths, contracts []string
	err := json.Unmarshal([]byte(args[1]), &selfPaths)
	checkError(err)
	err = json.Unmarshal([]byte(args[2]), &otherPaths)
	checkError(err)

	for i := 0; i < len(selfPaths); i++ {
		chaincodeName := getNameFromPath(selfPaths[i])
		contracts = append(contracts, chaincodeName)
		var lockedContract LockedContract
		hasInfo := getLockedContract(stub, chaincodeName, &lockedContract)
		// contract conflict
		if hasInfo {
			return shim.Error(selfPaths[i] + " is locked by unfinished xa transaction: " + lockedContract.XATransactionID)
		}
		lockedContract = LockedContract{
			XATransactionID: xaTransactionID,
		}
		lc, err := json.Marshal(&lockedContract)
		checkError(err)
		err = stub.PutState(getLockContractKey(chaincodeName), lc)
		checkError(err)
		allPaths = append(allPaths, selfPaths[i])
	}

	for i := 0; i < len(otherPaths); i++ {
		allPaths = append(allPaths, otherPaths[i])
	}

	timeStamp, err := stub.GetTxTimestamp()
	checkError(err)
	var xaTransaction = XATransaction{
		TransactionID:      xaTransactionID,
		Identity:           getIdentity(stub),
		Contracts:          contracts,
		Paths:              allPaths,
		Status:             XAStatusProcessing,
		StartTimestamp:     uint64(timeStamp.Seconds),
		CommitTimestamp:    0,
		RollbackTimestamp:  0,
		Seqs:               []uint64{},
		XATransactionSteps: []XATransactionStep{},
	}

	xa, err := json.Marshal(&xaTransaction)
	checkError(err)
	err = stub.PutState(getXATransactionKey(xaTransactionID), xa)
	checkError(err)

	addXATransaction(stub, xaTransactionID)
	return shim.Success([]byte(SuccessFlag))
}

/*
 * result: success
 */
func (p *Proxy) commitXATransaction(stub shim.ChaincodeStubInterface, args []string) peer.Response {
	if len(args) != 1 {
		return shim.Error("invalid arguments")
	}

	xaTransactionID := args[0]
	if !isExistedXATransaction(stub, xaTransactionID) {
		return shim.Error("xa transaction not found")
	}

	var xaTransaction XATransaction
	getXATransaction(stub, xaTransactionID, &xaTransaction)

	if xaTransaction.Status == XAStatusCommitted {
		return shim.Error("xa transaction has been committed")
	}

	if xaTransaction.Status == XAStatusRolledback {
		return shim.Error("xa transaction has been rolledback")
	}

	timeStamp, err := stub.GetTxTimestamp()
	checkError(err)
	xaTransaction.Status = XAStatusCommitted
	xaTransaction.CommitTimestamp = uint64(timeStamp.Seconds)

	xa, err := json.Marshal(&xaTransaction)
	checkError(err)
	err = stub.PutState(getXATransactionKey(xaTransactionID), xa)
	checkError(err)

	deleteLockedContracts(stub, xaTransactionID)
	return shim.Success([]byte(SuccessFlag))
}

/*
 * result: success | warning message
 */
func (p *Proxy) rollbackXATransaction(stub shim.ChaincodeStubInterface, args []string) peer.Response {
	if len(args) != 1 {
		return shim.Error("invalid arguments")
	}

	xaTransactionID := args[0]
	if !isExistedXATransaction(stub, xaTransactionID) {
		return shim.Error("xa transaction not found")
	}

	var xaTransaction XATransaction
	getXATransaction(stub, xaTransactionID, &xaTransaction)

	if xaTransaction.Status == XAStatusCommitted {
		return shim.Error("xa transaction has been committed")
	}

	if xaTransaction.Status == XAStatusRolledback {
		return shim.Error("xa transaction has been rolledback")
	}

	var res = SuccessFlag
	var message = "warning:"
	for i := len(xaTransaction.XATransactionSteps) - 1; i >= 0; i-- {
		transactionStep := xaTransaction.XATransactionSteps[i]
		newMethod := getRevertFunc(transactionStep.Method)
		chaincodeName := getNameFromPath(transactionStep.Path)

		// call revert function
		response := callContract(stub, chaincodeName, newMethod, transactionStep.Args)
		if response.Status != shim.OK {
			message = message + " revert \"" + transactionStep.Method + "\" failed."
			res = message
		}
	}

	timeStamp, err := stub.GetTxTimestamp()
	checkError(err)
	xaTransaction.Status = XAStatusRolledback
	xaTransaction.RollbackTimestamp = uint64(timeStamp.Seconds)

	xa, err := json.Marshal(&xaTransaction)
	checkError(err)
	err = stub.PutState(getXATransactionKey(xaTransactionID), xa)
	checkError(err)

	deleteLockedContracts(stub, xaTransactionID)

	return shim.Success([]byte(res))
}

// return json string
func (p *Proxy) getXATransaction(stub shim.ChaincodeStubInterface, args []string) peer.Response {
	if len(args) != 1 {
		return shim.Error("invalid arguments")
	}

	xaTransactionID := args[0]
	if !isExistedXATransaction(stub, xaTransactionID) {
		return shim.Error("xa transaction not found")
	}

	xa, err := stub.GetState(getXATransactionKey(xaTransactionID))
	checkError(err)

	return shim.Success(xa)
}

func (p *Proxy) getXATransactionNumber(stub shim.ChaincodeStubInterface) peer.Response {
	num, err := stub.GetState(XATransactionListLenKey)
	checkError(err)

	return shim.Success(num)
}

// return all transaction ids
func (p *Proxy) listXATransactions(stub shim.ChaincodeStubInterface, args []string) peer.Response {
	if len(args) != 2 {
		return shim.Error("invalid arguments")
	}

	xaLen, err := stub.GetState(XATransactionListLenKey)
	checkError(err)
	length := bytesToUint64(xaLen)

	var index uint64
	if "-1" == args[0] {
		index = length - 1
	} else {
		index = stringToUint64(args[0])
	}

	size := stringToInt(args[1])

	if length == 0 || length < index {
		return shim.Success([]byte("{\"total\":0,\"xaTransactions\":[]}"))
	}

	type XAInfo struct {
		TransactionID string   `json:"xaTransactionID"`
		Identity      string   `json:"accountIdentity"`
		Status        string   `json:"status"`
		Timestamp     uint64   `json:"timestamp"`
		Paths         []string `json:"paths"`
	}

	type XAList struct {
		Total          uint64   `json:"total"`
		XATransactions []XAInfo `json:"xaTransactions"`
	}

	var xaList XAList
	var i int

	for i = 0; i < size && index >= uint64(i); i++ {
		tid, err := stub.GetState(getTransactionTaskKey(index - uint64(i)))
		checkError(err)

		var xaTransaction XATransaction
		getXATransaction(stub, string(tid), &xaTransaction)
		var info = XAInfo{
			TransactionID: string(tid),
			Identity:      getIdentity(stub),
			Status:        xaTransaction.Status,
			Timestamp:     xaTransaction.StartTimestamp,
			Paths:         xaTransaction.Paths,
		}
		xaList.XATransactions = append(xaList.XATransactions, info)
	}

	xaList.Total = length
	res, err := json.Marshal(&xaList)
	checkError(err)

	return shim.Success(res)
}

// called by router to check transaction status
func (p *Proxy) getLatestXATransaction(stub shim.ChaincodeStubInterface) peer.Response {
	xaTransactionID := getLatestTransactionID(stub)

	if xaTransactionID == NullFlag {
		return shim.Success([]byte(NullFlag))
	}

	return p.getXATransaction(stub, []string{xaTransactionID})
}

func (p *Proxy) rollbackAndDeleteXATransactionTask(stub shim.ChaincodeStubInterface, args []string) peer.Response {
	if len(args) != 1 {
		return shim.Error("invalid arguments")
	}

	res := p.rollbackXATransaction(stub, args)
	if res.Status == shim.ERROR {
		return res
	}

	return deleteLatestTransaction(stub, args[1])
}

func (p *Proxy) getXATransactionState(stub shim.ChaincodeStubInterface, args []string) peer.Response {
	if len(args) != 1 {
		return shim.Error("invalid arguments")
	}

	path := args[0]
	chaincodeName := getNameFromPath(path)

	var lockedContract LockedContract
	isLocked := getLockedContract(stub, chaincodeName, &lockedContract)

	if !isLocked {
		return shim.Success([]byte(NullFlag))
	} else {
		seq := getCurrentSeq(stub, lockedContract.XATransactionID)
		return shim.Success([]byte(lockedContract.XATransactionID + " " + strconv.FormatUint(seq, 10)))
	}
}

func callContract(stub shim.ChaincodeStubInterface, contract, method, jsonArgs string) peer.Response {
	// parse args from json str
	var args []string
	err := json.Unmarshal([]byte(jsonArgs), &args)
	checkError(err)

	var trans [][]byte
	trans = append(trans, []byte(method))
	for _, param := range args {
		trans = append(trans, []byte(param))
	}

	channel, err := stub.GetState(ChannelKey)
	checkError(err)

	return stub.InvokeChaincode(contract, trans, string(channel))
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

func addXATransaction(stub shim.ChaincodeStubInterface, transactionID string) {
	xaLen, err := stub.GetState(XATransactionListLenKey)
	checkError(err)

	index := bytesToUint64(xaLen)
	err = stub.PutState(getTransactionTaskKey(index), []byte(transactionID))
	checkError(err)

	err = stub.PutState(XATransactionListLenKey, uint64ToBytes(index+1))
	checkError(err)
}

func getLatestTransactionID(stub shim.ChaincodeStubInterface) string {
	taskLen, err := stub.GetState(XATransactionListLenKey)
	checkError(err)

	head, err := stub.GetState(XATaskHeadKey)
	checkError(err)

	if bytesToUint64(taskLen) == 0 || bytesToUint64(head) >= bytesToUint64(taskLen) {
		return NullFlag
	}

	id, err := stub.GetState(getTransactionTaskKey(bytesToUint64(head)))
	checkError(err)

	return string(id)
}

func deleteLatestTransaction(stub shim.ChaincodeStubInterface, transactionID string) peer.Response {
	taskLen, err := stub.GetState(XATransactionListLenKey)
	checkError(err)

	head, err := stub.GetState(XATaskHeadKey)
	checkError(err)

	if bytesToUint64(taskLen) == 0 || bytesToUint64(head) >= bytesToUint64(taskLen) {
		return shim.Error("delete nonexistent xa transaction")
	}

	id, err := stub.GetState(getTransactionTaskKey(bytesToUint64(head)))
	checkError(err)

	if string(id) != transactionID {
		return shim.Error("delete unmatched transaction")
	}

	err = stub.PutState(XATaskHeadKey, uint64ToBytes(bytesToUint64(head)+1))
	checkError(err)

	return shim.Success([]byte(SuccessFlag))
}

func getNameFromPath(path string) string {
	strs := strings.Split(path, Separator)
	if len(strs) != 3 {
		panic(fmt.Errorf("invalid path: " + path))
	}

	return strs[2]
}

func getRevertFunc(method string) string {
	return method + RevertFlag
}

func isExistedXATransaction(stub shim.ChaincodeStubInterface, xaTransactionID string) bool {
	id, err := stub.GetState(getXATransactionKey(xaTransactionID))
	checkError(err)

	return id != nil
}

func isValidSeq(stub shim.ChaincodeStubInterface, xaTransactionID string, seq uint64) bool {
	var xaTransaction XATransaction
	getXATransaction(stub, xaTransactionID, &xaTransaction)
	index := len(xaTransaction.Seqs)
	return (index == 0) || (seq > xaTransaction.Seqs[index-1])
}

func getCurrentSeq(stub shim.ChaincodeStubInterface, xaTransactionID string) uint64 {
	var xaTransaction XATransaction
	getXATransaction(stub, xaTransactionID, &xaTransaction)
	index := len(xaTransaction.Seqs)
	if index == 0 {
		return 0
	} else {
		return xaTransaction.Seqs[index-1]
	}
}

func getXATransaction(stub shim.ChaincodeStubInterface, xaTransactionID string, xa *XATransaction) {
	data, err := stub.GetState(getXATransactionKey(xaTransactionID))
	checkError(err)

	err = json.Unmarshal(data, xa)
	checkError(err)
}

func getLockedContract(stub shim.ChaincodeStubInterface, contract string, lc *LockedContract) bool {
	state, err := stub.GetState(getLockContractKey(contract))
	checkError(err)

	if state == nil {
		return false
	} else {
		err = json.Unmarshal(state, lc)
		checkError(err)
		return true
	}
}

func deleteLockedContracts(stub shim.ChaincodeStubInterface, transactionID string) {
	var xaTransaction XATransaction
	getXATransaction(stub, transactionID, &xaTransaction)

	for _, contract := range xaTransaction.Contracts {
		err := stub.DelState(getLockContractKey(contract))
		checkError(err)
	}
}

func getLockContractKey(contract string) string {
	return fmt.Sprintf(LockContractKey, contract)
}

func getXATransactionKey(transactionID string) string {
	return fmt.Sprintf(XATransactionKey, transactionID)
}

func getTransactionTaskKey(index uint64) string {
	return fmt.Sprintf(XATransactionTaskKey, index)
}

func stringToUint64(str string) uint64 {
	i, e := strconv.Atoi(str)
	if e != nil {
		return 0
	}
	return uint64(i)
}

func stringToInt(str string) int {
	i, e := strconv.Atoi(str)
	if e != nil {
		return 0
	}
	return i
}

func bytesToUint64(bts []byte) uint64 {
	u, err := strconv.ParseUint(string(bts), 10, 64)
	checkError(err)

	return u
}

func uint64ToString(u uint64) string {
	return strconv.FormatUint(u, 10)
}

func uint64ToBytes(u uint64) []byte {
	return []byte(uint64ToString(u))
}
func checkError(err error) {
	if err != nil {
		panic(err)
	}
}

func main() {
	err := shim.Start(new(Proxy))
	if err != nil {
		fmt.Printf("Error: %s", err)
	}
}
