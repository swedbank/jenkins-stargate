package com.swedbank.jenkins.utilities.extension

import com.swedbank.jenkins.utilities.PipelineRunContext

/**
 * Mocks any methods calls to the object
 */
class ValidationRuleMock {

    String[] callStack = []

    Object methodMissing(String name, Object args) {
        callStack += name
        return new ValidationRuleMock()
    }
}

/**
 * Holds specific mocks for the jenkins-shared-utilities
 */
class UtilitiesExt extends BaseContextExt {
    public static final String VALIDATE_PARAMETERS = 'validateParameters'
    public static final String VALIDATION_RULE = 'validationRule'
    final String extName = 'utilities'

    PipelineRunContext context

    @Override
    void setupExt(PipelineRunContext cnt) {
        context = cnt
        cnt.with {
            method(VALIDATE_PARAMETERS, [Map, List]) { map, list -> return true }
            method(VALIDATION_RULE, [String]) { paramName -> return new ValidationRuleMock() }
        }
    }

    void disableValidationManagerMocks() {
        if (this.context != null) {
            this.context.helper.unRegisterAllowedMethod(VALIDATE_PARAMETERS, [Map, List])
            this.context.helper.unRegisterAllowedMethod(VALIDATION_RULE,  [String])
        }
    }
}
