package com.tuz

import hudson.AbortException
import jenkins.model.CauseOfInterruption.UserInterruption
import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException

class XCI {
    def script
    def xstages = []
    def wrappers = []
    def err = null

    XCI(script) {
        this.script = script
        wrappers << new RetriesWrapper()
        wrappers << new TimeoutWrapper()
    }

    def xstage(String stageName, Map stageConfigMap = [:], Closure c) {
        script.logger.debug("stageName=${stageName}, stageConfigMap=${stageConfigMap}")
        def group = stageConfigMap.get('group', 'default')
        def enabled = stageConfigMap.get('enabled', true)
        def parallel = stageConfigMap.get('parallel', false)
        def ignoreFailure = stageConfigMap.get('ignoreFailure', false)
        def timeout = stageConfigMap.get('timeout', 0)
        def retries = stageConfigMap.get('retries', 0)
        def callback = stageConfigMap.get('callback', { })

        xstages.add([
            name: stageName,
            body: c,
            group: group,
            enabled: enabled,
            parallel: parallel,
            ignoreFailure: ignoreFailure,
            timeout: timeout,
            retries: retries,
            callback: callback,
            // 运行后设置
            startTime: 0,
            endTime: 0,
            result: '',
            reason: ''
        ])
    }

    def shouldStageRun(Map stage) {
        def enabled = stage.enabled
        if (enabled instanceof Closure) {
            enabled = enabled()
        }

        if (!enabled) {
            return false
        }
        if (stage.group != 'teardown' && err) {
            return false
        }
        return true
    }

    def wrapStageBody(Map stage, Closure body) {
        for (wrapper in wrappers) {
            body = wrapper.wrap(stage, body)
        }
        return body
    }

    def runStage(String group, Map stage) {
        try {
            def run = shouldStageRun(stage)
            if (!run) {
                stage.result = 'SKIP'
            }
            stage.startTime = new Date().getTime()

            script.stage(stage.name) {
                script.when(run) {
                    if (group != 'teardown') {
                        wrapStageBody(stage, stage.body).call()
                    } else {
                        def tasks = []
                        def always = { c -> tasks.add(c) }                        
                        def success = { c ->
                            if (!err) {
                                tasks.add(c)
                            }
                        }
                        def failure = { c ->
                            if (err) {
                                tasks.add(c)
                            }
                        }
                        def numberOfParameters = stage.body.maximumNumberOfParameters
                        def wrappedBody = stage.body
                        if (!numberOfParameters) {
                        } else if (numberOfParameters == 1) {
                            wrappedBody = wrapStageBody(stage, { stage.body.call(this) })
                        } else if (numberOfParameters == 3) {
                            wrappedBody = wrapStageBody(stage, { stage.body.call(always, success, failure) })
                        } else if (numberOfParameters == 4) {
                            wrappedBody = wrapStageBody(stage, { stage.body.call(this, always, success, failure) })
                        } else {
                            throw new AbortException('回调函数参数数量不一致!')
                        }
                        wrappedBody.call()
                        tasks.each { it.call() }    
                    }
                    stage.result = 'SUCCESS'
                }
            }
        } catch (e) {
            script.logger.debug("catch err=>${e}")
            stage.result = 'FAILURE'
            stage.reason = getExceptionMessage(e)
            if (!stage.ignoreFailure) {
                script.currentBuild.result = 'FAILURE'
                err = e
            }
        } finally {
            stage.endTime = new Date().getTime()
            try {
                stage.callback.call(stage)
            } catch(e) {
                // ignored
                script.logger.warn("[${stage.name}] callback exec failed: ${e}")
            }            
        }  
    }

    def runStages(String group, List stages) {
        script.logger.debug("group: [${group}] stages=${stages}")
        if (!stages) {
            return
        }
        // 1. 非 parallel 的 stage 先执行
        stages.findAll { !it.parallel }.each { runStage(group, it) }

        // 2. parallel 的 stage 并行执行
        def tasks = [:]
        stages.findAll { it.parallel }.each {
            tasks[it.name] = {
                runStage(group, it)
            }
        }
        if (tasks) {
            script.parallel(tasks)
        }        
    }

    @NonCPS
    def getSortedStages() {
        return xstages.toSorted{ a,b -> 
            if (a.startTime == 0) {
                return 1
            } else if (b.startTime == 0) {
                return -1
            } else {
                return a.startTime <=> b.startTime
            }
        }
    }

    def getSuccessStages() {
        return getSortedStages().findAll { it.result == 'SUCCESS' }
    }

    def getFailedStage(boolean includeIgnoreFailures = false) {
        return getFailedStages(includeIgnoreFailures).find { -> true }
    }

    def getFailedStages(boolean includeIgnoreFailures = true) {
        return getSortedStages()
            .findAll { it.result == 'FAILURE' }
            .findAll { includeIgnoreFailures ? true: it.ignoreFailure == false }
    }

    def getSkippedStages() {
        return getSortedStages().findAll { it.result == 'SKIP' }
    }

    def isSuccess() {
        return getFailedStage() == null
    }

    def getFailedReason() {
        return getFailedStage()?.reason
    }

    def getExceptionMessage(Exception e) {
        if (e instanceof FlowInterruptedException) {
            CauseOfInterruption coi = ((FlowInterruptedException) e).getCauses()[0]
            return coi.getShortDescription()
        } else {
            return e.message
        }
    }

    def run() {
        // 1. 根据 group 进行分组
        def xstagesMap = [:]
        xstages.each {
            if (!xstagesMap.containsKey(it.group)) {
                xstagesMap[it.group] = []
            }
            xstagesMap[it.group].add(it)
        }
        script.logger.debug("xstagesMap=${xstagesMap}")

        // 2. 过滤出 setup 和 teardown stage
        def setupStages = xstagesMap.remove('setup')
        def teardownStages = xstagesMap.remove('teardown')   

        // 3. setup 分组的 stage 先执行
        runStages('setup', setupStages)

        // 4. 其它 stage 按加入顺序依次执行
        xstagesMap.each { group, stages -> 
            runStages(group, stages)
        }
        
        // 5. teardown stage 最后执行
        runStages('teardown', teardownStages)  

        // 6. 重新抛出异常
        if (err) {
            throw err
        } 
    }

    interface StageBodyWrapper extends Serializable {
        @NonCPS
        Closure wrap(Map stage, Closure body)
    }

    class TimeoutWrapper implements StageBodyWrapper, Serializable {
        Closure wrap(Map stage, Closure body) {
            def timeout = stage.timeout
            if (!timeout) {
                return body
            }
            return {
                script.timeout(time: timeout, unit: 'SECONDS') {
                    body.call()
                }                
            }
        }
    }

    class RetriesWrapper implements StageBodyWrapper, Serializable {
        Closure wrap(Map stage, Closure body) {
            def retries = stage.retries
            if (!retries) {
                return body
            }
            return {
                def err = null
                for(int i = 0; i < retries + 1; i++) {
                    if (i != 0) {
                        script.logger.info("${stage.name} 第 ${i} 次重试!")
                    }
                    try {
                        body.call()
                        return
                    } catch(FlowInterruptedException e) {
                        // 响应 interrupt 中断
                        throw e
                    } catch(e) {
                        script.logger.error("${stage.name} err => ${e}")
                        err = e
                    }
                }
                if (err) {
                    throw err
                }
            }           
        }
    }
}