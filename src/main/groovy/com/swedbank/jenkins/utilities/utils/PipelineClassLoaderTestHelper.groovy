package com.swedbank.jenkins.utilities.utils

import static com.lesfurets.jenkins.unit.MethodSignature.method

import com.lesfurets.jenkins.unit.InterceptingGCL
import com.lesfurets.jenkins.unit.MethodSignature
import com.lesfurets.jenkins.unit.PipelineTestHelper
import org.apache.commons.io.FilenameUtils
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.runtime.InvokerHelper

class PipelineClassLoaderTestHelper extends PipelineTestHelper {

    Object runScript(String scriptName, Binding binding) {
        return runScript(scriptName, binding, true)
    }

    Object runScript(String scriptName, Binding binding, Boolean preferClassLoad) {
        return runScriptInternal(loadScript(scriptName, binding, preferClassLoad))
    }

    @Override
    Script loadScript(String scriptName, Binding binding) {
        return loadScript(scriptName, binding, true)
    }

    Script loadScript(String scriptName, Binding binding, Boolean preferClassLoad) {
        Objects.requireNonNull(binding, "Binding cannot be null.")
        Objects.requireNonNull(gse, "GroovyScriptEngine is not initialized: Initialize the helper by calling init().")

        def script;
        if (preferClassLoad) {
            // trigger library loading
            gse.loadScriptByName(scriptName)

            // load compiled class
            CompilerConfiguration configuration = new CompilerConfiguration()
            GroovyClassLoader classLoader = new InterceptingGCL(this, baseClassloader, configuration)
            def clazz = classLoader.loadClass(FilenameUtils.getBaseName(scriptName), true, false, false)
            setGlobalVars(binding)
            script = InvokerHelper.createScript(clazz, binding)
        } else {
            Class scriptClass = gse.loadScriptByName(scriptName)
            setGlobalVars(binding)
            script = InvokerHelper.createScript(scriptClass, binding)
        }
        script.metaClass.invokeMethod = getMethodInterceptor()
        script.metaClass.static.invokeMethod = getMethodInterceptor()
        script.metaClass.methodMissing = getMethodMissingInterceptor()
        return script
    }

    void unRegisterAllowedMethod(String name, List<Class> args) {
        allowedMethodCallbacks.remove(method(name, args.toArray(new Class[args.size()])))
    }

    boolean hasRegisteredMethod(MethodSignature methodSignature) {
        return allowedMethodCallbacks.containsKey(methodSignature)
    }
}
