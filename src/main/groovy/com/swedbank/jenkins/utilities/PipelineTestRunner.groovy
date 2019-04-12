package com.swedbank.jenkins.utilities

import com.lesfurets.jenkins.unit.MethodSignature
import com.lesfurets.jenkins.unit.global.lib.LibraryConfiguration
import com.lesfurets.jenkins.unit.global.lib.SourceRetriever
import org.assertj.core.util.Files

import java.nio.charset.Charset
import java.text.SimpleDateFormat

import static com.lesfurets.jenkins.unit.MethodSignature.method
import static com.lesfurets.jenkins.unit.global.lib.LibraryConfiguration.library

class PipelineTestRunner extends BasePipelineClassLoaderTest {
    /**
     * This flags indicates whether the helper should try to load script under test as the compiled
     * class or as the script source file.
     * For the first case we can get test coverage unblocked as the benefit, but this force us
     * to keep vars in the sourceSets.main.groovy.srcDirs
     */
    Boolean preferClassLoading = true
    PipelineTestHelperClassLoader internalHelper;

    PipelineTestRunner() {
        this.setUp()
        internalHelper = helper
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
        registerSharedLibs(context)
        prepareEnv(context)
        registerMockMethod(context)
        printInfo(context)

        return internalHelper.loadScript(context.scriptPath, this.binding, preferClassLoading)
    }

    def protected runContext(PipelineRunContext context) {
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
    }
}
