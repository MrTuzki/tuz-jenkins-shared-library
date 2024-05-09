import hudson.AbortException

def assertTrue(condition, msg) {
    if (!condition) {
        throw new AbortException(msg)
    }
}

def assertFalse(condition, msg) {
    assertTrue(!condition, msg)
}

def assertNull(object, msg) {
    assertTrue(object == null, msg)
}

def assertNotNull(object, msg) {
    assertTrue(object != null, msg)
}