package com.swedbank.jenkins.utilities

import com.swedbank.jenkins.utilities.extension.ShellExt
import com.swedbank.jenkins.utilities.utils.PipelineClassLoaderTestHelper
import spock.lang.Specification

/**
 * Shell extension specific unit tests
 */
class ShellExtensionSpec extends Specification {

    def "should be able to call extension and set handler"() {
        given:
        String handlerName = 'test1'
        Map testMap = [k1: 'v1']

        PipelineRunContext context = new PipelineRunContext(new Binding(), new PipelineClassLoaderTestHelper())
        ShellExt shellExt = new ShellExt()

        when:
        shellExt.callExt(context) {
            handler handlerName, testMap
        }

        then:
        assert shellExt.scriptHandlers.containsKey(handlerName)
        assert shellExt.scriptHandlers[handlerName] == testMap
    }
}
