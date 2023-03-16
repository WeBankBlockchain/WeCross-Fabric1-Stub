### v1.3.0

(2023-03-15)

**更新**

* log4j版本升级至2.19
* WeCross stub 版本号更新到1.3.0
  *去除netty和tcnative的依赖

### v1.2.1

(2021-12-15)

**修复**

* 修复log4j的漏洞，将其升级至2.15

### v1.2.0

(2021-08-20)

**更改**

* 升级版本号，与现有WeCross版本保持一致
* 完善 README 

### v1.1.1

(2021-04-02)

**更改**

* 优化区块头验证代码结构

### v1.1.0

(2021-02-02）

**新增**

* 支持区块校验

### v1.0.0

(2020-12-17)

**新增**

* 桥接合约：管理合约跨链调用请求
* Driver接口：getTransaction，获取交易详情

**更改**

* 删除账户加载逻辑
* 代理合约更改：
    * 发交易新增交易号UUID用于去重
    * 不允许call事务中的资源
    * 优化部分接口命名与定义

### v1.0.0-rc4

(2020-08-05)

**更新**

* 代理合约
  * WeCrossProxy代码及部署操作
  * 将代理合约作为调用入口调用其它合约
  * 通过代理合约控制其它合约的访问
* 链码管理
  * 新增API：install、instantiate、upgrade
  * 拉取正在运行的链码

### v1.0.0-rc3

(2020-06-15)

**更新**

* 操作区块链时，调用区块链的异步接口
* 适配异步Driver接口，向Router提供异步的call/sendTrnasaction

### v1.0.0-rc2

(2020-05-12)

**功能**
* 区块链适配
  * Hyperledger Fabric Stub配置加载
  * Hyperledger Fabric链状态数据查询
  * Hyperledger Fabric链上资源访问
  * Hyperledger Fabric交易解析
* 账户功能
  * Hyperledger Fabric账户加载
  * 交易签名
