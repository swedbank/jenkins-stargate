package com.swedbank.jenkins.utilities

import com.lesfurets.jenkins.unit.global.lib.SourceRetriever

class ProjectSource implements SourceRetriever {

    def sourceDir

    ProjectSource() {
        ProjectSource('.')
    }

    ProjectSource(String sourcePath) {
        sourceDir = new File(sourcePath)
    }

    List<URL> retrieve(String repository, String branch, String targetPath) {
        if (sourceDir.exists()) {
            return [sourceDir.toURI().toURL()]
        }
        throw new IllegalStateException("Directory $sourceDir.path does not exists!")
    }
}

