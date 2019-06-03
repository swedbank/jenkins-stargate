package com.swedbank.jenkins.utilities

import com.lesfurets.jenkins.unit.MethodSignature
import com.lesfurets.jenkins.unit.global.lib.LibraryConfiguration
import com.lesfurets.jenkins.unit.global.lib.SourceRetriever
import com.swedbank.jenkins.utilities.exception.WhenExitException
import org.assertj.core.util.Files

import java.nio.charset.Charset
import java.text.SimpleDateFormat

import static com.lesfurets.jenkins.unit.MethodSignature.method
import static com.lesfurets.jenkins.unit.global.lib.LibraryConfiguration.library

class PipelineTestRunner extends BasePipelineClassLoaderTest {

    static enum CredentialsType {
        SECRET_TEXT, SECRET_FILE, USERNAME_PASSWORD, PRIVATE_KEY
    }

    /**
     * This flags indicates whether the helper should try to load script under test as the compiled
     * class or as the script source file.
     * For the first case we can get test coverage unblocked as the benefit, but this force us
     * to keep vars in the sourceSets.main.groovy.srcDirs
     */
    Boolean preferClassLoading = true
    PipelineTestHelperClassLoader internalHelper

    PipelineTestRunner() {
        this.setUp()
        internalHelper = helper
        // Add support to the helper to unregister a method
        internalHelper.metaClass.unRegisterAllowedMethod = { String name, List<Class> args ->
            allowedMethodCallbacks.remove(method(name, args.toArray(new Class[args.size()])))
        }
    }

    def run(Closure cl) {
        PipelineRunContext cnt = new PipelineRunContext()
        cl.resolveStrategy = Closure.DELEGATE_FIRST
        cl.delegate = cnt
        cl.call()
        return runContext(cnt)
    }

    /**
     * Loads the script but do not execute it.
     */
    def load(Closure cl) {
        PipelineRunContext cnt = new PipelineRunContext()
        cl.resolveStrategy = Closure.DELEGATE_FIRST
        cl.delegate = cnt
        cl.call()
        return loadContext(cnt)
    }

    /**
     * Runs the script
     */
    def protected loadContext(PipelineRunContext context) {
        context.registerDeclarativeMethods()
        registerSharedLibs(context)
        prepareEnv(context)
        registerMockMethod(context)
        printInfo(context)

        return internalHelper.loadScript(context.scriptPath, this.binding, preferClassLoading)
    }

    def protected runContext(PipelineRunContext context) {
        context.registerDeclarativeMethods()
        registerSharedLibs(context)
        prepareEnv(context)
        registerMockMethod(context)
        printInfo(context)

        internalHelper.runScript(context.scriptPath, binding, preferClassLoading)
        if (context.printStack) {
            printCallStack()
        }
    }

    def protected static printInfo(PipelineRunContext context) {
        println("Script: ${context.scriptPath}")
        println("Env: ${context.env}")
        println("Properties: ${context.properties}")
        println("Shared libraries: ${context.sharedLibs.collect { it.name }}")
        println("Scripts calls:")
        context.scriptHandlers.each { println("\t$it") }
        println()
    }

    def protected prepareEnv(PipelineRunContext context) {
        binding.setVariable('env', context.env)
        context.properties.each { name, value -> binding.setVariable(name, value) }
    }

    def protected registerMockMethod(PipelineRunContext context) {
        context.mockMethods.each { MethodSignature signature, closure ->
            if (signature != null) {
                internalHelper.registerAllowedMethod(signature, closure ?: { -> })
            }
        }
    }

    def protected registerSharedLibs(PipelineRunContext context) {
        context.sharedLibs.collect { internalHelper.registerSharedLibrary(it) }
    }

    /**
     * Holds the setup information for the pipeline unit framework
     */
    class PipelineRunContext {
        String scriptPath

