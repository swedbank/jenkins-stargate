package com.swedbank.jenkins.utilities

import com.swedbank.jenkins.utilities.utils.BasePipelineClassLoaderTest
import com.swedbank.jenkins.utilities.utils.PipelineClassLoaderTestHelper
import groovy.util.logging.Log4j2

@Log4j2
class PipelineTestRunner extends BasePipelineClassLoaderTest {

    /**
     * This flags indicates whether the helper should try to load script under test as the compiled
     * class or as the script source file.
     * For the first case we can get test coverage unblocked as the benefit, but this force us
     * to keep vars in the sourceSets.main.groovy.srcDirs
     */
    Boolean preferClassLoading = true
    PipelineClassLoaderTestHelper internalHelper

    PipelineTestRunner() {
        this.setUp()
        internalHelper = helper
    }

    def run(Closure cl) {
        PipelineRunContext cnt = new PipelineRunContext.Builder(this)
                .setupDefaultEnv()
                .setupDefaultExtensions()
                .build()

        cl.resolveStrategy = Closure.DELEGATE_FIRST
        cl.delegate = cnt
        cl.call()
        return runContext(cnt)
    }

    /**
     * Loads the script but do not execute it.
     */
    def load(Closure cl) {
        log.info("Loading script")
        PipelineRunContext cnt = new PipelineRunContext.Builder(this)
                .setupDefaultEnv()
                .setupDefaultExtensions()
                .build()

        cl.resolveStrategy = Closure.DELEGATE_FIRST
        cl.delegate = cnt
        cl.call()
        return loadContext(cnt)
    }

    def loadContext(PipelineRunContext context) {
        return internalHelper.loadScript(context.scriptPath, context.binding, preferClassLoading)
    }

    def runContext(PipelineRunContext context) {
        internalHelper.runScript(context.scriptPath, context.binding, preferClassLoading)
        if (context.printStack) {
            printCallStack()
        }
    }

    def validateContent() {
        //todo
    }
}
