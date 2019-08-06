package com.swedbank.jenkins.utilities.extension

import com.swedbank.jenkins.utilities.PipelineRunContext

class CommonMocksExt extends BaseContextExt {
    @Override
    String getExtName() {
        "common"
    }

    @Override
    def setupExt(PipelineRunContext context) {
        context.method('string', [Map], { stringParam -> [(stringParam.name): stringParam?.defaultValue] })
        context.method('booleanParam', [Map], { boolParam -> [(boolParam.name): boolParam.defaultValue.toString()] })
        context.method('parameters', [ArrayList.class], { paramsList ->
            paramsList.each { param ->
                param.each { String name, Object val ->
                    context.param(name, val, false)
                }
            }
        })
        context.method('emailext', [Map], { mailParams -> })
        context.method('findFiles', [Map], { fileParams -> [length:1] })
        context.method('readFile', [String], { file -> Files.contentOf(new File(file), Charset.forName('UTF-8')) })
        context.method('httpRequest', [Map], { requestParams -> [status: 200, content: 'Mocked http request DONE'] })
        context.method('usernamePassword', [Map], { creds -> return creds })
        context.method('sshUserPrivateKey', [Map], { creds -> return creds })
        context.method('sshagent', [List, Closure], { list, cl -> cl() })
        context.method('stash', [Map], { map -> })
        context.method('unstash', [Map], { map -> })
        context.method('withCredentials', [List, Closure], { list, closure ->
            list.forEach {
                it.findAll { it.key.endsWith('Variable') }?.each { k, v ->
                    context.property(v, "$v")
                }
            }
            def res = closure.call()
            list.forEach {
                it.findAll { it.key.endsWith('Variable') }?.each { k, v ->
                    binding.setVariable(v, null)
                }
            }
            return res
        })
    }
}
