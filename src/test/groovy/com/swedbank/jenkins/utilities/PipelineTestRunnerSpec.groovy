package com.swedbank.jenkins.utilities

import com.swedbank.jenkins.utilities.extension.BaseContextExt
import spock.lang.Specification

/**
 * Test common usage scenarios
 */
class PipelineTestRunnerSpec extends Specification {
    private static final String TEST_LIB_PATH = 'src/test/resources/'
    private static final String TEST_LIB_NAME = 'test-lib'
    private static final String BUILD_TIMESTAMP_ENV_NAME = 'BUILD_TIMESTAMP'
    private static final String LIBRARY_SCRIPT_RESULT = 'var script'
    private static final String PIPELINE_SCRIPT_RESULT = 'testresult'
    private static final String ECHO_OUTPUT_1 = 'Hello world!'
    public static final String ECHO_METHOD_NAME = 'echo'
    private final String DECLARATIVE_SCRIPT_UNDER_TEST = getClass().getResource('/declarativePipelineExample.groovy').toURI().toString()
    private final String SCRIPT_UNDER_TEST = getClass().getResource('/dummyScript.groovy').toURI()

    PipelineTestRunner runner

    def setup() {
        runner = new PipelineTestRunner()
    }

    def "verify script loading"() {
        when:
        Script stepScript = runner.load {
            script SCRIPT_UNDER_TEST
        }

        then:
        assert stepScript() == PIPELINE_SCRIPT_RESULT
    }

    def "verify simple script execute"() {
        when:
        runner.run {
            script SCRIPT_UNDER_TEST
        }

        then:
        assert runner.helper.callStack.size() == 1
        assert runner.helper.callStack[0].target.class.canonicalName == 'dummyScript'
    }

    def "verify default env variables"() {
        when:
        Script stepScript = runner.load {
            script SCRIPT_UNDER_TEST
        }

        then:
        assert stepScript.env != null
        assert stepScript.env.containsKey(BUILD_TIMESTAMP_ENV_NAME)
    }

    def "verify set custom env variables (old style)"() {
        given:
        String myValue = 'myValue'

        when:
        Script stepScript = runner.load {
            script SCRIPT_UNDER_TEST
            env += [MYENV: myValue]
        }

        then:
        assert stepScript.env != null
        assert stepScript.env.containsKey(BUILD_TIMESTAMP_ENV_NAME)
        assert stepScript.env.MYENV == myValue
    }

    def "verify set custom env variables (new style)"() {
        given:
        String myValue1 = 'MY_VALUE1'
        String myValue2 = 'MY_VALUE2'
        String myValue3 = 'VALUE'

        when:
        Script stepScript = runner.load {
            script SCRIPT_UNDER_TEST

            env NEWENV1: myValue1,
                NEWENV2: myValue2

            env 'NAME', myValue3
        }

        then:
        assert stepScript.env != null
        assert stepScript.env.containsKey(BUILD_TIMESTAMP_ENV_NAME)
        assert stepScript.env.NEWENV1 == myValue1
        assert stepScript.env.NEWENV2 == myValue2
        assert stepScript.env.NAME == myValue3
    }

    def "verify library load from the source"() {
        when:
        Script stepScript = runner.load {
            sharedLibrary(TEST_LIB_NAME, TEST_LIB_PATH)
            script getClass().getResource('/dummyScript.groovy').toURI().toString()
        }

        then:
        assert stepScript.varScript() == LIBRARY_SCRIPT_RESULT
    }

    def "verify method mock"() {
        when:
        Script stepScript = runner.load {
            script SCRIPT_UNDER_TEST
            method('mockedMethod', [String]) { str -> return "Hello, ${str}" }
        }

        then:
        assert stepScript.mockedMethod('dude') == 'Hello, dude'
    }

    def "verify property mock"() {
        given:
        String testValue = 'mock for scm'

        when:
        Script stepScript = runner.load {
            script SCRIPT_UNDER_TEST
            property 'scm', testValue
        }

        then:
        assert stepScript.scm == testValue
    }

