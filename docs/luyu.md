# 陆羽协议配置及测试

## 配置

### 配置插件

将fabric1-stub-xxxx.jar放置于陆羽协议路由的plugin目录下

### 配置接入链

给要接入的链取名字：如 fabric1

在陆羽协议的路由配置目录`chains/<chainName>`下，文件夹名即为链名（如链名为fabric1的配置文件目录为：`chains/fabric1`）

包含以下文件：

``` 
fabric1/
├── accounts
│   └── fabric_admin # 向链上发送交易的账户，当前版本下只能指定一个，在driver.toml中配置生效
│       ├── account.crt 
│       ├── account.key
│       └── account.toml
├── connection.toml
├── driver.toml
├── orderer-tlsca.crt
├── org1-tlsca.crt
├── org2-tlsca.crt
└── plugin.toml
```

**plugin.toml**

```
[common]
    name = 'fabric1'
    type = 'Fabric1.4'
```

**driver.toml**

```
[users]
    adminName = "fabric_admin" # 指定发送交易的账户，与accounts目录下的目录名对应
```

**connection.toml**

```
[common]
    name = 'fabric1'
    type = 'Fabric1.4'

[fabricServices]
    channelName = 'mychannel'
    orgUserName = 'fabric_admin'
    ordererTlsCaFile = 'orderer-tlsca.crt'
    ordererAddress = 'grpcs://localhost:7050'

[orgs]
    [orgs.Org1]
        tlsCaFile = 'org1-tlsca.crt'
        endorsers = ['grpcs://localhost:7051']

    [orgs.Org2]
        tlsCaFile = 'org2-tlsca.crt'
        endorsers = ['grpcs://localhost:9051']
```

**accounts.toml**

``` toml
[account]
    type = 'Fabric1.4'
    mspid = 'Org1MSP'
    keystore = 'account.key'
    signcert = 'account.crt'
```

**证书拷贝位置**

例如`fabric-samples-1.4.4/first-network/crypto-config`下

| 文件              | 说明                       | 位置                                                         |
| ----------------- | -------------------------- | ------------------------------------------------------------ |
| account.crt       | 账户证书                   | `peerOrganizations/org1.example.com/users/Admin@org1.example.com/msp/signcerts/Admin@org1.example.com-cert.pem` |
| account.key       | 账户私钥                   | `peerOrganizations/org1.example.com/users/Admin@org1.example.com/msp/keystore/*_sk` |
| orderer-tlsca.crt | 连接排序节点的根证书       | `ordererOrganizations/example.com/orderers/orderer.example.com/msp/tlscacerts/tlsca.example.com-cert.pem` |
| org1-tlsca.crt    | 连接org1的背书节点的根证书 | `peerOrganizations/org1.example.com/peers/peer0.org1.example.com/tls/ca.crt` |
| org2-tlsca.crt    | 连接org2的背书节点的根证书 | `peerOrganizations/org2.example.com/peers/peer0.org2.example.com/tls/ca.crt` |

## 调试方法

启动路由后

**查询资源**

`POST`

`http://localhost:8250/sys/listResources`

``` json
{
	"version":"1",
	"data":{
		"ignoreRemote": true
	}
}
```

**发送交易**

`POST`

`localhost:8250/resource/<zoneName>/<chainName>/<resourceName>/sendTransaction`

``` json
{
        "version":"1.0.0",
        "data":{
                "path": "payment.fabric.mycc",
                "method": "invoke",
                "args": ["a", "b", "1"],
                "nonce":123456,
                "luyuSign": []
        }
}
```

正确返回

``` json
{
    "version": "1.0.0",
    "errorCode": 0,
    "message": "Success",
    "data": {
        "result": [
            ""
        ],
        "code": 0,
        "message": "Success",
        "path": "payment.fabric.mycc",
        "method": "invoke",
        "args": [
            "a",
            "b",
            "1"
        ],
        "transactionHash": "534426ea5f3db65a143a1c36a30bbeff3488ecc7f978f8df3cfea1b533889957",
        "transactionBytes": "",
        "blockNumber": 12,
        "version": null
    }
}
```

**查询状态**

`POST`

`localhost:8250/resource/<zoneName>/<chainName>/<resourceName>/call`

``` json
{
        "version":"1",
        "data":{
                "path": "payment.fabric.mycc",
                "method": "query",
                "args": ["a"],
                "nonce":123456,
                "luyuSign":""
        }
}
```

正确返回

``` json
{
    "version": "1.0.0",
    "errorCode": 0,
    "message": "Success",
    "data": {
        "result": [
            "82"
        ],
        "code": 0,
        "message": "",
        "path": "payment.fabric.mycc",
        "method": "query",
        "args": [
            "a"
        ],
        "version": null
    }
}
```

