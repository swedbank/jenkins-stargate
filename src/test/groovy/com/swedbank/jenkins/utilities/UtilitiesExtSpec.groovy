package com.swedbank.jenkins.utilities

import com.lesfurets.jenkins.unit.MethodSignature
import com.swedbank.jenkins.utilities.extension.UtilitiesExt
import org.junit.Test
import spock.lang.Specification

class UtilitiesExtSpec extends Specification {
    public static final String VALIRATION_TEST_SCRIPT = '/validateParametersExample.groovy'

    @Test void 'should_mock_validation_manager'() {
        given:
        UtilitiesExt utilExt = new UtilitiesExt()
        PipelineRunContext context = new PipelineRunContext.Builder(new PipelineTestRunner())
                .addCustomExtension(utilExt)
                .build()

        when:
        utilExt.callExt(context) { }

        then:
        assert context.helper.hasRegisteredMethod(
                MethodSignature.method(UtilitiesExt.VALIDATE_PARAMETERS, Map, List))
        assert context.helper.hasRegisteredMethod(
                MethodSignature.method(UtilitiesExt.VALIDATION_RULE, Map))
    }

    @Test void 'should_be_able_to_remove_mock'() {
        given:
        UtilitiesExt utilExt = new UtilitiesExt()
        PipelineRunContext context = new PipelineRunContext.Builder(new PipelineTestRunner())
                .addCustomExtension(utilExt)
                .build()

        when:
        utilExt.callExt(context) {
            disableValidationManagerMocks()
        }

        then:
        assert !context.helper.hasRegisteredMethod(
                MethodSignature.method(UtilitiesExt.VALIDATE_PARAMETERS, Map, List))
        assert !context.helper.hasRegisteredMethod(
                MethodSignature.method(UtilitiesExt.VALIDATION_RULE, List))
    }

    @Test void 'should_be_to_use_validation_rules_from_vars'() {
        given:
        PipelineTestRunner runner = new PipelineTestRunner()

        when:
        Script validationTestScript = runner.load {
            script getClass().getResource(VALIRATION_TEST_SCRIPT).toURI().toString()
        }

        then:
        assert validationTestScript()

    }

    @Test void 'should_disable_validation_mocks_when_requested'() {
        given:
        PipelineTestRunner runner = new PipelineTestRunner()
        Script validationTestScript = runner.load {
            script getClass().getResource(VALIRATION_TEST_SCRIPT).toURI().toString()
            utilities {
                disableValidationManagerMocks()
            }
        }

        when:
        validationTestScript()

        then:
        MissingMethodException ex = thrown()
        assert ex.message.contains(UtilitiesExt.VALIDATION_RULE)
    }
}
