/**
* 获取当前时间
*/
def getDateTime(format="yyyy-MM-dd HH:mm:ss") {
    def now = new Date()
    return now.format(format, TimeZone.getTimeZone('Asia/Shanghai'))
}

/**
* 获取当前日期
*/
def getDate(format='yyyy-MM-dd') {
    return getDatetime(format)
}

/**
* 获取时间戳, 精确到毫秒
*/
def getTimestamp() {
    def now = new Date()
    return now.getTime()
}