    def "verify job parameter mock"() {
        given:
        String testParamName = 'user_param'
        String testParamValue = 'JOB_PARAMETER'

        when:
        Script stepScript = runner.load {
            script SCRIPT_UNDER_TEST
            param testParamName, testParamValue
        }

        then:
        assert stepScript.params[testParamName] == testParamValue
    }

    def "verify sh mocking with script handler (deprecated style)"() {
        when:
        boolean isMockScriptCalled = false
        Script stepScript = runner.load {
            sharedLibrary(TEST_LIB_NAME, TEST_LIB_PATH)
            scriptHandlers['test-sh'] = [
                    regexp: /echo 123/,
                    handler: { scriptParams -> return isMockScriptCalled = true }
            ]
            // negative test:
            // declare another handler to make sure it was no called
            scriptHandlers += [
                anotherMock: [
                    regexp: /echo 321/,
                    handler: { scriptParams -> return isMockScriptCalled = false }
                ]
            ]
            script SCRIPT_UNDER_TEST

        }
        then:
        assert stepScript.varScript() == LIBRARY_SCRIPT_RESULT
        assert isMockScriptCalled
    }

    def "verify sh mocking with script handler (new style)"() {
        when:
        boolean isMockScriptCalled = false
        Script stepScript = runner.load {
            sharedLibrary(TEST_LIB_NAME, TEST_LIB_PATH)
            script SCRIPT_UNDER_TEST

            shell {
                handler 'test-sh-new', [
                        regexp: /echo 123/,
                        handler: { scriptParams -> return isMockScriptCalled = true },
                ]
            }

        }
        then:
        assert stepScript.varScript() == LIBRARY_SCRIPT_RESULT
        assert isMockScriptCalled
    }

    def "verify library load with the script loader"() {
        when:
        runner.preferClassLoading = false
        Script stepScript = runner.load {
            sharedLibrary(TEST_LIB_NAME, TEST_LIB_PATH)
            script SCRIPT_UNDER_TEST
        }
        then:
        assert stepScript.varScript() == LIBRARY_SCRIPT_RESULT
        assert stepScript() == PIPELINE_SCRIPT_RESULT
    }

    def "should call a mocked declarative pipeline"() {
        when:
        List testingEcho = []
        Script scriptStep = runner.load {
            script DECLARATIVE_SCRIPT_UNDER_TEST
            method(ECHO_METHOD_NAME, [String]) { str -> testingEcho.add(str) }
        }
        scriptStep()

        then:
        assert testingEcho[0] == ECHO_OUTPUT_1
        assert testingEcho[1] == 'Oh, Happy days!'
    }

    def "should call a mocked declarative pipeline with unsuccessful build result"() {
        when:
        Object currentResult = runner.binding.getVariable('currentBuild')
        List testingEcho = []
        def scriptStep = runner.load {
            script DECLARATIVE_SCRIPT_UNDER_TEST
            method(ECHO_METHOD_NAME, [String]) { str ->
                testingEcho.add(str)
                currentResult.result = 'FAILURE'
            }
        }
        scriptStep()

        then:
        assert testingEcho[0] == ECHO_OUTPUT_1
        assert testingEcho[1] == 'Well, not so happy days...'
    }

    def "should allow to use custom extension"() {
        given:
        boolean extWasCalled = false
        String testValue = 'testValue'

        when:
        Script stepScript = runner.load {
            addExtension(new BaseContextExt() {
                String extName = 'myExt'

                @Override
                void setupExt(PipelineRunContext cnt) {
                    cnt.property('testProp', testValue)
                }

                void doSomething() {
                    extWasCalled = true
                }
            })
            script SCRIPT_UNDER_TEST
            // call extension methods
            myExt {
                doSomething()
            }
        }

        then:
        assert stepScript.testProp == testValue
        assert extWasCalled
    }
}
