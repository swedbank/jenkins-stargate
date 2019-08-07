package com.swedbank.jenkins.utilities.extension

import com.swedbank.jenkins.utilities.PipelineRunContext
import com.swedbank.jenkins.utilities.exception.WhenExitException

class DeclarativePipelineExt extends BaseContextExt {
    static enum CredentialsType {
        SECRET_TEXT, SECRET_FILE, USERNAME_PASSWORD, PRIVATE_KEY
    }

    final String extName = 'declarative'

    @Override
    void setupExt(PipelineRunContext context) {
        // initially copied and modified from here:
        // https://github.com/macg33zr/pipelineUnit/blob/master/pipelineTests/groovy/testSupport/PipelineTestHelper.groovy
        context.with {
            method('execute', []) { }
            method('pipeline', [Closure]) { cl -> cl() }
            method('options', [Closure]) { cl -> cl() }
            method('timeout', [Map]) { str -> }
            method('skipDefaultCheckout', [boolean]) { booleanValue -> }
            mockCredentialsMethod(it)
            method('environment', [Closure]) { Closure c ->
                Binding envBefore = new Binding()
                envBefore.metaClass.setProperty = { String name, Object value ->
                    if (value instanceof Closure) {
                        value(name)
                    } else {
                        env(name, value)
                        property(name, value)
                    }
                }
                envBefore.metaClass.setVariable = envBefore.&setProperty

                c.resolveStrategy = Closure.DELEGATE_FIRST
                c.delegate = envBefore
                c()
            }

            // Handle parameters section adding the default params
            method('parameters', [Closure]) { Closure parametersBody ->
                // Register the contained elements
                method('string', [Map]) { Map stringParam ->
                    // Add the param default for a string
                    param(stringParam.name, stringParam.defaultValue)
                }
                method('booleanParam', [Map]) { Map boolParam ->
                    // Add the param default for a string
                    param(boolParam.name, boolParam.defaultValue.toString().toBoolean())
                }

                // Run the body closure
                Object paramsResult = parametersBody()

                // Unregister the contained elements
                helper.unRegisterAllowedMethod('string', [Map])
                helper.unRegisterAllowedMethod('booleanParam', [Map])

                // Result to higher level. Is this needed?
                return paramsResult
            }

            // If any of these need special handling, it needs to be implemented here or in the tests with a closure instead of null
            method('triggers', [Closure]) { cl -> cl() }
            method('pollSCM', [String]) { str -> }
            method('cron', [String]) { str -> }

            method('agent', [Closure]) { cl -> cl() }
            method('label', [String]) { str -> }
            method('docker', [String]) { str -> }
            method('image', [String]) { str -> }
            method('args', [String]) { str -> }
            method('dockerfile', [Closure]) { cl -> cl() }
            method('dockerfile', [Boolean]) { bool -> }
            method('timestamps', []) { str -> }
            method('tools', [Closure]) { cl -> cl() }
            method('stages', [Closure]) { cl -> cl() }
            method('validateDeclarativePipeline', [String]) { str -> }

            method('parallel', [Closure]) { cl -> cl() }
            mockStageMethod(it)

            method('steps', [Closure]) { cl -> cl() }
            method('script', [Closure]) { cl -> cl() }

            method('when', [Closure]) { cl -> cl() }
            method('expression', [Closure]) { cl -> cl() }
            method('post', [Closure]) { cl -> cl() }

            /**
             * Handling the post sections
             */
            Closure postResultEmulator = { String section, Closure c ->

                Object currentBuild = context.binding.getVariable('currentBuild')

                switch (section) {
                    case 'always':
                    case 'cleanup':
                    case 'changed': // How to handle changed? It may happen so just run it..
                        return c.call()
                    case 'success':
                        if (currentBuild.result == 'SUCCESS') {
                            return c.call()
                        }
                        break
                    case 'unstable':
                        if (currentBuild.result == 'UNSTABLE') {
                            return c.call()
                        }
                        break
                    case 'failure':
                        if (currentBuild.result == 'FAILURE') {
                            return c.call()
                        }
                        break
                    case 'aborted':
                        if (currentBuild.result == 'ABORTED') {
                            return c.call()
                        }
                        break
                    case 'regression':
                        if (currentBuild.result == 'REGRESSION') {
                            return c.call()
                        }
                        break
                    case 'unsuccessful':
                        if (currentBuild.result != 'SUCCESS') {
                            return c.call()
                        }
                        break
                    case 'fixed':
                        if (currentBuild.result == 'FIXED') {
                            return c.call()
                        }
                        break
                    default:
                        break
                }
            }
            method('always', [Closure], postResultEmulator.curry('always'))
            method('changed', [Closure], postResultEmulator.curry('changed'))
            method('success', [Closure], postResultEmulator.curry('success'))
            method('unstable', [Closure], postResultEmulator.curry('unstable'))
            method('failure', [Closure], postResultEmulator.curry('failure'))
            method('aborted', [Closure], postResultEmulator.curry('aborted'))
            method('regression', [Closure], postResultEmulator.curry('regression'))
            method('unsuccessful', [Closure], postResultEmulator.curry('unsuccessful'))
            method('fixed', [Closure], postResultEmulator.curry('fixed'))
            method('cleanup', [Closure], postResultEmulator.curry('cleanup'))
        }
    }

