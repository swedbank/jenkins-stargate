package com.swedbank.jenkins.utilities

import com.swedbank.jenkins.utilities.extension.BaseContextExt
import com.swedbank.jenkins.utilities.extension.CommonMocksExt
import com.swedbank.jenkins.utilities.extension.DeclarativePipelineExt
import com.swedbank.jenkins.utilities.extension.ShellExt
import spock.lang.Specification

class PipelineRunContextSpec extends Specification{

    void "should be able to build default context with builder "() {
        when:
        PipelineRunContext context = new PipelineRunContext.Builder(new PipelineTestRunner())
                .setupDefaultEnv().setupDefaultExtensions().build()

        then:
        assert context != null
        context.metaClass.respondsTo(context, new CommonMocksExt().getExtName())
        context.metaClass.respondsTo(context, new DeclarativePipelineExt().getExtName())
        context.metaClass.respondsTo(context, new ShellExt().getExtName())
    }

    void "should allow to register custom extension"() {
        given:
        def extName = "testNameExtension"
        def propertyName = "customVariable"
        def propertyValue = "customValue"

        when:
        PipelineRunContext context = new PipelineRunContext.Builder(new PipelineTestRunner())
                .setupDefaultEnv()
                .addCustomExtension(new BaseContextExt() {
                    @Override
                    String getExtName() {
                        return extName
                    }

                    @Override
                    def setupExt(PipelineRunContext cnt) {
                        cnt.property(propertyName, propertyValue)
                    }
                })
                .build()

        then:
        context.metaClass.respondsTo(context, extName)
        context.binding.hasVariable(propertyName)
        context.binding.getVariable(propertyName) == propertyValue

    }

    def "should run context with runner in java style"() {
        given:
        String expectedResult = "Hello world!!."
        PipelineTestRunner runner = new PipelineTestRunner()
        PipelineRunContext context = new PipelineRunContext.Builder(runner)
                .addCustomExtension(new CommonMocksExt()).build()

        context.script(getClass().getResource('/dummyScript.groovy').toURI().toString())
        context.method("mymethod", { -> return expectedResult })

        when:
        def groovyScript = runner.loadContext(context)
        def actualResult = groovyScript.mymethod()

        then:
        assert actualResult == expectedResult
    }
}
