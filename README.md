# tuz-jenkins-shared-library

## 使用方法

-   Jenkins Shared Library 介绍，请参考[官方文档](https://www.jenkins.io/doc/book/pipeline/shared-libraries/)

-   进入 Jenkins，点击 Manage Jenkins > Configure System > Global Pipeline Libraries，添加本仓库
-   在 Jenkinsfile 中引用本仓库提供的方法，Ex：

```groovy
def result = shUtils.execCmd('ls -al')
logger.info("result=${result}")
```

