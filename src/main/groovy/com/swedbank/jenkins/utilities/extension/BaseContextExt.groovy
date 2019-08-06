package com.swedbank.jenkins.utilities.extension

import com.swedbank.jenkins.utilities.PipelineRunContext

abstract class BaseContextExt {
    /**
     * Specifies the extension name which will appear in the context object
     * and will map to the extension object.
     */
    abstract String getExtName()

    /**
     * Performs initialization and setup of the extension object.
     * Place initial methods mocking and vars initialization into that method
     */
    abstract def setupExt(PipelineRunContext cnt)

    def callExt(PipelineRunContext context, Closure cl) {
        cl.resolveStrategy = Closure.DELEGATE_FIRST
        cl.delegate = this
        cl(context)
    }
}
