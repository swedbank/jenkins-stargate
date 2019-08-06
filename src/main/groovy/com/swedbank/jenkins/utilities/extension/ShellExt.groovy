package com.swedbank.jenkins.utilities.extension

import com.swedbank.jenkins.utilities.PipelineRunContext
import groovy.util.logging.Log4j2

@Log4j2
class ShellExt extends BaseContextExt {
    @Override
    String getExtName() {
        "shell"
    }

    @Override
    def setupExt(PipelineRunContext context) {
        this.handler('git-rev-parse-head', [
                regexp: /git rev-parse HEAD/,
                handler: { scriptParams -> return '29480a51' }
        ])
        this.handler('git-show-name', [
                regexp: /git show -s --pretty=%an/,
                handler: { scriptParams -> return 'Test Username' }
        ])
        this.handler('git-show-email', [
                regexp: /git show -s --pretty=%ae/,
                handler: { scriptParams -> return 'TestUsername@mail.some' }
        ])
        this.handler('git-status', [
                regexp: /git status */,
                handler: { scriptParams -> return 'nothing to commit, working tree clean' }
        ])
        setupShellMockMethods(context)
    }

    protected def setupShellMockMethods(PipelineRunContext context) {
        context.method('sh', [Map], { shellMap ->
            def res = scriptHandlers.find {
                shellMap.script ==~ it.value.regexp }?.value?.handler(shellMap)

            if (res == null) {
                if (shellMap.returnStdout) {
                    res = 'dummy response'
                } else if (shellMap.returnStatus) {
                    res = 0
                }
            }
            return res
        })

        context.method('sh', [String], { scriptStr ->
            def res = scriptHandlers.find {
                scriptStr ==~ it.value.regexp }?.value?.handler([script: scriptStr])
            return res == null ? 0 : res
        })
        return this
    }

    // exposed names
    Map<String, Object> scriptHandlers = [:]

    /**
     * Registers new shell mock. The handlerParams should be in the next form:
     * [
     *     regexp : /echo 123/,
     *     handler: { scriptParams -> //....  }*
     * ]
     */
    def handler(String name, Map handlerParams) {
        log.info("New shell handler -> ${name}: ${handlerParams}")
        scriptHandlers[name] = handlerParams
    }

}
