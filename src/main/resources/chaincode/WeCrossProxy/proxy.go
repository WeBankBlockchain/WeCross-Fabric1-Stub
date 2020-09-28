/*
*   v1.0.0-rc4
*   proxy contract for WeCross
*   main entrance of all contract call
 */

package main

import (
	"encoding/json"
	"fmt"
	"github.com/hyperledger/fabric/core/chaincode/shim"
	"github.com/hyperledger/fabric/protos/peer"
	"strconv"
	"strings"
)

const (
	Version     = "v1.0.0-rc5"
	RevertFlag  = "_revert"
	Separator   = "."
	NullFlag    = "null"
	SuccessFlag = "0"

	TaskLenKey         = "TaskLen"
	TaskHeadKey        = "TaskHead"
	FinishedTasksKey   = "FinishedTasks"
	ChannelKey         = "Channel"
	LockContractKey    = "Contract-%s"         // %s: chaincode name
	TransactionInfoKey = "Transaction-%s-info" // %s: transaction id
	TransactionTaskKey = "Transaction-%d-task" // %d: index

)

type TransactionStep struct {
	Seq       uint   `json:"seq"`
	Path      string `json:"path"`
	Timestamp string `json:"timestamp"`
	Func      string `json:"func"`
	Args      string `json:"args"`
}

type TransactionInfo struct {
	TransactionID     string            `json:"transactionID"`
	Contracts         []string          `json:"contracts"`
	AllPaths          []string          `json:"allPaths"` // all paths related to this transaction
	Paths             []string          `json:"paths"`    // paths related to current chain
	Status            int               `json:"status"`
	StartTimestamp    string            `json:"startTimestamp"`
	CommitTimestamp   string            `json:"commitTimestamp"`
	RollbackTimestamp string            `json:"rollbackTimestamp"`
	Seqs              []uint            `json:"seqs"`
	TransactionSteps  []TransactionStep `json:"transactionSteps"`
}

type LockedContractInfo struct {
	//Path           string  `json:"path"`
	TransactionID string `json:"transactionID"`
}

type ArgsJsonTemplate struct {
	Args []string `json:"args"`
}

type ProxyChaincode struct {
}

