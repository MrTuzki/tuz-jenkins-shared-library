/**
* 终止当前 build
* @param reason: 终止原因
*/
def abort(reason='') {
    currentBuild.result = 'ABORTED'
    def message
    if (reason) {
        message = "Aborting the build: ${reason}"
    } else {
        message = "Aborting the build!"
    }
    error(message)
}