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
