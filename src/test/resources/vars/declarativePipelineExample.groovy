def call() {
    pipeline {
        agent  {
            label 'dubnium-strict-net'
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
            }
        }
    }
}