func (p *ProxyChaincode) Init(stub shim.ChaincodeStubInterface) (res peer.Response) {
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

func (p *ProxyChaincode) Invoke(stub shim.ChaincodeStubInterface) (res peer.Response) {
	defer func() {
		if r := recover(); r != nil {
			res = shim.Error(fmt.Sprintf("%v", r))
		}
	}()

	fcn, args := stub.GetFunctionAndParameters()

	switch fcn {
	case "init":
		res = p.init(stub, args)
	case "getVersion":
		res = p.getVersion(stub, args)
	case "constantCall":
		res = p.constantCall(stub, args)
	case "sendTransaction":
		res = p.sendTransaction(stub, args)
	case "startTransaction":
		res = p.startTransaction(stub, args)
	case "commitTransaction":
		res = p.commitTransaction(stub, args)
	case "rollbackTransaction":
		res = p.rollbackTransaction(stub, args)
	case "getAllTransactionIDs":
		res = p.getAllTransactionIDs(stub)
	case "getFinishedTransactionIDs":
		res = p.getFinishedTransactionIDs(stub)
	case "getTransactionInfo":
		res = p.getTransactionInfo(stub, args)
	case "getLatestTransactionInfo":
		res = p.getLatestTransactionInfo(stub)
	case "rollbackAndDeleteTransaction":
		res = p.rollbackAndDeleteTransaction(stub, args)
	case "getTransactionState":
		res = p.getTransactionState(stub, args)
	default:
		res = shim.Error("invalid function name")
	}

	return
}

// set channel
func (p *ProxyChaincode) init(stub shim.ChaincodeStubInterface, args []string) peer.Response {
	if len(args) != 1 {
		return shim.Error("invalid arguments, [channel] expected")
	}

	channel := args[0]
	err := stub.PutState(ChannelKey, []byte(channel))
	checkError(err)
	err = stub.PutState(TaskLenKey, []byte("0"))
	checkError(err)
	err = stub.PutState(TaskHeadKey, []byte("1"))
	checkError(err)

	return shim.Success([]byte(SuccessFlag))
}

func (p *ProxyChaincode) getVersion(stub shim.ChaincodeStubInterface, args []string) peer.Response {
	return shim.Success([]byte(Version))
}

// query
func (p *ProxyChaincode) constantCall(stub shim.ChaincodeStubInterface, args []string) peer.Response {
	if len(args) != 4 {
		return shim.Error("invalid arguments")
	}
	transactionID, path, method, thisArgs := args[0], args[1], args[2], args[3]

	chaincodeName := getNameFromPath(path)
	if transactionID == "0" {
		return callContract(stub, chaincodeName, method, thisArgs)
	}

	if !isExistedTransaction(stub, transactionID) {
		return shim.Error("transaction id not found")
	}

	var lockedContractInfo LockedContractInfo
	getLockedContractInfo(stub, chaincodeName, &lockedContractInfo)

	if lockedContractInfo.TransactionID != transactionID {
		return shim.Error(path + "is unregistered in transaction " + transactionID)
	}

	return callContract(stub, chaincodeName, method, thisArgs)
}

// invoke
func (p *ProxyChaincode) sendTransaction(stub shim.ChaincodeStubInterface, args []string) peer.Response {
	if len(args) != 6 {
		return shim.Error("invalid arguments")
	}
	uniqueID, transactionID, seq, path, method, thisArgs := args[0], args[1], stringToUint(args[2]), args[3], args[4], args[5]

	uid, err := stub.GetState(uniqueID)
	checkError(err)
	if uid != nil {
		return shim.Error("transaction unique id is repeated")
	}

	chaincodeName := getNameFromPath(path)

	var lockedContractInfo LockedContractInfo
	hasInfo := getLockedContractInfo(stub, chaincodeName, &lockedContractInfo)

	if transactionID == "0" {
		if hasInfo {
			return shim.Error(path + " is locked by unfinished transaction: " + lockedContractInfo.TransactionID)
		}
		return callContract(stub, chaincodeName, method, thisArgs)
	}

	if !isExistedTransaction(stub, transactionID) {
		return shim.Error("transaction not found")
	}

	var transactionInfo TransactionInfo
	getTransactionInfo(stub, transactionID, &transactionInfo)
	if transactionInfo.Status == 1 {
		return shim.Error("transaction has been committed")
	}

	if transactionInfo.Status == 2 {
		return shim.Error("transaction has been rolledback")
	}

	if lockedContractInfo.TransactionID != transactionID {
		return shim.Error(path + "is unregistered in transaction " + transactionID)
	}

	if !isValidSeq(stub, transactionID, seq) {
		return shim.Error("seq should be greater than before")
	}

	timeStamp, err := stub.GetTxTimestamp()
	checkError(err)

	// recode transactionStep
	var transactionStep = TransactionStep{
		Seq:       seq,
		Path:      path,
		Timestamp: int64ToString(timeStamp.Seconds),
		Func:      method,
		Args:      thisArgs,
	}
	transactionInfo.Seqs = append(transactionInfo.Seqs, seq)
	transactionInfo.TransactionSteps = append(transactionInfo.TransactionSteps, transactionStep)

	// recode transactionInfo
	ti, err := json.Marshal(&transactionInfo)
	checkError(err)
	err = stub.PutState(getTransactionInfoKey(transactionID), ti)
	checkError(err)

	err = stub.PutState(uniqueID, []byte("true"))
	checkError(err)

	return callContract(stub, chaincodeName, method, thisArgs)
}

/*
 * @args transactionID || num || path1 || path2 || ...
 * the first num paths are related to current chain
 * result: 0-success
 */
func (p *ProxyChaincode) startTransaction(stub shim.ChaincodeStubInterface, args []string) peer.Response {
	argsLen := len(args)
	if argsLen < 4 {
		return shim.Error("invalid arguments")
	}

	num := stringToInt(args[1])
	if (num == 0) || (2*num+2) > argsLen {
		return shim.Error("invalid arguments")
	}

	transactionID := args[0]
	if isExistedTransaction(stub, transactionID) {
		return shim.Error("transaction " + transactionID + " already exists")
	}

	var contracts []string
	var paths []string
	for i := 0; i < num; i++ {
		paths = append(paths, args[i+2])
		chaincodeName := getNameFromPath(args[i+2])
		contracts = append(contracts, chaincodeName)
		var lockedContractInfo LockedContractInfo
		hasInfo := getLockedContractInfo(stub, chaincodeName, &lockedContractInfo)
		// contract conflict
		if hasInfo {
			return shim.Error(args[i+2] + " is locked by unfinished transaction: " + lockedContractInfo.TransactionID)
		}

		lockedContractInfo = LockedContractInfo{
			//			Path:    args[i+2],
			TransactionID: transactionID,
		}

		li, err := json.Marshal(&lockedContractInfo)
		checkError(err)
		err = stub.PutState(getLockContractKey(chaincodeName), li)
		checkError(err)
	}

	var allPaths []string
	for i := num + 2; i < argsLen; i++ {
		allPaths = append(allPaths, args[i])
	}

	timeStamp, err := stub.GetTxTimestamp()
	checkError(err)

	var transactionInfo = TransactionInfo{
		TransactionID:     transactionID,
		Contracts:         contracts,
		AllPaths:          allPaths,
		Paths:             paths,
		Status:            0,
		StartTimestamp:    int64ToString(timeStamp.Seconds),
		CommitTimestamp:   "0",
		RollbackTimestamp: "0",
		Seqs:              []uint{},
		TransactionSteps:  []TransactionStep{},
	}

	ti, err := json.Marshal(&transactionInfo)
	checkError(err)
	err = stub.PutState(getTransactionInfoKey(transactionID), ti)
	checkError(err)

	addTransaction(stub, transactionID)
	return shim.Success([]byte(SuccessFlag))
}

/*
 * result: 0-success
 */
func (p *ProxyChaincode) commitTransaction(stub shim.ChaincodeStubInterface, args []string) peer.Response {
	if len(args) != 1 {
		return shim.Error("invalid arguments")
	}

	transactionID := args[0]
	if !isExistedTransaction(stub, transactionID) {
		return shim.Error("transaction not found")
	}

	var transactionInfo TransactionInfo
	getTransactionInfo(stub, transactionID, &transactionInfo)

	// has committed
	if transactionInfo.Status == 1 {
		return shim.Success([]byte(SuccessFlag))
	}

	if transactionInfo.Status == 2 {
		return shim.Error("transaction has been rolledback")
	}

	timeStamp, err := stub.GetTxTimestamp()
	checkError(err)
	transactionInfo.Status = 1
	transactionInfo.CommitTimestamp = int64ToString(timeStamp.Seconds)
	ti, err := json.Marshal(&transactionInfo)
	checkError(err)
	err = stub.PutState(getTransactionInfoKey(transactionID), ti)
	checkError(err)

	deleteLockedContracts(stub, transactionID)
	addFinishedTransaction(stub, transactionID)

	return shim.Success([]byte(SuccessFlag))
}

/*
 * result: 0-success
 */
func (p *ProxyChaincode) rollbackTransaction(stub shim.ChaincodeStubInterface, args []string) peer.Response {
	if len(args) != 1 {
		return shim.Error("invalid arguments")
	}

	transactionID := args[0]
	if !isExistedTransaction(stub, transactionID) {
		return shim.Error("transaction not found")
	}

	var transactionInfo TransactionInfo
	getTransactionInfo(stub, transactionID, &transactionInfo)

	if transactionInfo.Status == 1 {
		return shim.Error("transaction has been committed")
	}

	// has rolledback
	if transactionInfo.Status == 2 {
		return shim.Success([]byte(SuccessFlag))
	}

	for i := len(transactionInfo.TransactionSteps) - 1; i >= 0; i-- {
		transactionStep := transactionInfo.TransactionSteps[i]
		newMethod := getRevertFunc(transactionStep.Func)
		chaincodeName := getNameFromPath(transactionStep.Path)

		// call revert function
		response := callContract(stub, chaincodeName, newMethod, transactionStep.Args)
		if response.Status != shim.OK {
			panic(response.GetMessage())
		}
	}

	timeStamp, err := stub.GetTxTimestamp()
	checkError(err)
	transactionInfo.Status = 2
	transactionInfo.RollbackTimestamp = int64ToString(timeStamp.Seconds)
	ti, err := json.Marshal(&transactionInfo)
	checkError(err)
	err = stub.PutState(getTransactionInfoKey(transactionID), ti)
	checkError(err)

	deleteLockedContracts(stub, transactionID)
	addFinishedTransaction(stub, transactionID)

	return shim.Success([]byte(SuccessFlag))
}

// return json string
func (p *ProxyChaincode) getTransactionInfo(stub shim.ChaincodeStubInterface, args []string) peer.Response {
	if len(args) != 1 {
		return shim.Error("invalid arguments")
	}

	transactionID := args[0]
	if !isExistedTransaction(stub, transactionID) {
		return shim.Error("transaction not found")
	}

	info, err := stub.GetState(getTransactionInfoKey(transactionID))
	checkError(err)

	return shim.Success(info)
}

// return all transaction ids
func (p *ProxyChaincode) getAllTransactionIDs(stub shim.ChaincodeStubInterface) peer.Response {
	taskLen, err := stub.GetState(TaskLenKey)
	checkError(err)

	length := bytesToUint64(taskLen)
	if length == 0 {
		return shim.Success([]byte(""))
	}

	var i uint64
	res := ""
	for i = 1; i <= length; i++ {
		id, err := stub.GetState(getTransactionTaskKey(i))
		checkError(err)
		res += string(id) + " "
	}

	return shim.Success([]byte(res))
}

func (p *ProxyChaincode) getFinishedTransactionIDs(stub shim.ChaincodeStubInterface) peer.Response {
	idBytes, err := stub.GetState(FinishedTasksKey)
	checkError(err)

	if idBytes == nil {
		return shim.Success([]byte(""))
	}

	var fids []string
	err = json.Unmarshal(idBytes, &fids)
	res := ""
	for _, id := range fids {
		res += id + " "
	}

	return shim.Success([]byte(res))
}

// called by router to check transaction status
func (p *ProxyChaincode) getLatestTransactionInfo(stub shim.ChaincodeStubInterface) peer.Response {
	transactionID := getLatestTransaction(stub)

	if transactionID == NullFlag {
		return shim.Success([]byte(NullFlag))
	}

	return p.getTransactionInfo(stub, []string{transactionID})
}

func (p *ProxyChaincode) rollbackAndDeleteTransaction(stub shim.ChaincodeStubInterface, args []string) peer.Response {
	if len(args) != 1 {
		return shim.Error("invalid arguments")
	}

	res := p.rollbackTransaction(stub, args)
	if res.Status == shim.ERROR {
		return res
	}

	return deleteLatestTransaction(stub, args[1])
}

func (p *ProxyChaincode) getTransactionState(stub shim.ChaincodeStubInterface, args []string) peer.Response {
	if len(args) != 1 {
		return shim.Error("invalid arguments")
	}

	path := args[0]
	chaincodeName := getNameFromPath(path)

	var lockedContractInfo LockedContractInfo
	hasInfo := getLockedContractInfo(stub, chaincodeName, &lockedContractInfo)

	if !hasInfo {
		return shim.Success([]byte(NullFlag))
	} else {
		seq := getCurrentSeq(stub, lockedContractInfo.TransactionID)
		return shim.Success([]byte(lockedContractInfo.TransactionID + " " + strconv.FormatUint(uint64(seq), 10)))
	}
}

func callContract(stub shim.ChaincodeStubInterface, contract, method, jsonArgs string) peer.Response {
	// parse args from json str
	var argsTemplate ArgsJsonTemplate
	err := json.Unmarshal([]byte(jsonArgs), &argsTemplate)
	checkError(err)

	var trans [][]byte
	trans = append(trans, []byte(method))
	for _, param := range argsTemplate.Args {
		trans = append(trans, []byte(param))
	}

	channel, err := stub.GetState(ChannelKey)
	checkError(err)

	return stub.InvokeChaincode(string(contract), trans, string(channel))
}

func addTransaction(stub shim.ChaincodeStubInterface, transactionID string) {
	l, err := stub.GetState(TaskLenKey)
	checkError(err)

	index := bytesToUint64(l) + 1
	err = stub.PutState(getTransactionTaskKey(index), []byte(transactionID))
	checkError(err)

	err = stub.PutState(TaskLenKey, uint64ToBytes(index))
	checkError(err)
}

func addFinishedTransaction(stub shim.ChaincodeStubInterface, transactionID string) {
	idBytes, err := stub.GetState(FinishedTasksKey)
	checkError(err)

	var fids []string
	if idBytes == nil {
		fids = []string{transactionID}
		idBytes, err = json.Marshal(&fids)
		checkError(err)
	} else {
		err = json.Unmarshal(idBytes, &fids)
		fids = append(fids, transactionID)
	}

	idBytes, err = json.Marshal(&fids)
	checkError(err)
	err = stub.PutState(FinishedTasksKey, idBytes)
	checkError(err)
}

func getLatestTransaction(stub shim.ChaincodeStubInterface) string {
	taskLen, err := stub.GetState(TaskLenKey)
	checkError(err)

	head, err := stub.GetState(TaskHeadKey)
	checkError(err)

	if bytesToUint64(head) > bytesToUint64(taskLen) {
		return NullFlag
	}

	id, err := stub.GetState(getTransactionTaskKey(bytesToUint64(head)))
	checkError(err)

	return string(id)
}

func deleteLatestTransaction(stub shim.ChaincodeStubInterface, transactionID string) peer.Response {
	taskLen, err := stub.GetState(TaskLenKey)
	checkError(err)

	head, err := stub.GetState(TaskHeadKey)
	checkError(err)

	if bytesToUint64(head) > bytesToUint64(taskLen) {
		return shim.Error("delete nonexistent transaction")
	}

	id, err := stub.GetState(getTransactionTaskKey(bytesToUint64(head)))
	checkError(err)

	if string(id) != transactionID {
		return shim.Error("delete unmatched transaction")
	}

	err = stub.PutState(TaskHeadKey, uint64ToBytes(bytesToUint64(head)+1))
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

func isExistedTransaction(stub shim.ChaincodeStubInterface, transactionID string) bool {
	t, err := stub.GetState(getTransactionInfoKey(transactionID))
	checkError(err)

	return t != nil
}

func isValidSeq(stub shim.ChaincodeStubInterface, transactionID string, seq uint) bool {
	var transactionInfo TransactionInfo
	getTransactionInfo(stub, transactionID, &transactionInfo)
	index := len(transactionInfo.Seqs)
	return (index == 0) || (seq > transactionInfo.Seqs[index-1])
}

func getCurrentSeq(stub shim.ChaincodeStubInterface, transactionID string) uint {
	var transactionInfo TransactionInfo
	getTransactionInfo(stub, transactionID, &transactionInfo)
	index := len(transactionInfo.Seqs)
	if index == 0 {
		return 0
	} else {
		return transactionInfo.Seqs[index-1]
	}
}

func getTransactionInfo(stub shim.ChaincodeStubInterface, transactionID string, ti *TransactionInfo) {
	t, err := stub.GetState(getTransactionInfoKey(transactionID))
	checkError(err)

	err = json.Unmarshal(t, ti)
	checkError(err)
}

func getLockedContractInfo(stub shim.ChaincodeStubInterface, contract string, ci *LockedContractInfo) bool {
	l, err := stub.GetState(getLockContractKey(contract))
	checkError(err)

	if l == nil {
		return false
	} else {
		err = json.Unmarshal(l, ci)
		checkError(err)
		return true
	}
}

func deleteLockedContracts(stub shim.ChaincodeStubInterface, transactionID string) {
	var transactionInfo TransactionInfo
	getTransactionInfo(stub, transactionID, &transactionInfo)

	for _, contract := range transactionInfo.Contracts {
		err := stub.DelState(getLockContractKey(contract))
		checkError(err)
	}
}

func getLockContractKey(contract string) string {
	return fmt.Sprintf(LockContractKey, contract)
}

func getTransactionInfoKey(transactionID string) string {
	return fmt.Sprintf(TransactionInfoKey, transactionID)
}

func getTransactionTaskKey(index uint64) string {
	return fmt.Sprintf(TransactionTaskKey, index)
}

func int64ToString(num int64) string {
	return strconv.FormatInt(num, 10)
}

func stringToUint(str string) uint {
	i, e := strconv.Atoi(str)
	if e != nil {
		return 0
	}
	return uint(i)
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
	err := shim.Start(new(ProxyChaincode))
	if err != nil {
		fmt.Printf("Error: %s", err)
	}
}
