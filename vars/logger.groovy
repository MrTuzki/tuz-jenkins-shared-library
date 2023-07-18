import com.tuz.Constants

def info(msg) {
    println("[${dateUtils.getDateTime()}][INFO] ${msg}")
}

def warn(msg) {
    println("[${dateUtils.getDateTime()}][WARN] ${msg}")
}

def error(msg) {
    println("[${dateUtils.getDateTime()}][ERROR] ${msg}")
}

def debug(msg) {
    if (Constants.DEBUG) {
        println("[${dateUtils.getDateTime()}][DEBUG] ${msg}")
    }
}