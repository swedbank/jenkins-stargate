package com.swedbank.jenkins.utilities

import com.swedbank.jenkins.utilities.extension.BaseContextExt
import com.swedbank.jenkins.utilities.extension.CommonMocksExt
import com.swedbank.jenkins.utilities.extension.DeclarativePipelineExt
import com.swedbank.jenkins.utilities.extension.ShellExt
import spock.lang.Specification

/**
 * Unit tests for run context.
 * Run context allows to mock jenkins environment in which we need
 * to run pipeline code.
 */
class PipelineRunContextSpec extends Specification {

    void "should be able to build default context with builder "() {
        when:
        PipelineRunContext context = new PipelineRunContext.Builder(new PipelineTestRunner())
                .setupDefaultEnv().setupDefaultExtensions().build()

        then:
        assert context != null
        context.metaClass.respondsTo(context, new CommonMocksExt().extName)
        context.metaClass.respondsTo(context, new DeclarativePipelineExt().extName)
        context.metaClass.respondsTo(context, new ShellExt().extName)
    }

    void "should allow to register custom extension"() {
        given:
        final String name = 'testNameExtension'
        final String propertyName = 'customVariable'
        final String propertyValue = 'customValue'

        when:
        PipelineRunContext context = new PipelineRunContext.Builder(new PipelineTestRunner())
                .setupDefaultEnv()
                .addCustomExtension(new BaseContextExt() {
                    String extName = name

                    @Override
                    void setupExt(PipelineRunContext cnt) {
                        cnt.property(propertyName, propertyValue)
                    }
                })
                .build()

        then:
        context.metaClass.respondsTo(context, name)
        context.binding.hasVariable(propertyName)
        context.binding.getVariable(propertyName) == propertyValue
    }

    def "should run context with runner in java style"() {
        given:
        String expectedResult = 'Hello world!!.'
        PipelineTestRunner runner = new PipelineTestRunner()
        PipelineRunContext context = new PipelineRunContext.Builder(runner)
                .addCustomExtension(new CommonMocksExt()).build()

        context.script(getClass().getResource('/dummyScript.groovy').toURI().toString())
        context.method('mymethod') { -> return expectedResult }

        when:
        Script groovyScript = runner.loadContext(context)
        String actualResult = groovyScript.mymethod()

        then:
        assert actualResult == expectedResult
    }

    def "should now allow to replace or keep env variable"() {
        given:
        PipelineTestRunner runner = new PipelineTestRunner()
        PipelineRunContext context = new PipelineRunContext.Builder(runner).build()

        String var1 = 'var1'
        String var2 = 'var2'
        String var3 = 'var3'
        String var4 = 'var4'
        String val1 = 'val1'
        String val3 = 'val3'
        String val5 = 'val5'

        when:
        context.with() {
            // first value in the env map
            env(var1, val1)
            env(var2, 'val2')
            // replace value
            env(var2, val3)
            // keep existing value
            env(var1, 'val4', false)
            // try to set null value
            env(var3, null)
            env(var4, val5, true)
        }

        then:
        Object envVar = context.binding.getVariable(PipelineRunContext.ENVIRONMENT_BLOCK_VARIABLE_NAME)
        assert envVar[var1] == val1
        assert envVar[var2] == val3
        assert envVar[var4] == val5
        assert !envVar.containsKey(var3)
    }

    def "should not allow call missing property"() {
        given:
        PipelineRunContext context = new PipelineRunContext.Builder(
                new PipelineTestRunner()).build()

        when:
        context.method('undefinedProperty', null)
        context.undefinedPorperty = 'foo'

        then:
            thrown MissingPropertyException

    }
}
