package com.swedbank.jenkins.utilities

import com.swedbank.jenkins.utilities.extension.DeclarativePipelineExt
import spock.lang.Specification

class DeclarativeEnvironmentSpec extends Specification {
    PipelineTestRunner runner
    Closure baseClosure

    def setup() {
        runner = new PipelineTestRunner()
        baseClosure = {
            script getClass().getResource('/declarativePipelineEnvironment.groovy').toURI().toString()
            property "myLibrary", new Expando("getCredName": { -> return "named_creds" })
        }
    }

    def "should mock declarative environment by default"() {
        given:
        def testingEcho = []
        def scriptStep = runner.load baseClosure << {
            method "echo", [String.class], { str -> testingEcho.add(str) }
        }
        when:
        scriptStep()
        then:
        scriptStep.env.TEST_ENV_VAR == 'TEST1'
        scriptStep.env.TEST_CRED_ENV_VAR == 'MY_CREDS_user:MY_CREDS_password'
        scriptStep.env.TEST_CRED_ENV_VAR_USR == 'MY_CREDS_user'
        scriptStep.env.TEST_CRED_ENV_VAR_PSW == 'MY_CREDS_password'
        testingEcho[0] == "${scriptStep.env.TEST_ENV_VAR}"
    }

    def "should allow to define custom credentials"() {
        given:
        def scriptStep = runner.load baseClosure << {
            declarative {
                registerCredentials("MY_CREDS", DeclarativePipelineExt.CredentialsType.SECRET_TEXT)
            }

        }
        when:
        scriptStep()
        then:
        scriptStep.env.TEST_CRED_ENV_VAR == 'MY_CREDS_secret_text'
    }

    def "should allow to define custom credentials with custom handler"() {
        given:
        def scriptStep = runner.load baseClosure << {
            declarative {
                registerCredentials("MY_CREDS", DeclarativePipelineExt.CredentialsType.PRIVATE_KEY, {
                    varName -> env(varName, varName + "_custom_value")
                })
            }
        }
        when:
        scriptStep()
        then:
        scriptStep.env.TEST_CRED_ENV_VAR == 'TEST_CRED_ENV_VAR_custom_value'
    }

    def "should allow to define custom credentials with mocked name"() {
        given:
        def scriptStep = runner.load baseClosure
        when:
        scriptStep()
        then:
        scriptStep.env.TEST_ENV_VAR == "TEST1"
        scriptStep.TEST_ENV_VAR == "TEST1"
        scriptStep.env.TEST_CRED_ENV_VAR_USR == "MY_CREDS_user"
        scriptStep.TEST_CRED_ENV_VAR_USR == "MY_CREDS_user"
        scriptStep.env.TEST_CRED_ENV_VAR_PSW == "MY_CREDS_password"
        scriptStep.TEST_CRED_ENV_VAR_PSW == "MY_CREDS_password"
        scriptStep.env.TEST_CRED_ENV_VAR == "MY_CREDS_user:MY_CREDS_password"
        scriptStep.TEST_CRED_ENV_VAR == "MY_CREDS_user:MY_CREDS_password"
        scriptStep.env.TEST_CRED_MOCKED_USR == "named_creds_user"
        scriptStep.TEST_CRED_MOCKED_USR == "named_creds_user"
        scriptStep.env.TEST_CRED_MOCKED_PSW == "named_creds_password"
        scriptStep.TEST_CRED_MOCKED_PSW == "named_creds_password"
        scriptStep.env.TEST_CRED_MOCKED == "named_creds_user:named_creds_password"
        scriptStep.TEST_CRED_MOCKED == "named_creds_user:named_creds_password"
        scriptStep.env.TEST_CRED_MOCKED2 == "TEST1_REUSE"
        scriptStep.TEST_CRED_MOCKED2 == "TEST1_REUSE"
        scriptStep.env.TEST_CRED_MOCKED3 == "TEST1_REUSE2"
        scriptStep.TEST_CRED_MOCKED3 == "TEST1_REUSE2"
        scriptStep.env.TEST_CRED_MOCKED4 == scriptStep.env.BUILD_TIMESTAMP
        scriptStep.TEST_CRED_MOCKED4 == scriptStep.env.BUILD_TIMESTAMP
    }
}
