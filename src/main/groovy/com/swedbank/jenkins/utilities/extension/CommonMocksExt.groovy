package com.swedbank.jenkins.utilities.extension

import com.swedbank.jenkins.utilities.PipelineRunContext

/**
 * Holds mocks for the basic pipeline methods.
 */
class CommonMocksExt extends BaseContextExt {
    final String extName = 'common'

    @Override
    void setupExt(PipelineRunContext context) {
        context.with {
            method('string', [Map]) { stringParam -> [(stringParam.name): stringParam?.defaultValue] }
            method('booleanParam', [Map]) { boolParam -> [(boolParam.name): boolParam.defaultValue.toString()] }
            method('parameters', [ArrayList]) { paramsList ->
                paramsList.each { param ->
                    param.each { String name, Object val ->
                        context.param(name, val, false)
                    }
                }
            }
            method('emailext', [Map]) { mailParams -> }
            method('findFiles', [Map]) { fileParams -> [length: 1] }
            method('readFile', [String]) { file ->
                Files.contentOf(new File(file), Charset.forName('UTF-8'))
            }
            method('httpRequest', [Map]) { requestParams ->
                [status: 200, content: 'Mocked http request DONE']
            }
            method('usernamePassword', [Map]) { creds -> return creds }
            method('sshUserPrivateKey', [Map]) { creds -> return creds }
            method('sshagent', [List, Closure]) { list, cl -> cl() }
            method('stash', [Map]) { map -> }
            method('unstash', [Map]) { map -> }
            method('withCredentials', [List, Closure]) { list, closure ->
                list.forEach {
                    it.findAll { it.key.endsWith('Variable') }?.each { k, v ->
                        context.property(v, "$v")
                    }
                }

                return closure.call()
            }
        }
    }
}