        String script(name) {
            this.scriptPath = name
        }
        Map env = [
                BUILD_TIMESTAMP: (new SimpleDateFormat("MM/dd/yyyy HH:mm:ss")).format(new Date())
        ]
        // the key value pair of jenkins properties to define.
        Map properties = [
                scm: [:],
                params: [:],
        ]

        Map scriptHandlers = [
                'git-rev-parse-head': [
                        regexp: /git rev-parse HEAD/,
                        handler: { scriptParams -> return '29480a51' }
                ],
                'git-show-name': [
                        regexp: /git show -s --pretty=%an/,
                        handler: { scriptParams -> return 'Test Username' }
                ],
                'git-show-email': [
                        regexp: /git show -s --pretty=%ae/,
                        handler: { scriptParams -> return 'TestUsername@mail.some' }
                ],
                'git-status': [
                        regexp: /git status */,
                        handler: { scriptParams -> return 'nothing to commit, working tree clean' }
                ]
        ]
        Map<MethodSignature, Closure> mockMethods = [
                (method('string', Map.class))                         : { stringParam ->
                    return [(stringParam.name): stringParam?.defaultValue]
                },
                (method('booleanParam', Map.class))                   : { Map boolParam ->
                    return [(boolParam.name): boolParam.defaultValue.toString()]
                },
                (method('parameters', ArrayList.class))               : { paramsList ->
                    paramsList.each { param ->
                        param.each { name, val ->
                            // carefully override parameters to not rewrite the
                            // mocked ones
                            Map params = binding.getVariable('params') as Map
                            if (params == null) {
                                params = [:]
                                binding.setVariable('params', params)
                            }
                            if ((val != null) && (params[name] == null)) {
                                params[name] = val
                                binding.setVariable('params', params)
                            }
                        }
                    }
                },
                (method('sh', Map.class))                             : { shellMap ->
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
                },

                (method('sh', String.class))                          : { scriptStr ->
                    def res = scriptHandlers.find {
                        scriptStr ==~ it.value.regexp }?.value?.handler([script: scriptStr])
                    return res == null ? 0 : res
                },

                (method('emailext', LinkedHashMap.class))             : { mailParams -> },
                (method('findFiles', Map.class))                      : { fileParams -> return [length:1] },
                (method('readFile', String.class))                    : { file ->
                    return Files.contentOf(new File(file), Charset.forName('UTF-8'))
                },
                (method('httpRequest', LinkedHashMap.class))          : { requestParams ->
                    new Expando(status: 200, content: 'Mocked http request DONE')
                },
                (method('usernamePassword', Map.class))               : { creds -> return creds },

                (method('sshUserPrivateKey', Map.class))              : { creds -> return creds },
                (method('sshagent', List.class, Closure.class))       : { list, cl -> cl() },
                (method('withCredentials', List.class, Closure.class)): { list, closure ->
                    list.forEach {
                        it.findAll { it.key.endsWith('Variable') }?.each { k, v ->
                            binding.setVariable(v, "$v")
                        }
                    }
                    def res = closure.call()
                    list.forEach {
                        it.findAll { it.key.endsWith('Variable') }?.each { k, v ->
                            binding.setVariable(v, "$v")
                        }
                    }
                    return res
                },
                (method('stash', Map.class))                          : { map -> },
                (method('unstash', Map.class))                        : { map -> }
        ]

        Map credentialsTypes = [:]

        Boolean printStack = true
        def sharedLibs = new ArrayList<LibraryConfiguration>()

        def sharedLibrary(String name, String targetPath='.',
                          String version="master",
                          Boolean allowOverride=true,
                          Boolean implicit=true,
                          SourceRetriever retriever=null) {

            if (retriever == null) {
                retriever = new ProjectSource(targetPath)
            }

            sharedLibs.add(library().name(name)
                    .defaultVersion(version)
                    .allowOverride(allowOverride)
                    .implicit(implicit)
                    .targetPath(targetPath)
                    .retriever(retriever)
                    .build())
        }