    /**
     * Handling of a stage skipping execution in tests due to failure, abort, when
     */
    private static List mockStageMethod(PipelineRunContext context) {
        context.with {
            method('stage', [String, Closure]) { String stgName, Closure body ->

                // Returned from the stage
                Object stageResult

                // Handling of the when. Only supporting expression right now
                method('when', [Closure]) { Closure whenBody ->

                    // Handle a when expression
                    method('expression', [Closure]) { Closure expressionBody ->

                        // Run the expression and return any result
                        Object expressionResult = expressionBody()
                        if (expressionResult == false) {
                            throw new WhenExitException("Stage '${stgName}' skipped due to when expression returned false")
                        }
                        return expressionResult
                    }

                    // TODO - handle other when clauses in the when
                    // branch : 'when { branch 'master' }'
                    // environment : 'when { environment name: 'DEPLOY_TO', value: 'production' }'

                    // Run the when body and return any result
                    return whenBody()
                }

                // Stage is not executed if build fails or aborts
                String status = binding.getVariable('currentBuild').result
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
                            println we.message
                        }
                }

                // Unregister
                helper.unRegisterAllowedMethod('when', [Closure])
                helper.unRegisterAllowedMethod('expression', [Closure])

                return stageResult
            }
        }
    }

    private List mockCredentialsMethod(PipelineRunContext context) {
        context.with {
            method('credentials', [String]) { credId ->
                Map defaultCredData = [
                        type: CredentialsType.USERNAME_PASSWORD,
                        handler: null,
                ]
                Map credData = credentialsTypes.get(credId, defaultCredData)
                CredentialsType credType = credData.get('type', defaultCredData.type)
                Closure credHandler = credData.get('handler')
                if (credHandler != null) {
                    return credHandler
                }
                return { varName ->
                    String retValue = credId
                    switch (credType) {
                        case CredentialsType.SECRET_TEXT:
                            property(varName, retValue + '_secret_text')
                            env(varName, retValue + '_secret_text')
                            break
                        case CredentialsType.SECRET_FILE:
                            property(varName, retValue + '_secret_file')
                            env(varName, retValue + '_secret_file')
                            break
                        case CredentialsType.USERNAME_PASSWORD:
                            env("${varName}_USR", "${retValue}_user")
                            env("${varName}_PSW", "${retValue}_password")
                            property("${varName}_USR", "${retValue}_user")
                            property("${varName}_PSW", "${retValue}_password")
                            property(varName, "${retValue}_user:${retValue}_password")
                            env(varName, "${retValue}_user:${retValue}_password")
                            break
                        case CredentialsType.PRIVATE_KEY:
                            env("${varName}_USR", "${retValue}_user")
                            env("${varName}_PSW", "${retValue}_password")
                            property("${varName}_USR", "${retValue}_user")
                            property("${varName}_PSW", "${retValue}_password")
                            property(varName, retValue + '_private_file')
                            env(varName, retValue + '_private_file')
                            break
                        default:
                            break
                    }
                }
            }
        }
    }

    Map credentialsTypes = [:]

    /**
     * Registers credentials for the environment block in the declarative pipeline
     * @param id credentials id
     * @param credType credentials type
     * @param Optional. Custom handler for the credentials block. Should return the variable value.
     *  Accepts the variable name that will hold the value.
     */
    void registerCredentials(String id, CredentialsType credType, Closure credHandler=null) {
        this.credentialsTypes[id] = [type: credType, handler: credHandler]
    }
}
