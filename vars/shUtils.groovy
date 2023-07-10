/**
* 执行shell命令并返回执行结果
*/ 
def execCmd(cmd) {
    return sh(script: cmd, returnStdout:true).trim()
}