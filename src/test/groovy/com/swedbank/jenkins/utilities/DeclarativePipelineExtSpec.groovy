package com.swedbank.jenkins.utilities

import com.swedbank.jenkins.utilities.extension.DeclarativePipelineExt
import com.swedbank.jenkins.utilities.utils.PipelineClassLoaderTestHelper
import spock.lang.Specification

class DeclarativePipelineExtSpec extends Specification {
    def "should be able to call extension and set credentials handlers"() {
        given:
        PipelineRunContext context = new PipelineRunContext(new Binding(), new PipelineClassLoaderTestHelper())
        DeclarativePipelineExt decExt = new DeclarativePipelineExt()

        when:
        decExt.callExt(context) {
            registerCredentials 'test1',
                                DeclarativePipelineExt.CredentialsType.USERNAME_PASSWORD,
                                { name -> }
        }

        then:
        assert decExt.credentialsTypes.containsKey('test1')
    }
}

