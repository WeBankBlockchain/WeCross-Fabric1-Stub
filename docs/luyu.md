# 陆羽协议配置及测试

## 配置

### 配置插件

将fabric1-stub-xxxx.jar放置于陆羽协议路由的plugin目录下

### 配置账户

在陆羽协议的路由配置目录`accounts`下，配置Fabric的账户

包含以下文件：

```
accounts/
└──fabric_admin
|   ├── account.crt
|   ├── account.key
|   └── account.toml
├── fabric_admin_org1
|   ├── account.crt
|   ├── account.key
|   └── account.toml
└── fabric_admin_org2
    ├── account.crt
    ├── account.key
    └── account.toml
```

### 配置接入链

给要接入的链取名字：如 fabric1

在陆羽协议的路由配置目录`chains/<chainName>`下，文件夹名即为链名（如链名为fabric1的配置文件目录为：`chains/fabric1`）

包含以下文件：

``` 
fabric1/
├── connection.toml
├── driver.toml
├── orderer-tlsca.crt
├── org1-tlsca.crt
├── org2-tlsca.crt
└── plugin.toml
```

**plugin.toml**

```toml
[common]
    name = 'fabric1'
    type = 'Fabric1.4'
```

**driver.toml**

```
（空文件）
```

**connection.toml**

```toml
[fabricServices]
    channelName = 'mychannel'
    orgUserName = 'fabric_admin'
    ordererTlsCaFile = 'orderer-tlsca.crt'
    ordererAddress = 'grpcs://localhost:7050'

[orgs]
    [orgs.Org1]
        tlsCaFile = 'org1-tlsca.crt'
        adminName = 'fabric_admin_org1' # 配置方式与fabric_admin相同
        endorsers = ['grpcs://localhost:7051']

    [orgs.Org2]
        tlsCaFile = 'org2-tlsca.crt'
        adminName = 'fabric_admin_org2' # 配置方式与fabric_admin相同，但account.toml 中的mspid为Org2MSP
        endorsers = ['grpcs://localhost:9051']
```

**account.toml**

`fabric_admin` 和 `fabric_admin_org1` 目录下的为：

``` toml
[account]
    type = 'Fabric1.4'
    mspid = 'Org1MSP' 
    keystore = 'account.key'
    signcert = 'account.crt'
```

`fabric_admin_org2` 目录下的为：

``` toml
[account]
    type = 'Fabric1.4'
    mspid = 'Org2MSP'  # 此处不同
    keystore = 'account.key'
    signcert = 'account.crt'
```

**证书拷贝位置**

例如`fabric-samples-1.4.4/first-network/crypto-config`下：

| 文件                                                         | 说明                       | 位置                                                         |
| ------------------------------------------------------------ | -------------------------- | ------------------------------------------------------------ |
| orderer-tlsca.crt                                            | 连接排序节点的根证书       | `ordererOrganizations/example.com/orderers/orderer.example.com/msp/tlscacerts/tlsca.example.com-cert.pem` |
| org1-tlsca.crt                                               | 连接org1的背书节点的根证书 | `peerOrganizations/org1.example.com/peers/peer0.org1.example.com/tls/ca.crt` |
| org2-tlsca.crt                                               | 连接org2的背书节点的根证书 | `peerOrganizations/org2.example.com/peers/peer0.org2.example.com/tls/ca.crt` |
| fabric_admin/account.crt、<br>fabric_admin_org1/account.crt  | 账户证书                   | `peerOrganizations/org1.example.com/users/Admin@org1.example.com/msp/signcerts/Admin@org1.example.com-cert.pem` |
| fabric_admin/account.key、<br/>fabric_admin_org1/account.key | 账户私钥                   | `peerOrganizations/org1.example.com/users/Admin@org1.example.com/msp/keystore/*_sk` |
| fabric_admin_org2/account.crt                                | 账户证书                   | `peerOrganizations/org2.example.com/users/Admin@org2.example.com/msp/signcerts/Admin@org2.example.com-cert.pem` |
| fabric_admin_org2/account.key                                | 账户私钥                   | `peerOrganizations/org2.example.com/users/Admin@org2.example.com/msp/keystore/*_sk` |

**重启路由**

配置完成后，重启路由使其生效

``` bash
bash stop.sh && bash start.sh
```

**配置二级账户**

由于Fabric的二级账户无法自动生成，需通过网页管理台将其配置到相关一级账户下

``` http
http://localhost:9250/s/index.html#/account/index
```

点击添加二级账户，填入相关信息，其中

* 算法类型：Hyperledger Fabric 1.4
* 私钥文件：account.key
* 公钥证书：account.crt
* 目的链Path：需操作的区块链的path，如`payment.fabric`
* MSPID：account.toml中的mspid对应的内容
* 设为默认账户：选上，表示向`payment.fabric`链发送交易时默认采用用此二级账户

点击确认即可

### 配置跨链验证

该配置用于在非直连区块链的路由上，通过跨链验证机制验证直连路由返回消息的正确性。如：路由A直连了Fabric链，则在路由B上进行该配置。配置后，路由B会采用跨链验证机制，校验发往路由A的交易上链结果的正确性。

注：该配置是可选配置，若不配置则不开启

**配置目录**

对方接入的链名字：如 fabric1

在陆羽协议的路由配置目录`chains/<chainName>`下，文件夹名即为链名（如链名为fabric1的配置文件目录为：`chains/fabric1`）

包含以下文件：

``` 
fabric1/
├── driver.toml
├── plugin.toml
└── verifier # 存放验证证书的目录
```

**plugin.toml**

```toml
[common]
    name = 'fabric1'
    type = 'Fabric1.4'
```

**driver.toml**

```toml
[verifier]
     [verifier.endorserCA] # 机构的CA列表
            Org1MSP = 'verifier/org1CA/ca.org1.example.com-cert.pem' # 相对路径：验证证书所在位置的
            Org2MSP = 'verifier/org2CA/ca.org2.example.com-cert.pem'
     [verifier.ordererCA] # 排序节点的CA证书
            OrdererMSP = 'verifier/ordererCA/ca.example.com-cert.pem'
```

相应的存放验证证书的目录结构如下

``` 
verifier/
├── ordererCA
│   └── ca.example.com-cert.pem
├── org1CA
│   └── ca.org1.example.com-cert.pem
└── org2CA
    └── ca.org2.example.com-cert.pem
```


**证书拷贝位置**

例如`fabric-samples-1.4.4/first-network/crypto-config`下：

| 文件                                | 说明             | 位置                                                         |
| ----------------------------------- | ---------------- | ------------------------------------------------------------ |
| ordererCA/ca.example.com-cert.pem   | 排序节点的CA证书 | `ordererOrganizations/example.com/ca/ca.example.com-cert.pem` |
| org1CA/ca.org1.example.com-cert.pem | 机构org1的CA证书 | `peerOrganizations/org1.example.com/ca/ca.org1.example.com-cert.pem` |
| org2CA/ca.org2.example.com-cert.pem | 机构org2的CA证书 | `peerOrganizations/org2.example.com/ca/ca.org2.example.com-cert.pem` |

**重启路由**

配置完成后，重启路由使其生效

``` bash
bash stop.sh && bash start.sh
```

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

