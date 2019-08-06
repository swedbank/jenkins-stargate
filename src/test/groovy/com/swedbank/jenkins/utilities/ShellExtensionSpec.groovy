package com.swedbank.jenkins.utilities

import com.swedbank.jenkins.utilities.extension.ShellExt
import com.swedbank.jenkins.utilities.utils.PipelineClassLoaderTestHelper
import spock.lang.Specification

class ShellExtensionSpec extends Specification {

    def "should be able to call extension and set handler"() {
        given:
        PipelineRunContext context = new PipelineRunContext(new Binding(), new PipelineClassLoaderTestHelper())
        ShellExt shellExt = new ShellExt()

        when:
        shellExt.callExt(context) {
            handler 'test1', [k1:'v1']
        }

        then:
        assert shellExt.scriptHandlers.containsKey('test1')
        assert shellExt.scriptHandlers.test1 == [k1:'v1']
    }
}
