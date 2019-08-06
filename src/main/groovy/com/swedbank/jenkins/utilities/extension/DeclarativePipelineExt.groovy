package com.swedbank.jenkins.utilities.extension

import com.swedbank.jenkins.utilities.PipelineRunContext
import com.swedbank.jenkins.utilities.exception.WhenExitException

class DeclarativePipelineExt extends BaseContextExt {
    static enum CredentialsType {
        SECRET_TEXT, SECRET_FILE, USERNAME_PASSWORD, PRIVATE_KEY
    }

    @Override
    String getExtName() {
        return "declarative"
    }

    @Override
    def setupExt(PipelineRunContext context) {
        // initially copied and modified from here:
        // https://github.com/macg33zr/pipelineUnit/blob/master/pipelineTests/groovy/testSupport/PipelineTestHelper.groovy
        context.method('execute', [], { })
        context.method('pipeline', [Closure], { cl -> cl() })
        context.method('options', [Closure], { cl -> cl() })
        context.method('timeout', [Map], { str -> })
        context.method('skipDefaultCheckout', [boolean], { booleanValue -> })
        context.method('credentials', [String], { credId ->
            Map defaultCredData = [
                    type: CredentialsType.USERNAME_PASSWORD,
                    handler: null
            ]
            Map credData = credentialsTypes.get(credId, defaultCredData)
            CredentialsType credType = credData.get('type', defaultCredData.type)
            Closure credHandler = credData.get('handler')
            if (credHandler != null) {
                return credHandler
            } else {
                return { varName ->
                    String retValue = credId
                    switch (credType) {
                        case CredentialsType.SECRET_TEXT:
                            context.property(varName, retValue + '_secret_text')
                            context.env(varName, retValue + '_secret_text')
                            break
                        case CredentialsType.SECRET_FILE:
                            context.property(varName, retValue + '_secret_file')
                            context.env(varName, retValue + '_secret_file')
                            break
                        case CredentialsType.USERNAME_PASSWORD:
                            context.env("${varName}_USR", "${retValue}_user")
                            context.env("${varName}_PSW", "${retValue}_password")
                            context.property("${varName}_USR", "${retValue}_user")
                            context.property("${varName}_PSW", "${retValue}_password")
                            context.property(varName, "${retValue}_user:${retValue}_password")
                            context.env(varName, "${retValue}_user:${retValue}_password")
                            break
                        case CredentialsType.PRIVATE_KEY:
                            context.env("${varName}_USR", "${retValue}_user")
                            context.env("${varName}_PSW", "${retValue}_password")
                            context.property("${varName}_USR", "${retValue}_user")
                            context.property("${varName}_PSW", "${retValue}_password")
                            context.property(varName, retValue + '_private_file')
                            context.env(varName, retValue + '_private_file')
                            break
                        default:
                            break
                    }
                    return retValue
                }
            }
        })
        context.method('environment', [Closure], { Closure c ->
            def envBefore = new Binding()
            envBefore.metaClass.setProperty = { String name, Object value ->
                if (value instanceof Closure) {
                    value = value(name)
                } else {
                    context.env(name, value)
                    context.property(name, value)
                }
            }
            envBefore.metaClass.setVariable = envBefore.&setProperty

            c.resolveStrategy = Closure.DELEGATE_FIRST
            c.delegate = envBefore
            c()
        })

        // Handle parameters section adding the default params
        context.method('parameters', [Closure], { Closure parametersBody ->

            // Register the contained elements
            context.method('string', [Map], { Map stringParam ->

                // Add the param default for a string
                context.param(stringParam.name, stringParam.defaultValue)

            })
            context.method('booleanParam', [Map.class], { Map boolParam ->
                // Add the param default for a string
                context.param(boolParam.name, boolParam.defaultValue.toString().toBoolean())
            })

            // Run the body closure
            def paramsResult = parametersBody()

            // Unregister the contained elements
            context.helper.unRegisterAllowedMethod('string', [Map.class])
            context.helper.unRegisterAllowedMethod('booleanParam', [Map.class])

            // Result to higher level. Is this needed?
            return paramsResult
        })

        // If any of these need special handling, it needs to be implemented here or in the tests with a closure instead of null
        context.method('triggers', [Closure], { cl -> cl() })
        context.method('pollSCM', [String], { str -> })
        context.method('cron', [String], { str -> })

        context.method('agent', [Closure], { cl -> cl() })
        context.method('label', [String], { str -> })
        context.method('docker', [String], { str -> })
        context.method('image', [String], { str -> })
        context.method('args', [String], { str -> })
        context.method('dockerfile', [Closure], { cl -> cl() })
        context.method('dockerfile', [Boolean], { bool -> })
        context.method('timestamps', [], { str -> })
        context.method('tools', [Closure], { cl -> cl() })
        context.method('stages', [Closure], { cl -> cl() })
        context.method('validateDeclarativePipeline', [String], { str -> })

        context.method('parallel', [Closure], { cl -> cl() })

        /**
         * Handling of a stage skipping execution in tests due to failure, abort, when
         */
        context.method('stage', [String, Closure], { String stgName, Closure body ->

            // Returned from the stage
            def stageResult

            // Handling of the when. Only supporting expression right now
            context.method('when', [Closure], { Closure whenBody ->

                // Handle a when expression
                context.method('expression', [Closure], { Closure expressionBody ->

                    // Run the expression and return any result
                    def expressionResult = expressionBody()
                    if (expressionResult == false) {
                        throw new WhenExitException("Stage '${stgName}' skipped due to when expression returned false")
                    }
                    return expressionResult
                })

                // TODO - handle other when clauses in the when
                // branch : 'when { branch 'master' }'
                // environment : 'when { environment name: 'DEPLOY_TO', value: 'production' }'

                // Run the when body and return any result
                return whenBody()
            })

            // Stage is not executed if build fails or aborts
            def status = context.binding.getVariable('currentBuild').result
            switch (status) {
                case 'FAILURE':
                case 'ABORTED':
                    println "Stage '${stgName}' skipped - job status: '${status}'"
                    break
                default:

                    // Run the stage body. A when statement may exit with an exception
                    try {
                        stageResult = body()
                    }
                    catch (WhenExitException we) {
                        // The when exited with an exception due to returning false. Swallow it.
                        println we.getMessage()
                    }
                    catch (Exception e) {
                        // Some sort of error in the pipeline
                        throw e
                    }

            }

            // Unregister
            context.helper.unRegisterAllowedMethod('when', [Closure])
            context.helper.unRegisterAllowedMethod('expression', [Closure])

            return stageResult
        })

        context.method('steps', [Closure], { cl -> cl() })
        context.method('script', [Closure], { cl -> cl() })

        context.method('when', [Closure], { cl -> cl() })
        context.method('expression', [Closure], { cl -> cl() })
        context.method('post', [Closure], { cl -> cl() })

        /**
         * Handling the post sections
         */
        def postResultEmulator = { String section, Closure c ->

            def currentBuild = context.binding.getVariable('currentBuild')

            switch (section) {
                case 'always':
                case 'cleanup':
                case 'changed': // How to handle changed? It may happen so just run it..
                    return c.call()
                    break
                case 'success':
                    if (currentBuild.result == 'SUCCESS') { return c.call() }
                    else { println "post ${section} skipped as not SUCCESS"; return null }
                    break
                case 'unstable':
                    if (currentBuild.result == 'UNSTABLE') { return c.call() }
                    else { println "post ${section} skipped as SUCCESS"; return null }
                    break
                case 'failure':
                    if (currentBuild.result == 'FAILURE') { return c.call() }
                    else { println "post ${section} skipped as not FAILURE"; return null }
                    break
                case 'aborted':
                    if (currentBuild.result == 'ABORTED') { return c.call() }
                    else { println "post ${section} skipped as not ABORTED"; return null }
                    break
                case 'regression':
                    if (currentBuild.result == 'REGRESSION') { return c.call() }
                    else { println "post ${section} skipped as not FAILURE"; return null }
                    break
                case 'unsuccessful':
                    if (currentBuild.result != 'SUCCESS') { return c.call() }
                    else { println "post ${section} skipped as not SUCCESS"; return null }
                    break
                case 'fixed':
                    if (currentBuild.result == 'FIXED') { return c.call() }
                    else { println "post ${section} skipped as not SUCCESS"; return null }
                    break
                default:
                    assert false, "post section ${section} is not recognised. Check pipeline syntax."
                    break
            }
        }
        context.method('always', [Closure], postResultEmulator.curry('always'))
        context.method('changed', [Closure], postResultEmulator.curry('changed'))
        context.method('success', [Closure], postResultEmulator.curry('success'))
        context.method('unstable', [Closure], postResultEmulator.curry('unstable'))
        context.method('failure', [Closure], postResultEmulator.curry('failure'))
        context.method('aborted', [Closure], postResultEmulator.curry('aborted'))
        context.method('regression', [Closure], postResultEmulator.curry('regression'))
        context.method('unsuccessful', [Closure], postResultEmulator.curry('unsuccessful'))
        context.method('fixed', [Closure], postResultEmulator.curry('fixed'))
        context.method('cleanup', [Closure], postResultEmulator.curry('cleanup'))

        return this
    }

    Map credentialsTypes = [:]

    /**
     * Registers credentials for the environment block in the declarative pipeline
     * @param id credentials id
     * @param credType credentials type
     * @param Optional. Custom handler for the credentials block. Should return the variable value.
     *  Accepts the variable name that will hold the value.
     */
    def registerCredentials(String id, CredentialsType credType, Closure credHandler=null) {
        this.credentialsTypes[id] = [type: credType, handler: credHandler]
    }
}
