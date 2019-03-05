package com.swedbank.jenkins.utilities

import org.assertj.core.util.Files

import java.nio.charset.Charset
import java.text.SimpleDateFormat

import static com.lesfurets.jenkins.unit.global.lib.LibraryConfiguration.library

import com.lesfurets.jenkins.unit.BasePipelineTest
import com.lesfurets.jenkins.unit.global.lib.LibraryConfiguration
import com.lesfurets.jenkins.unit.global.lib.SourceRetriever


class PipelineTestRunner extends BasePipelineTest {

    PipelineTestRunner() {
        this.setUp()
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

        return loadScript(context.scriptPath)
    }

    def protected runContext(PipelineRunContext context) {
        registerSharedLibs(context)
        prepareEnv(context)
        registerMockMethod(context)
        printInfo(context)

        runScript(context.scriptPath)
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
        context.mockMethods.each { name, params ->
            helper.registerAllowedMethod(name, params.first() as List<Class>, params.last() as Closure) }
    }

    def protected registerSharedLibs(PipelineRunContext context) {
        context.sharedLibs.collect { helper.registerSharedLibrary(it) }
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
                        handler: { scriptParams -> return '29480a51' }],
                'git-show-name': [
                        regexp: /git show -s --pretty=%an/,
                        handler: { scriptParams -> return 'Test Username' }],
                'git-show-email': [
                        regexp: /git show -s --pretty=%ae/,
                        handler: { scriptParams -> return 'TestUsername@mail.some' }],
                'git-status': [
                        regexp: /git status */,
                        handler: { scriptParams -> return 'nothing to commit, working tree clean' }]
        ]
        Map  mockMethods = [
                string: [[Map.class], { stringParam ->
                    return [(stringParam.name): stringParam?.defaultValue]
                }],
                booleanParam: [[Map.class], { Map boolParam ->
                    return [(boolParam.name): boolParam.defaultValue.toString()]
                }],
                parameters: [[ArrayList], { paramsList ->
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
                }],
                sh: [[Map.class], { shellMap ->
                    def res = scriptHandlers.find {
                        shellMap.script ==~ it.value.regexp }?.value?.handler(shellMap)

                    if (res == null) {
                        if (shellMap.returnStdout) {
                            res = "dummy response"
                        } else if (shellMap.returnStatus) {
                            res = 0
                        }
                    }
                    return res

                }],
                emailext: [[LinkedHashMap.class], { mailParams -> }],

                findFiles: [[Map.class], { fileParams ->
                    return [length:1] }],
                readFile: [[String.class], { file ->
                    return Files.contentOf(new File(file), Charset.forName("UTF-8"))
                }],
                httpRequest: [[LinkedHashMap.class], { requestParams ->
                    new Expando(status: 200, content: "Mocked http request DONE")}],
                usernamePassword: [[Map.class], { creds ->
                    return creds
                }],
                sshUserPrivateKey: [[Map.class], { creds ->
                    return creds
                }],
                sshagent: [[List.class, Closure.class], { list, cl ->
                    cl()
                }],
                withCredentials: [[List.class, Closure.class], { list, closure ->
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
                }],
                stash: [[Map.class], null],
                unstash: [[Map.class], null]
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
            mockMethods.put(name, [args, closure])
        }
    }
}


