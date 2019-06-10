def call() {
    pipeline {
        agent  {
            label 'dubnium-strict-net'
        }

        environment {
            TEST_ENV_VAR = "TEST1"
            TEST_CRED_ENV_VAR = credentials("MY_CREDS")
            TEST_CRED_MOCKED = credentials("${myLibrary.getCredName()}")
            TEST_CRED_MOCKED2 = "${TEST_ENV_VAR}_REUSE"
            TEST_CRED_MOCKED3 = "${env.TEST_ENV_VAR}_REUSE2"
            TEST_CRED_MOCKED4 = "${env.BUILD_TIMESTAMP}"
        }

        post {
            success {
                echo "${env.TEST_ENV_VAR}"
            }
        }
    }
}