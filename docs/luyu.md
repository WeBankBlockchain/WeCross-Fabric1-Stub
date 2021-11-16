# 陆羽跨链协议配置

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

由于Fabric的二级账户无法自动生成，需手动将其配置到相关一级账户下（后续支持更方便的方式）

在配置了本插件的路由下执行命令

``` bash
java  -cp conf/:lib/*:plugin/* link.luyu.protocol.link.fabric1.tools.AddAlgAccountRequestPacketBuilder
```

会看到help输出，参数含义如下

* 参数1：sender，指定一级账户地址，即：用sdk的gen_account.sh生成的账户地址（0x开头的一串16进制字符串）
* 参数2：chain path，指定fabric链的链path，如：payment.fabric1
* 参数3：account name，指定conf/account下的二级账户所在目录名，如：fabric_admin

如：

``` bash
java -cp conf/:lib/*:plugin/* link.luyu.protocol.link.fabric1.tools.AddAlgAccountRequestPacketBuilder 0xaaabbcc payment.fabric fabric_admin
```

得到需要请求的json

``` json
{
  "data" : {
    "luyuSign" : "",
    "type" : "ECDSA_SECP256R1_WITH_SHA256",
    "nonce" : 1636899264619,
    "identity" : "0xaaabbcc",
    "pubKey" : "xxxxxxxxx",
    "secKey" : "FJ6iv1aOXi+0cJFSMqBy5h7CbB3DUHdbGnDiYPoZ0zM=",
    "properties" : {
      "Fabric1.4:payment.fabric:name" : "fabric_admin",
      "Fabric1.4:payment.fabric:cert" : "xxxxxxxx",
      "Fabric1.4:payment.fabric:mspid" : "Org1MSP"
    },
    "isDefault" : true
  }
}
```

将账户服务的RPC接口采用非SSL的模式，**并重启账户服务**

``` bash
vim account-manager/conf/application.toml # sslOn 设置为 false
```

调用账户服务的RPC接口，发送json

* Method：`POST`

* URL：http://x.x.x.x:8340/auth/addAlgAccount
* Body：上述生成的json字段

成功后data.errorCode中返回0

``` json
{
    "version": "1.0",
    "errorCode": 0,
    "message": "success",
    "data": {
        "errorCode": 0,
        "message": "success"
    }
}
```

将账户服务的RPC接口改回SSL模式，以便能和路由交互

``` bash
vim account-manager/conf/application.toml # sslOn 设置为 true
```

重启账户服务即可

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
