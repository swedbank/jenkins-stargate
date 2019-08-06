package com.swedbank.jenkins.utilities

import com.lesfurets.jenkins.unit.MethodSignature
import com.lesfurets.jenkins.unit.global.lib.SourceRetriever
import com.swedbank.jenkins.utilities.extension.BaseContextExt
import com.swedbank.jenkins.utilities.extension.CommonMocksExt
import com.swedbank.jenkins.utilities.extension.DeclarativePipelineExt
import com.swedbank.jenkins.utilities.extension.ShellExt
import com.swedbank.jenkins.utilities.utils.PipelineClassLoaderTestHelper
import groovy.util.logging.Log4j2

import java.text.SimpleDateFormat

import static com.lesfurets.jenkins.unit.global.lib.LibraryConfiguration.library

/**
 * Holds the setup information for the pipeline unit framework
 */
@Log4j2
final class PipelineRunContext {

    static class Builder {
        PipelineRunContext runContext

        Builder (PipelineTestRunner runner) {
            this(runner.binding, runner.internalHelper)
        }

        Builder(Binding binding, PipelineClassLoaderTestHelper helper) {
            this.runContext = new PipelineRunContext(binding, helper)
        }

        def setupDefaultEnv() {
            runContext.env([
                    "BUILD_TIMESTAMP": new SimpleDateFormat("MM/dd/yyyy HH:mm:ss").format(new Date())])
            return this
        }

        def setupDefaultExtensions() {
            addCustomExtension(new ShellExt())
            addCustomExtension(new CommonMocksExt())
            addCustomExtension(new DeclarativePipelineExt())
            return this
        }

        def addCustomExtension(BaseContextExt ext) {
            runContext.addExtension(ext)
            return this
        }

        def build() {
            return runContext
        }
    }

    protected Binding binding
    protected PipelineClassLoaderTestHelper helper
    protected String scriptPath

    protected  PipelineRunContext(Binding binding, PipelineClassLoaderTestHelper helper) {
        this.binding = binding
        this.helper = helper

        // Set metaClass property to ExpandoMetaClass instance, so we
        // can add dynamic methods.
        def mc = new ExpandoMetaClass(PipelineRunContext, false, true)
        mc.initialize()
        this.metaClass = mc
    }

    def addExtension(BaseContextExt ext) {
        ext.setupExt(this)
        // set two additional methods
        // 1. Same as the extension name to accept only closure
        // 2. The extension object itself to have access to internal properties.
        // #2 is not really required, but can be useful to support deprecated api
        this.metaClass."${ext.getExtName()}" = { Closure cl -> ext.callExt(this, cl) }
        this.metaClass."${ext.getExtName()}__object" = ext
    }

    def protected setMapVariable(String varName, String key, Object value, Boolean replace=false) {
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
    String script(name) {
        this.scriptPath = name
    }

    /**
     * Set environment variables
     */
    def env(String name, String value, Boolean replace=true) {
        setMapVariable('env', name, value, replace)
    }

    def env(Map<String, String> envVars, Boolean replace=false) {
        envVars.each { key, value ->
            env(key, value, replace)
        }
    }

    /**
     * Declares the script parameter. Will be accessible directly from pipeline
     */
    def property(String name, Object value) {
        this.binding.setVariable(name, value)
        log.info("New property -> ${name}: '${value}'")
    }

    /**
     * Declares the job parameter. Will be accessible through the params.<param_name>
     *     in the pipeline scripts.
     */
    def param(String name, Object value, Boolean replace=true) {
        setMapVariable('params', name, value, replace)
    }

    Boolean printStack = true

    def sharedLibrary(String name, String targetPath='.',
                      String version="master",
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

    def method(String name, List<Class> args = [], Closure closure) {
        this.method(MethodSignature.method(name,
                args.toArray(new Class[args?.size()])), closure)
    }

    def method(MethodSignature signature, Closure cl) {
        if (signature != null) {
            helper.registerAllowedMethod(signature, cl ?: { -> })
        }
    }

    // Backward compatibility with the old style calling of the library
    def propertyMissing(String name, value) {
       if (name == 'env') {
           return this.env(value)
       }

    }
    def propertyMissing(String name) {
        if (['env'].contains(name)) {
            return [:]
        } else if (name == 'scriptHandlers') {
            log.info("Deprecated! Adding new script handler. Use shellExt { handler 'name' params } instead")
            return this.shell__object.scriptHandlers
        }
    }
}