import com.tuz.XCI

def call(String nodeLabel = '', Map configMap = [:], Closure body) {
    logger.debug("nodeLabel=${nodeLabel}, configMap=${configMap}")
    def callback = configMap.callback
    def xci = new XCI(this)
    def xstage = { String stageName, Map stageConfigMap, Closure c -> 
        if (!c) {
            c = stageConfigMap
            stageConfigMap = [:]
        }
        xci.xstage(stageName, stageConfigMap, c)
    }
    body.call(xstage)

    node(nodeLabel) {
        try {
            if (configMap.timeout) {
                timeout(time: configMap.timeout, unit: 'SECONDS') {
                    xci.run()
                }
            } else {
                xci.run()
            }
        } catch(e) {
            throw e
        } finally {
            if (callback && callback instanceof Closure) {
                callback.call(xci)
            }
        }
    }
}