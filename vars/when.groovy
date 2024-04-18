import org.jenkinsci.plugins.pipeline.modeldefinition.Utils

def call(condition, body) {
    if (condition) {
        body.call()
    } else {
        Utils.markStageSkippedForConditional(STAGE_NAME)
    }
}