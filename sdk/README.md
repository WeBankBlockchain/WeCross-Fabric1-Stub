# Fabric 跨链调用 SDK

在chaincode中发起跨链调用，目前提供了：

* Go语言SDK：[cross_fabric_sdk.go](./cross_fabric_sdk.go)

### 引用

将`cross_fabric_sdk.go` 与链码放置于相同目录下。

## 使用

提供三个函数

* CrossCall：发送跨链查询请求，每个调用自动生成一个nonce，返回时用此nonce调用回调函数
* CrossSendTransaction：发送跨链交易，每个调用自动生成一个nonce，返回时用此nonce调用回调函数
* ParseCallbackArgs：处理交易回调，得到nonce和回调参数

### 发送跨链查询请求

使用`CrossCall`函数发送跨链查询请求。该操作不会修改对方区块链，得到结果后调用回调函数返回。

**定义**

``` go
func CrossCall(
    stub shim.ChaincodeStubInterface,  	// fabric的shim接口
    path string, 						// 目的资源的path
    method string, 						// 目的资源的方法名
    args []string, 						// 调用参数
    identity string, 					// 一级账户地址，在调用时会校验发送交易的二级账户是否属于此一级账户
    callbackMethod string				// 回调函数名，在代码中需实现相应的回调函数
) (uint64, error)
```

**示例**

完整示例请参考：[cross_hello.go](./cross_hello.go)

调用

``` go
nonce, err := CrossCall(
    stub, 
    "payment.bcos.HelloWorld", 
    "get", 
    []string{}, 
    "0x222962196394e2e5ecc3fac11ab99b7446393ca1", 
    "getCallback")
```

回调参数处理

在`Invoke`函数中，使用`ParseCallbackArgs`函数处理回调，得到nonce和回调参数

``` go
fn, args := stub.GetFunctionAndParameters() // fn 即为回调函数名
nonce, callbackArgs, err = ParseCallbackArgs(args)
if err == nil {
    p.getCallback(stub, nonce, callbackArgs[0])
}
```

回调函数实现

``` go
func (p CrossHelloWorld) getCallback(stub shim.ChaincodeStubInterface, nonce uint64, ret0 string) {
	p.set(stub, ret0)
}
```

### 发送跨链交易

使用`CrossSendTransaction`函数发送跨链交易。该操作会向目的区块链发送一笔交易，交易上链后将返回值调用回调函数返回。

**定义**

``` go
func CrossSendTransaction(
    stub shim.ChaincodeStubInterface,  	// fabric的shim接口
    path string, 						// 目的资源的path
    method string, 						// 目的资源的方法名
    args []string, 						// 调用参数
    identity string, 					// 一级账户地址，在调用时会校验发送交易的二级账户是否属于此一级账户
    callbackMethod string				// 回调函数名，在代码中需实现相应的回调函数
) (uint64, error)
```

**示例**

完整示例请参考：[cross_hello.go](./cross_hello.go)

调用

``` go
nonce, err := CrossSendTransaction(
    stub, 
    "payment.bcos.HelloWorld", 
    "set", 
    []string{args[0]}, 
    "0x222962196394e2e5ecc3fac11ab99b7446393ca1", 
    "setCallback")
```

回调参数处理

在`Invoke`函数中，使用`ParseCallbackArgs`函数处理回调，得到nonce

``` go
fn, args := stub.GetFunctionAndParameters() // fn 即为回调函数名
nonce, _, err = ParseCallbackArgs(args)
if err == nil {
    p.setCallback(stub, nonce)
}
```

回调函数实现

``` go
func (p CrossHelloWorld) setCallback(stub shim.ChaincodeStubInterface, nonce uint64) {
	p.set(stub, "callback called")
	p.testCall(stub)
}
```

## 原理

cross_fabric_sdk.go 提供的函数被调用后会抛出以下事件，事件被插件捕获，将相应参数解析后采用与链下SDK相同的方式调用至目的链。目的链执行后，返回值以回调函数的形式原路返回，最终调用回当前合约。