        /**
         * Declares the script parameter. Will be accessible directly from pipeline
         */
        def property(String name, Object value) {
            this.properties.put(name, value)
        }

        /**
         * Declares the job parameter. Will be accessible through the params.<param_name>
         *     in the pipeline scripts.
         */
        def param(String name, String value) {
            def params = this.properties.get('params', [:])
            params.put(name, value)
            this.properties['params'] = params
        }

        def method(String name, List<Class> args = [], Closure closure) {
            mockMethods.put(method(
                    name, args.toArray(new Class[args?.size()])), closure)
        }

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
        /**
         * Declarative pipeline methods not in the base
         *
         * See here:
         * https://www.cloudbees.com/sites/default/files/declarative-pipeline-refcard.pdf
         */
        void registerDeclarativeMethods() {

            // For execution of the pipeline
            internalHelper.registerAllowedMethod('execute', [], { })
            internalHelper.registerAllowedMethod('pipeline', [Closure.class], null)
            internalHelper.registerAllowedMethod('options', [Closure.class], null)
            internalHelper.registerAllowedMethod('timeout', [Map.class], { str -> })
            internalHelper.registerAllowedMethod('skipDefaultCheckout', [boolean.class], { booleanValue -> })
            internalHelper.registerAllowedMethod('credentials', [String.class], { credId ->
                Map defaultCredData = [
                    type: CredentialsType.USERNAME_PASSWORD,
                    handler: null
                ]
                Map credData = this.credentialsTypes.get(credId, defaultCredData)
                CredentialsType credType = credData.get('type', defaultCredData.type)
                Closure credHandler = credData.get('handler')
                if (credHandler != null) {
                    return credHandler
                } else {
                    return { varName ->
                        String retValue = credId
                        switch (credType) {
                            case CredentialsType.SECRET_TEXT:
                                retValue += '_secret_text'
                                break
                            case CredentialsType.SECRET_FILE:
                                retValue += '_secret_file'
                                break
                            case CredentialsType.USERNAME_PASSWORD:
                                env["${varName}_USR"] = "${retValue}_user"
                                env["${varName}_PSW"] = "${retValue}_password"
                                retValue = "${retValue}_user:${retValue}_password"
                                break
                            case CredentialsType.PRIVATE_KEY:
                                env["${varName}_USR"] = "${retValue}_user"
                                env["${varName}_PSW"] = "${retValue}_password"
                                retValue += '_secret_file'
                                break
                            default:
                                break
                        }
                        return retValue
                    }
                }
            })
            // Handle environment section adding the env vars
            internalHelper.registerAllowedMethod('environment', [Closure.class], { Closure c ->

                def envBefore = [env: binding.getVariable('env')]
                println "Env section - original env vars: ${envBefore.toString()}"
                c.resolveStrategy = Closure.DELEGATE_FIRST
                c.delegate = envBefore
                c()

                def envNew = envBefore.env
                envBefore.each { k, v ->
                    if (k != 'env') {
                        if (v instanceof Closure) {
                            // call handler
                            envNew["$k"] = v(k)
                        } else {
                            // just set user defined env variable
                            envNew["$k"] = v
                        }
                    }
                }
                println "Env section - env vars set to: ${envNew.toString()}"
                binding.setVariable('env', envNew)
            })

            // Handle parameters section adding the default params
            internalHelper.registerAllowedMethod('parameters', [Closure.class], { Closure parametersBody ->

                // Register the contained elements
                internalHelper.registerAllowedMethod('string', [Map.class], { Map stringParam ->

                    // Add the param default for a string
                    addParam(stringParam.name, stringParam.defaultValue)

                })
                internalHelper.registerAllowedMethod('booleanParam', [Map.class], { Map boolParam ->
                    // Add the param default for a string
                    addParam(boolParam.name, boolParam.defaultValue.toString().toBoolean())
                })

                // Run the body closure
                def paramsResult = parametersBody()

                // Unregister the contained elements
                internalHelper.unRegisterAllowedMethod('string', [Map.class])
                internalHelper.unRegisterAllowedMethod('booleanParam', [Map.class])

                // Result to higher level. Is this needed?
                return paramsResult
            })

            // If any of these need special handling, it needs to be implemented here or in the tests with a closure instead of null
            internalHelper.registerAllowedMethod('triggers', [Closure.class], null)
            internalHelper.registerAllowedMethod('pollSCM', [String.class], null)
            internalHelper.registerAllowedMethod('cron', [String.class], null)

            internalHelper.registerAllowedMethod('agent', [Closure.class], null)
            internalHelper.registerAllowedMethod('label', [String.class], null)
            internalHelper.registerAllowedMethod('docker', [String.class], null)
            internalHelper.registerAllowedMethod('image', [String.class], null)
            internalHelper.registerAllowedMethod('args', [String.class], null)
            internalHelper.registerAllowedMethod('dockerfile', [Closure.class], null)
            internalHelper.registerAllowedMethod('dockerfile', [Boolean.class], null)

            internalHelper.registerAllowedMethod('timestamps', [], null)
            internalHelper.registerAllowedMethod('tools', [Closure.class], null)
            internalHelper.registerAllowedMethod('stages', [Closure.class], null)
            internalHelper.registerAllowedMethod('validateDeclarativePipeline', [String.class], null)

            internalHelper.registerAllowedMethod('parallel', [Closure.class], null)

            /**
             * Handling of a stage skipping execution in tests due to failure, abort, when
             */
            internalHelper.registerAllowedMethod('stage', [String.class, Closure.class], { String stgName, Closure body ->

                // Returned from the stage
                def stageResult

                // Handling of the when. Only supporting expression right now
                internalHelper.registerAllowedMethod('when', [Closure.class], { Closure whenBody ->

                    // Handle a when expression
                    internalHelper.registerAllowedMethod('expression', [Closure.class], { Closure expressionBody ->

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
                def status = binding.getVariable('currentBuild').result
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
                internalHelper.unRegisterAllowedMethod('when', [Closure.class])
                internalHelper.unRegisterAllowedMethod('expression', [Closure.class])

                return stageResult
            })

            internalHelper.registerAllowedMethod('steps', [Closure.class], null)
            internalHelper.registerAllowedMethod('script', [Closure.class], null)

            internalHelper.registerAllowedMethod('when', [Closure.class], null)
            internalHelper.registerAllowedMethod('expression', [Closure.class], null)
            internalHelper.registerAllowedMethod('post', [Closure.class], null)

            /**
             * Handling the post sections
             */
            def postResultEmulator = { String section, Closure c ->

                def currentBuild = binding.getVariable('currentBuild')

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
                        if (currentBuild.result == 'UNSUCCESSFUL') { return c.call() }
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
            internalHelper.registerAllowedMethod('always', [Closure.class], postResultEmulator.curry('always'))
            internalHelper.registerAllowedMethod('changed', [Closure.class], postResultEmulator.curry('changed'))
            internalHelper.registerAllowedMethod('success', [Closure.class], postResultEmulator.curry('success'))
            internalHelper.registerAllowedMethod('unstable', [Closure.class], postResultEmulator.curry('unstable'))
            internalHelper.registerAllowedMethod('failure', [Closure.class], postResultEmulator.curry('failure'))
            internalHelper.registerAllowedMethod('aborted', [Closure.class], postResultEmulator.curry('aborted'))
            internalHelper.registerAllowedMethod('regression', [Closure.class], postResultEmulator.curry('regression'))
            internalHelper.registerAllowedMethod('unsuccessful', [Closure.class], postResultEmulator.curry('unsuccessful'))
            internalHelper.registerAllowedMethod('fixed', [Closure.class], postResultEmulator.curry('fixed'))
            internalHelper.registerAllowedMethod('cleanup', [Closure.class], postResultEmulator.curry('cleanup'))
        }
    }
}
