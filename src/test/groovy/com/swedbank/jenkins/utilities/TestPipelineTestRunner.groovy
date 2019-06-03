package com.swedbank.jenkins.utilities

import spock.lang.Specification

class TestPipelineTestRunner extends Specification {
    PipelineTestRunner runner
    
    def setup() {
        runner = new PipelineTestRunner()
    }
    
    def "verify script loading"() {
        when:
            def stepScript = runner.load {
                script getClass().getResource('/dummyScript.groovy').toURI().toString()
            }

        then:
            stepScript() == 'testresult'
    }

    def "verify simple script execute"() {
        when:
            runner.run {
                script getClass().getResource('/dummyScript.groovy').toURI().toString()
            }

        then:
            runner.helper.callStack.size() == 1
            runner.helper.callStack[0].target.class.canonicalName == 'dummyScript'
    }

    def "verify default env variables"() {
        when:
            def stepScript = runner.load {
                script getClass().getResource('/dummyScript.groovy').toURI().toString()
            }

        then:
            stepScript.env != null
            stepScript.env.containsKey('BUILD_TIMESTAMP')
    }

    def "verify set custom env variables"() {
        when:
            def stepScript = runner.load {
                script getClass().getResource('/dummyScript.groovy').toURI().toString()
                env += [MYENV: 'MY_VALUE']
            }

        then:
            stepScript.env != null
            stepScript.env.containsKey('BUILD_TIMESTAMP')
            stepScript.env.MYENV == 'MY_VALUE'
    }

    def "verify library load from the source"() {
        when:
            def stepScript = runner.load {
                sharedLibrary("test-lib", 'src/test/resources/')
                script getClass().getResource('/dummyScript.groovy').toURI().toString()
            }

        then:
            stepScript.varScript() == 'var script'
    }

    def "verify method mock"() {
        when:
            def stepScript = runner.load {
                script getClass().getResource('/dummyScript.groovy').toURI().toString()
                method "mockedMethod", [String.class], { str -> return "Hello, ${str}"}
            }

        then:
            stepScript.mockedMethod("dude") == 'Hello, dude'
    }

    def "verify property mock"() {
        when:
            def stepScript = runner.load {
                script getClass().getResource('/dummyScript.groovy').toURI().toString()
                property "scm", "mock for scm"
            }

        then:
            stepScript.scm == "mock for scm"
    }

    def "verify job paramter mock"() {
        when:
            def stepScript = runner.load {
                script getClass().getResource('/dummyScript.groovy').toURI().toString()
                param "user_param", "JOB_PARAMETER"
            }

        then:
            stepScript.params['user_param'] == "JOB_PARAMETER"
    }

    def "verify sh mocking with script handler"() {
        when:
            def isMockScriptCalled = false
            def stepScript = runner.load {
                sharedLibrary("test-lib", 'src/test/resources/')
                script getClass().getResource('/dummyScript.groovy').toURI().toString()
                scriptHandlers['test-sh']  = [
                                regexp: /echo 123/,
                                handler: { scriptParams -> return isMockScriptCalled = true }
                        ]
            }
        then:
            stepScript.varScript() == 'var script'
            isMockScriptCalled
    }

    def "verify library load with the script loader"() {
        when:
            runner.preferClassLoading = false
            def stepScript = runner.load {
                sharedLibrary("test-lib", 'src/test/resources/')
                script getClass().getResource('/dummyScript.groovy').toURI().toString()
            }
        then:
            stepScript.varScript() == 'var script'
            stepScript() == 'testresult'
    }

    def "should call a mocked declarative pipeline"() {
        when:
            def testingEcho = []
            def scriptStep = runner.load {
                script getClass().getResource('/declarativePipelineExample.groovy').toURI().toString()
                method "echo", [String.class], { str -> testingEcho.add(str) }
            }
        then:
            scriptStep()
            testingEcho[0] == "Hello world!"
            testingEcho[1] == "Oh, Happy days!"
    }

    def "should mock declarative environment by default"() {
        given:
        def testingEcho = []
        def scriptStep = runner.load {
            script getClass().getResource('/declarativePipelineExample.groovy').toURI().toString()
            method "echo", [String.class], { str -> testingEcho.add(str) }
        }
        when:
        scriptStep()
        then:
            scriptStep.env.TEST_ENV_VAR == 'TEST1'
            scriptStep.env.TEST_CRED_ENV_VAR == 'MY_CREDS_user:MY_CREDS_password'
            scriptStep.env.TEST_CRED_ENV_VAR_USR == 'MY_CREDS_user'
            scriptStep.env.TEST_CRED_ENV_VAR_PSW == 'MY_CREDS_password'
            testingEcho[2] == "${scriptStep.env.TEST_ENV_VAR}"
    }

    def "should allow to define custom credentials"() {
        given:
        def scriptStep = runner.load {
            script getClass().getResource('/declarativePipelineExample.groovy').toURI().toString()
            registerCredentials("MY_CREDS", PipelineTestRunner.CredentialsType.SECRET_TEXT)
        }
        when:
        scriptStep()
        then:
        scriptStep.env.TEST_CRED_ENV_VAR == 'MY_CREDS_secret_text'
    }

    def "should allow to define custom credentials with custom handler"() {
        given:
        def scriptStep = runner.load {
            script getClass().getResource('/declarativePipelineExample.groovy').toURI().toString()
            registerCredentials("MY_CREDS", PipelineTestRunner.CredentialsType.PRIVATE_KEY, {
                varName -> return varName + "_custom_value"
            })
        }
        when:
        scriptStep()
        then:
        scriptStep.env.TEST_CRED_ENV_VAR == 'TEST_CRED_ENV_VAR_custom_value'
    }
}
