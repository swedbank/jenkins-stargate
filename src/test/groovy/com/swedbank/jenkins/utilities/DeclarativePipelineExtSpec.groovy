package com.swedbank.jenkins.utilities

import com.swedbank.jenkins.utilities.extension.DeclarativePipelineExt
import com.swedbank.jenkins.utilities.utils.PipelineClassLoaderTestHelper
import spock.lang.Specification

/**
 * Unit tests for the declarative pipeline
 */
class DeclarativePipelineExtSpec extends Specification {
    def "should be able to call extension and set credentials handlers"() {
        given:
        def credName = 'test1'
        PipelineRunContext context = new PipelineRunContext(new Binding(), new PipelineClassLoaderTestHelper())
        DeclarativePipelineExt decExt = new DeclarativePipelineExt()

        when:
        decExt.callExt(context) {
            registerCredentials credName,
                                DeclarativePipelineExt.CredentialsType.USERNAME_PASSWORD,
                                { name -> }
        }

        then:
        assert decExt.credentialsTypes.containsKey(credName)
    }
}

