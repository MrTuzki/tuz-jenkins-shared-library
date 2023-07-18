def prettyPrint(object) {
    def ret = ''
    if (object instanceof String || object instanceof GString) {
        ret = groovy.json.JsonOutput.prettyPrint(object)
    } else {
        ret = groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(object))
    }
    
    logger.info(ret)
    return ret
}

def toJsonString(object) {
    return groovy.json.JsonOutput.toJson(object)
}

def toGroovyObject(text) {
    return new groovy.json.JsonSlurperClassic().parseText(text)
}