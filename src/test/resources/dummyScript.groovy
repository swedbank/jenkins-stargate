def call() {
    node {
        // passively increase coverage of mocks
        parameters([string(name: 'param1', defaultValue: 'Testing')])
        withCredentials([usernamePassword(credentialsId: 'mytoken', usernameVariable: 'USERNAME')]) {
            sh "echo $USERNAME"
            // passively verify default mocks for shell handlers
            sh 'git rev-parse HEAD'
            sh 'git rev-parse --abbrev-ref HEAD'
            sh 'git show -s --pretty=%an'
            sh 'git show -s --pretty=%ae'
            sh 'git status'
            sh 'git rebase'
            sh (script: 'git reverse', returnStatus: true)
        }
    }
    return 'testresult'
}