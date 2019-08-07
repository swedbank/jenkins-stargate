package com.swedbank.jenkins.utilities

import com.swedbank.jenkins.utilities.extension.DeclarativePipelineExt
import spock.lang.Specification

/**
 * Unit tests for the environment block in declarative pipelines
 */
class DeclarativeEnvironmentSpec extends Specification {
    private static final String TEST_CREDENTIALS_ID = 'MY_CREDS'

    PipelineTestRunner runner
    Closure baseClosure

    def setup() {
        runner = new PipelineTestRunner()
        baseClosure = {
            script getClass().getResource('/declarativePipelineEnvironment.groovy').toURI().toString()
            property 'myLibrary', new Expando('getCredName': { -> return 'named_creds' })
        }
    }

    def "should mock declarative environment by default"() {
        given:
        def testingEcho = []
        def scriptStep = runner.load baseClosure << {
            method 'echo', [String], { str -> testingEcho.add(str) }
        }
        when:
        scriptStep()
        then:
        assert scriptStep.env.TEST_ENV_VAR == 'TEST1'
        assert scriptStep.env.TEST_CRED_ENV_VAR == 'MY_CREDS_user:MY_CREDS_password'
        assert scriptStep.env.TEST_CRED_ENV_VAR_USR == 'MY_CREDS_user'
        assert scriptStep.env.TEST_CRED_ENV_VAR_PSW == 'MY_CREDS_password'
        assert testingEcho[0] == "${scriptStep.env.TEST_ENV_VAR}"
    }

    def "should allow to define custom credentials"() {
        given:
        def scriptStep = runner.load baseClosure << {
            declarative {
                registerCredentials(TEST_CREDENTIALS_ID, DeclarativePipelineExt.CredentialsType.SECRET_TEXT)
            }

        }
        when:
        scriptStep()
        then:
        assert scriptStep.env.TEST_CRED_ENV_VAR == 'MY_CREDS_secret_text'
    }

    def "should allow to define custom private key credentials"() {
        given:
        def scriptStep = runner.load baseClosure << {
            declarative {
                registerCredentials(TEST_CREDENTIALS_ID, DeclarativePipelineExt.CredentialsType.PRIVATE_KEY)
            }

        }
        when:
        scriptStep()
        then:
        assert scriptStep.env.TEST_CRED_ENV_VAR == 'MY_CREDS_private_file'
    }

    def "should allow to define custom secret key credentials"() {
        given:
        def scriptStep = runner.load baseClosure << {
            declarative {
                registerCredentials(TEST_CREDENTIALS_ID, DeclarativePipelineExt.CredentialsType.SECRET_FILE)
            }

        }
        when:
        scriptStep()
        then:
        assert scriptStep.env.TEST_CRED_ENV_VAR == 'MY_CREDS_secret_file'
    }

    def "should allow to define custom credentials with custom handler"() {
        given:
        def scriptStep = runner.load baseClosure << {
            declarative {
                registerCredentials(TEST_CREDENTIALS_ID, DeclarativePipelineExt.CredentialsType.PRIVATE_KEY) {
                    varName -> env(varName, varName + '_custom_value')
                }
            }
        }
        when:
        scriptStep()
        then:
        assert scriptStep.env.TEST_CRED_ENV_VAR == 'TEST_CRED_ENV_VAR_custom_value'
    }

    def "should allow to define custom credentials with mocked name"() {
        given:
        def scriptStep = runner.load baseClosure
        when:
        scriptStep()
        then:
        // make sure we have all kind of variable defined.
        assert scriptStep.env.TEST_ENV_VAR == "TEST1"
        assert scriptStep.TEST_ENV_VAR == "TEST1"
        assert scriptStep.env.TEST_CRED_ENV_VAR_USR == "MY_CREDS_user"
        assert scriptStep.TEST_CRED_ENV_VAR_USR == "MY_CREDS_user"
        assert scriptStep.env.TEST_CRED_ENV_VAR_PSW == "MY_CREDS_password"
        assert scriptStep.TEST_CRED_ENV_VAR_PSW == "MY_CREDS_password"
        assert scriptStep.env.TEST_CRED_ENV_VAR == "MY_CREDS_user:MY_CREDS_password"
        assert scriptStep.TEST_CRED_ENV_VAR == "MY_CREDS_user:MY_CREDS_password"
        assert scriptStep.env.TEST_CRED_MOCKED_USR == "named_creds_user"
        assert scriptStep.TEST_CRED_MOCKED_USR == "named_creds_user"
        assert scriptStep.env.TEST_CRED_MOCKED_PSW == "named_creds_password"
        assert scriptStep.TEST_CRED_MOCKED_PSW == "named_creds_password"
        assert scriptStep.env.TEST_CRED_MOCKED == "named_creds_user:named_creds_password"
        assert scriptStep.TEST_CRED_MOCKED == "named_creds_user:named_creds_password"
        assert scriptStep.env.TEST_CRED_MOCKED2 == "TEST1_REUSE"
        assert  scriptStep.TEST_CRED_MOCKED2 == "TEST1_REUSE"
        assert scriptStep.env.TEST_CRED_MOCKED3 == "TEST1_REUSE2"
        assert scriptStep.TEST_CRED_MOCKED3 == "TEST1_REUSE2"
        assert scriptStep.env.TEST_CRED_MOCKED4 == scriptStep.env.BUILD_TIMESTAMP
        assert scriptStep.TEST_CRED_MOCKED4 == scriptStep.env.BUILD_TIMESTAMP
    }
}
