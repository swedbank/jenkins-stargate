def call() {
    pipeline {
        agent  {
            label 'dubnium-strict-net'
        }

        environment {
            TEST_ENV_VAR = "TEST1"
            TEST_CRED_ENV_VAR = credentials("MY_CREDS")
        }

        options {
            timeout(time: 30, unit: 'MINUTES')
        }

        stages {
            stage ('Template') {
                steps {
                    echo 'Hello world!'
                }
            }
        }

        post {
            success {
                echo 'Oh, Happy days!'
                echo "${env.TEST_ENV_VAR}"
            }
        }
    }
}