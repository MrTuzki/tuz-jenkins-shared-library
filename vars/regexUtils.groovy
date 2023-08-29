def matchOne(regex, content) {
    def matcher = content =~ regex
    if (matcher.find()) {
        return matcher[0]
    }
}

def matchAll(regex, content) {
    def matcher = content =~ regex
    if (matcher.find()) {
        return matcher[0..-1]
    }
}

def matches(regex, content) {
    def matcher = content =~ regex
    return matcher.find()
}

def replace(regex, content) {
    return content.replaceAll(regex, content)
}