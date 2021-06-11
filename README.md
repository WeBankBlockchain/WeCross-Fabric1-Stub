![](./docs/images/menu_logo_wecross.png)

# WeCross-Fabric1-Stub

[![CodeFactor](https://www.codefactor.io/repository/github/webankblockchain/WeCross-Fabric1-Stub/badge)](https://www.codefactor.io/repository/github/webankblockchain/WeCross-Fabric1-Stub) [![Build Status](https://travis-ci.org/WeBankBlockchain/WeCross-Fabric1-Stub.svg?branch=dev)](https://travis-ci.org/WeBankBlockchain/WeCross-Fabric1-Stub) [![Latest release](https://img.shields.io/github/release/WeBankBlockchain/WeCross-Fabric1-Stub.svg)](https://github.com/WeBankBlockchain/WeCross-Fabric1-Stub/releases/latest)
[![License](https://img.shields.io/github/license/WeBankBlockchain/WeCross-Fabric1-Stub)](https://www.apache.org/licenses/LICENSE-2.0) [![Language](https://img.shields.io/badge/Language-Java-blue.svg)](https://www.java.com)

WeCross Fabric1 Stub是[WeCross](https://github.com/WeBankBlockchain/WeCross)用于适配[Hyperledger Fabric 1.4](https://github.com/hyperledger/fabric/tree/release-1.4)及以上版本的插件。

## 关键特性

- Hyperledger Fabric配置加载
- Hyperledger Fabric账户生成
- Hyperledger Fabric链上资源访问
- Hyperledger Fabric交易签名与解析

## 编译插件

**环境要求**:

  - [JDK8及以上](https://www.oracle.com/java/technologies/javase-downloads.html)
  - Gradle 5.0及以上

**编译命令**:

```shell
git clone https://github.com/WeBankBlockchain/WeCross-Fabric1-Stub.git
cd WeCross-Fabric1-Stub
./gradlew assemble
```

如果编译成功，将在当前目录的dist/apps目录下生成插件jar包。

## 插件使用

插件的详细使用方式请参阅[WeCross技术文档](https://wecross.readthedocs.io/zh_CN/latest/docs/stubs/fabric.html#id1)

陆羽协议使用方式请参阅[陆羽协议配置及测试](docs/luyu.md)

## 贡献说明

欢迎参与WeCross社区的维护和建设：

- 提交代码(Pull requests)，可参考[代码贡献流程](CONTRIBUTING.md)以及[wiki指南](https://github.com/WeBankBlockchain/WeCross/wiki/%E8%B4%A1%E7%8C%AE%E4%BB%A3%E7%A0%81)
- [提问和提交BUG](https://github.com/WeBankBlockchain/WeCross-Fabric1-Stub/issues/new)

希望在您的参与下，WeCross会越来越好！

## 社区
联系我们：wecross@webank.com

## License

WeCross Fabric1 Stub的开源协议为Apache License 2.0，详情参考[LICENSE](./LICENSE)。
