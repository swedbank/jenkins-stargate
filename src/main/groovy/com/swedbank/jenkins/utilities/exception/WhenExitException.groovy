package com.swedbank.jenkins.utilities.exception

/**
 * An exception class to exit a stage due to the when statement
 */
class WhenExitException extends Exception {

    WhenExitException(String message) {
        super(message)
    }
}
