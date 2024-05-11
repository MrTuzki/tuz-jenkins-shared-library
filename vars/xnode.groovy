import hudson.AbortException
import com.tuz.XCI

def call(String nodeLabel = '', Map configMap = [:], Closure body) {
    logger.debug("nodeLabel=${nodeLabel}, configMap=${configMap}")
    def nodeSelector = configMap.get('nodeSelector', [:])
    def callback = configMap.get('callback',  { })

    def xci = new XCI(this)
    def xstage = { String stageName, Map stageConfigMap, Closure c -> 
        if (!c) {
            c = stageConfigMap
            stageConfigMap = [:]
        }
        xci.xstage(stageName, stageConfigMap, c)
    }
    body.call(xstage)

    def nodeName = selectNode(nodeLabel, nodeSelector)
    node(nodeName) {
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
            try {
                callback.call(xci)
            } catch(e) {
                // ignored
                logger.warn("[xnode] callback exec failed: ${e}")
            }
        }
    }
}

@NonCPS
def selectNode(String nodeLabel, Map nodeSelector) {
    if (nodeLabel || !nodeSelector.prefers) {
        return nodeLabel
    }

    def queueable = nodeSelector.get('queueable', false)
    def prefers = nodeSelector.get('prefers', [])
    def excludes = nodeSelector.get('excludes', [])
    for (prefer in prefers) {
        def nodes = jenkins.model.Jenkins.instance.getLabel(prefer)?.getNodes()
        if (!nodes) {
            continue
        }
        for (node in nodes) {
            def nodeName = node.getNodeName()
            if (nodeName in excludes) {
                continue
            }
            def computer = node.toComputer()
            if (!computer || computer.isOffline()) {
                continue
            }

            if (queueable) {
                return nodeName
            }
            def executor = computer.getExecutors()?.find { e -> 
                !e.isBusy()
            }
            if (executor) {
                return nodeName
            }
        }
    }
    throw new AbortException('no available node!')
}