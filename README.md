# Jenkins-Stargate

Jenkins stargate is a pipeline unit testing library based on the [JenkinsPipelineUnit](https://github.com/jenkinsci/JenkinsPipelineUnit) test framework.
It incorporates some of the work from [macg33zr/pipelineUnit](https://github.com/macg33zr/pipelineUnit).
It provides a nice groovy DSL and some additional extensions to make pipeline testing easier.

This library allows to:
 - load and execute JenkinsFile's with the scripted or declarative pipelines
 - load and execute shared library groovy files
 - load jenkins shared libraries (marked with the @Library annotation)
 - mock jenkins specific variables, functions and jenkins plugins methods

## Still pending "official" release

Work is still in progress to publish this library to jcenter. We just need to sort through some stuff concerning bintray
and our github account before we set up automated publishing.

Until then you are free to build this library yourself for testing purposes. Be warned, however, there maybe some
changes with git tags/version and potentially even commit history as we streamline this process with our internal development

Prior to open-sourcing, this library was named jenkins-pipeline-test.

## Installation

Add library dependency in the ``build.gradle`` file:

    dependencies {
        testImplementation 'tools.swedbank:jenkins-pipeline-test:v2.3.1'
    }


Make sure your pipeline source file are in the main sourceSet. See [Coverage](#coverage) section for details.

## Usage

### Script loading and running

The ``Jenkins-Pipeline-Test`` library allows to load or execute JenkinsFile or shared library groovy script.

To load the script use:

    PipelineTestRunner runner = new PipelineTestRunner()
    def stepScript = runner.load {
        script "/path/to/jenkins/script"
    }
    assert stepScript() == 'testresult'

To load and run the script:

    PipelineTestRunner runner = new PipelineTestRunner()
    def testResult = runner.run {
        script "/path/to/jenkins/script"
    }
    assert testResult == 'testresult'


### Mocking

You can mock environment variables, build properties, build parameters and jenkins methods:

    def testResult = PipelineTestRunner().run {
        script "/path/to/jenkins/script"

        // mock environment variable (will appear inside the 'env' map)
        env 'GOPATH', '/home/ciuser/go/'
        // mock property
        property 'csm', [:]
        // mock job parameters (will apper inside the 'param' map)
        param 'JOB_PARAM1', 'foo'

        // mock method (method name, input parameters, closure to call instead)
        method 'emailSend', [String, String], { email, body -> "mock for sneding emails"}
        method 'checkout', [Map], { parameters -> "mocking external dependencies" },
    }

All those mocking functions (``env``, ``property``, ``param`` and ``method``) can be called more the one inside the run/load closure.

By default the ``Jenkins-Pipeline-Test`` library will mock many of the jenkins default methods for you, like
``node, sh, pipeline, stage, sshagent, withCredentials`` etc.
See the ``\extension\*.groovy`` files for the complete list.


### Mocking shell scripts

The ``Jenkins-Pipeline-Test`` library provides an extension``shellExt`` which can be used to
mock specific shell scripts inside you jenkins libraries or Jenkinsfile's.

Suppose somewhere in your pipeline code you have:

    node {
        ...
        env.GIT_TAG = sh(script: "git tag", returnStdout: true)
        ...
    }

To avoid real calls to git you can mock that specific shell call by providing regexp and custom handler:

     def testResult = PipelineTestRunner().run {
         script "/path/to/pipleine"

          shell {
              handler 'mock_name', [
                  regexp : /git tag/,
                  handler: { scriptParams -> return "v1.1.1" }
              ]
          }
     }

### Jenkins Shared Libraries

If you use jenkins shared libraries (annotated with ``@Library``), you can pre-load them in your test:

    def testResult = PipelineTestRunner().run {
        sharedLibrary("test-lib", 'src/test/resources/')
        script "/path/to/pipleine"
    }

If you are testing jenkins shared library code, and your ``/vars/*.groovy`` files depend on each other,
you can load library from current folder without specifying second argument (targetPath):

    def testResult = PipelineTestRunner().run {
        sharedLibrary("test-lib")
        script "/vars/myLibFunction.groovy"
    }

### Declarative pipeline

The ``Jenkins-Pipeline-Test``library mocks for you most of the declarative pipeline methods.
So for most cases you don't need to provide extra mocks for your tests.

 The ``Jenkins-Pipeline-Test``library will also mock ``environment`` block and the
 ``credentials`` functions. By default all the credentials are treated as username/password.

To change credentials type, you can use ``declerative`` extension:

    def testResult = PipelineTestRunner().run {
         script "/paht/to/pipeline"
         declarative {
             registerCredentials("MY_CREDS_ID", DeclarativePipelineExt.CredentialsType.SECRET_TEXT)
         }
    }

This will change credential type to the ``SECRET_TEXT`` for the credentials with the ``MY_CREDS_ID`` id.

You can also define custom handler for the credentials. Handler accepts the variable name to which credentials should be assigned:

    def testResult = PipelineTestRunner().run {
         script "/paht/to/pipeline"
         declarative {
             registerCredentials("MY_CREDS", DeclarativePipelineExt.CredentialsType.PRIVATE_KEY, {
                 // set custom environment variable name
                 varName -> env(varName, varName + "_custom_value")
             })
         }
    }


### Custom extensions

Extensions allows extend context closure with user code. This allows to reuse code and
add nested DSL closures to the context closure. Check this example:

    def stepScript = PipelineTestRunner().load {
            addExtension(new BaseContextExt() {
                // provide extension name
                String extName = "myExt"

                // this method will be called first, once extension is added
                @Override
                void setupExt(PipelineRunContext cnt) {
                    cnt.property('testProp', 'testValue')
                }

                def doSomething() { }

                def modifyContext(PipelineRunContext cnt) {
                    cnt.env('my_new_env', 'test')
                }
            })

            // load script now
            script '/path/to/pipeline'

            // call new extension methods
            myExt {
                doSomething()
            }

            // you can also call extension with context
            myExt { context ->
                modifyContext(context)
            }
        }
    }

See more examples in the test folder (``.\src\test\``).

### Coverage

By default the ``Jenkins-Pipeline-Test`` library requires to have tests and library file in the ``sourceSets.{main test}.groovy.srcDirs``:

        sourceSets {
            main {
                groovy {
                    srcDirs = ['src', 'vars']
                }
            }
            test {
                groovy {
                    srcDirs = ['test/groovy']
                }
            }
        }

This required to correctly calculate coverage data for you pipelines. If you don't want to have ``vars`` treated as source files, you
can disable coverage by tweaking runner:

    PipelineTestRunner runner = new PipelineTestRunner()
    runner.preferClassLoading = false

    def stepScript = runner.load {
        script 'vars/myLibraryScript.groovy'
    }
