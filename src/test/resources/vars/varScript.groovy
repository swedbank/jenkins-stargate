def call() {
    sh "echo 123"
    stash([:])
    unstash([:])
    return "var script"
}