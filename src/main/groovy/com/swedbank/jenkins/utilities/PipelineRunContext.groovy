package com.swedbank.jenkins.utilities

import static com.lesfurets.jenkins.unit.global.lib.LibraryConfiguration.library

import com.lesfurets.jenkins.unit.MethodSignature
import com.lesfurets.jenkins.unit.global.lib.SourceRetriever
import com.swedbank.jenkins.utilities.extension.BaseContextExt
import com.swedbank.jenkins.utilities.extension.CommonMocksExt
import com.swedbank.jenkins.utilities.extension.DeclarativePipelineExt
import com.swedbank.jenkins.utilities.extension.ShellExt
import com.swedbank.jenkins.utilities.utils.PipelineClassLoaderTestHelper
import groovy.util.logging.Log4j2

import java.text.SimpleDateFormat

/**
 * Holds the setup information for the pipeline unit framework
 */
@Log4j2
final class PipelineRunContext {

    public static final String ENVIRONMENT_BLOCK_VARIABLE_NAME = 'env'
    public static final String SCRIPT_HANDLERS = 'scriptHandlers'
    private static final String SHELL_DEPRECATION_MESSAGE = 'Deprecated! Adding new script handler. ' +
            "Use shell { handler 'name' params } instead"

    static class Builder {
        PipelineRunContext runContext

        Builder (PipelineTestRunner runner) {
            this(runner.binding, runner.internalHelper)
        }

        Builder(Binding binding, PipelineClassLoaderTestHelper helper) {
            this.runContext = new PipelineRunContext(binding, helper)
        }

        Builder setupDefaultEnv() {
            runContext.env([
                    'BUILD_TIMESTAMP': new SimpleDateFormat(
                            'yyyy-MM-dd HH:mm:ss', Locale.US).format(new Date())])
            runContext.property('scm', [:])
            runContext.property('params', [:])
            return this
        }

        Builder setupDefaultExtensions() {
            addCustomExtension(new ShellExt())
            addCustomExtension(new CommonMocksExt())
            addCustomExtension(new DeclarativePipelineExt())
            return this
        }

        Builder addCustomExtension(BaseContextExt ext) {
            runContext.addExtension(ext)
            return this
        }

        PipelineRunContext build() {
            return runContext
        }
    }

    Binding binding
    PipelineClassLoaderTestHelper helper
    String scriptPath

    private PipelineRunContext(Binding binding, PipelineClassLoaderTestHelper helper) {
        this.binding = binding
        this.helper = helper

        // Set metaClass property to ExpandoMetaClass instance, so we
        // can add dynamic methods.
        ExpandoMetaClass mc = new ExpandoMetaClass(PipelineRunContext, false, true)
        mc.initialize()
        this.metaClass = mc
    }

    void addExtension(BaseContextExt ext) {
        ext.setupExt(this)
        // set two additional methods
        // 1. Same as the extension name to accept only closure
        // 2. The extension object itself to have access to internal properties.
        // #2 is not really required, but can be useful to support deprecated api
        this.metaClass."${ext.extName}" = { Closure cl -> ext.callExt(this, cl) }
        this.metaClass."${ext.extName}__object" = ext
    }

    private void setMapVariable(String varName, String key, Object value, Boolean replace=true) {
        Map currentVar = [:]
        if (binding.hasVariable(varName)) {
            currentVar = binding.getVariable((varName)) as Map
        }

        if ((value != null) && (currentVar[key] == null || replace)) {
            currentVar[key] = value
            binding.setVariable(varName, currentVar)
            log.info("New var -> ${varName}['${key}'] = '${value}'")
        }
    }

    /**
     * Set a groovy script name. Required property
     */
    String script(String name) {
        this.scriptPath = name
    }

    /**
     * Set environment variables
     */
    void env(String name, String value, Boolean replace=true) {
        setMapVariable(ENVIRONMENT_BLOCK_VARIABLE_NAME, name, value, replace)
    }

    void env(Map<String, String> envVars, Boolean replace=true) {
        envVars.each { key, value ->
            env(key, value, replace)
        }
    }

    /**
     * Declares the script parameter. Will be accessible directly from pipeline
     */
    void property(String name, Object value) {
        this.binding.setVariable(name, value)
        log.info("New property -> ${name}: '${value}'")
    }

    /**
     * Declares the job parameter. Will be accessible through the params.<param_name>
     *     in the pipeline scripts.
     */
    void param(String name, Object value, Boolean replace=true) {
        setMapVariable('params', name, value, replace)
    }

    Boolean printStack = true

    void sharedLibrary(String name, String targetPath='.',
                      String version='master',
                      Boolean allowOverride=true,
                      Boolean implicit=true,
                      SourceRetriever retriever=null) {

        if (retriever == null) {
            retriever = new ProjectSource(targetPath)
        }

        this.helper.registerSharedLibrary(library().name(name)
                .defaultVersion(version)
                .allowOverride(allowOverride)
                .implicit(implicit)
                .targetPath(targetPath)
                .retriever(retriever)
                .build())
        log.info("New library -> ${name}: [targetPath: '${targetPath}']")
    }

    void method(String name, List<Class> args = [], Closure closure) {
        this.method(MethodSignature.method(name,
                args.toArray(new Class[args?.size()])), closure)
    }

    void method(MethodSignature signature, Closure cl) {
        log.info("Method mock -> ${signature}")
        helper.registerAllowedMethod(signature, cl ?: { -> })
    }

    // Backward compatibility with the old style calling of the library
    Object propertyMissing(String name, Object value) {
        if (name == ENVIRONMENT_BLOCK_VARIABLE_NAME) {
            return this.env(value)
        } else if (name == SCRIPT_HANDLERS) {
            log.warn(SHELL_DEPRECATION_MESSAGE)
            return this.shell__object.handlers(value)
        }
        throw new MissingPropertyException(name)
    }
    Object propertyMissing(String name) {
        if ([ENVIRONMENT_BLOCK_VARIABLE_NAME].contains(name)) {
            return [:]
        } else if (name == SCRIPT_HANDLERS) {
            log.warn(SHELL_DEPRECATION_MESSAGE)
            return this.shell__object.scriptHandlers
        }
        throw new MissingPropertyException(name)
    }
}
