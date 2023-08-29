/**
* 获取 Git 项目的名称
*/
def getProjectName() {
    return sh(script: "basename -s .git `git config --get remote.origin.url`", returnStdout:true).trim()
}

/**
* 获取 Git 项目的地址
*/
def getProjectUrl() {
    return sh(script: "git config --get remote.origin.url", returnStdout:true).trim()
}

/**
* 获取changelog
*/
def getChangelog() {
    return sh(script: 'git log --pretty=format:%B -1', returnStdout:true).trim()
}

/**
* 获取更详细的changelog
*/
def getFullerChangelog() {
    return sh(script: 'git log --pretty=fuller -1', returnStdout:true).trim()
}

/**
* 获取CommitId, 如果不传递分支名, 默认是当前分支的最新CommitId
*/
def getCommitId(branch=null) {
    if (branch == null) {
        sh(script: "git rev-parse HEAD", returnStdout: true).trim()
    } else {
        return sh(script: "git rev-parse ${branch}", returnStdout: true).trim()
    }
}

/**
* 获取当前commit的提交者
* 注意: 不再使用
*/
def getCommitUser() {
    return sh(script: "git log --pretty=format:'%an' -1", returnStdout:true).trim()
}

/**
* 获取当前提交的 Author Name
*/
def getAuthorName() {
    return sh(script: "git log --pretty=format:'%an' -1", returnStdout:true).trim()
}

/**
* 获取当前提交的 Author Email
*/
def getAuthorEmail() {
    return sh(script: "git log --pretty=format:'%ae' -1", returnStdout:true).trim()
}

/**
* 获取当前提交的 Committer Name
*/
def getCommitterName() {
    return sh(script: "git log --pretty=format:'%cn' -1", returnStdout:true).trim()
}

/**
* 获取当前提交的 Committer Email
*/
def getCommitterEmail() {
    return sh(script: "git log --pretty=format:'%ce' -1", returnStdout:true).trim()
}

/**
* 检查当前的Commit是否有Tag
*/
def isCurrentCommitHasTag() {
    return sh(script: "git describe --exact-match --tags `git log -n1 --pretty='%h'`", returnStdout:true).trim()
}

/**
* 检查某个字符串是否是tag
*/
def isTag(tagName) {
    try {
        sh(script: "git show-ref --verify refs/tags/${tagName}")
        return true
    } catch(Exception e) {
        return false
    }
}