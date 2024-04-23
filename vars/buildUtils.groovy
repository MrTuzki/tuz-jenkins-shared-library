/**
* 终止当前 build
* @param reason: 终止原因
*/
def abort(String reason='') {
    currentBuild.result = 'ABORTED'
    def message
    if (reason) {
        message = "Aborting the build: ${reason}"
    } else {
        message = "Aborting the build!"
    }
    error(message)
}

/**
* 获取每个 stage 的运行结果, result 可能的返回值: SUCCESS, NOT_BUILT(等同于SKIP), FAILURE, UNKNOWN(还未执行完成)
*/
@NonCPS
def Map<String, String> getStageResults() {
    def visitor = new PipelineNodeGraphVisitor(currentBuild.rawBuild)

    def results = visitor.pipelineNodes.findAll {
        it.type == FlowNodeWrapper.NodeType.STAGE
    }.collectEntries {
        [it.displayName, "${it.status.result}"]
    }
    return results
}

def isStageSucceed(String stageName) {
    return getStageResults().get(stageName) == 'SUCCESS'
}

def isStageFailed(String stageName) {
    return getStageResults().get(stageName) == 'FAILURE'
}