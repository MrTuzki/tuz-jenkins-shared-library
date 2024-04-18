package com.tuz

class XCI {
    def script
    def xstages = []
    def err = null

    XCI(script) {
        this.script = script
    }

    def xstage(String stageName, Map stageConfigMap = [:], Closure c) {
        script.logger.debug("stageName=${stageName}, stageConfigMap=${stageConfigMap}")
        def group = stageConfigMap.get('group', 'default')
        def enabled = stageConfigMap.get('enabled', true)
        def parallel = stageConfigMap.get('parallel', false)
        def ignoreFailure = stageConfigMap.get('ignoreFailure', false)
        def timeout = stageConfigMap.get('timeout', 0)
        def retries = stageConfigMap.get('retries', 0)

        xstages.add([
            name: stageName,
            body: c,
            group: group,
            enabled: enabled,
            parallel: parallel,
            ignoreFailure: ignoreFailure,
            timeout: timeout,
            retries: retries
        ])
    }

    def shouldStageRun(Map stage) {
        def enabled = stage.enabled
        if (enabled instanceof Closure) {
            enabled = enabled.call()
        }

        if (!enabled) {
            return false
        }
        if (stage.group != 'teardown' && err) {
            return false
        }
        return true
    }

    def wrapStageBody(Map stage, Closure body = null) {
        if (!body) {
            body = stage.body
        }
        body = retriesWrapper(stage.name, stage.retries, body)
        body = timeoutWrapper(stage.timeout, body)
        return body
    }

    def timeoutWrapper(int t, Closure c) {
        if (!t) {
            return c
        }
        return {
            script.timeout(time: t, unit: 'SECONDS') {
                c.call()
            }
        }        
    }

    def retriesWrapper(String name, int retries, Closure c) {
        if (!retries) {
            return c
        }
        return {
            def err = null
            for(int i = 0; i < retries + 1; i++) {
                if (i != 0) {
                    script.logger.info("${name} 第 ${i} 次重试!")
                }
                try {
                    c.call()
                    return
                } catch(org.jenkinsci.plugins.workflow.steps.FlowInterruptedException e) {
                    // 响应 timeout 中断
                    throw e
                } catch(e) {
                    script.logger.error("${name} err => ${e}")
                    err = e
                }
            }
            if (err) {
                throw err
            }
        }
    }

    def runStage(String group, Map stage) {
        try {
            script.stage(stage.name) {
                script.when(shouldStageRun(stage)) {
                    if (group != 'teardown') {
                        wrapStageBody(stage).call()
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
                        def callMethod = stage.body.class.methods.find { it.name == 'call' }
                        def callMethodParameters = callMethod.parameters
                        if (!callMethodParameters.length) {
                            wrapStageBody(stage).call()
                        } else {
                            wrapStageBody(stage, { stage.body.call(always, success, failure) }).call()
                            tasks.each { it.call() }    
                        }
                        // 用完后, 立即设置为 null, 否则会报错 Caused: java.io.NotSerializableException: java.lang.reflect.Method
                        callMethod = null
                        callMethodParameters = []
                    }
                }
            }
        } catch (e) {
            script.logger.debug("catch err=>${e}")
            if (!stage.ignoreFailure) {
                script.currentBuild.result = 'FAILURE'
                err = e
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
}