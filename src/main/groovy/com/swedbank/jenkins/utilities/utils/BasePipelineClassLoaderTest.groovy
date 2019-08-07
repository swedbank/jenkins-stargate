package com.swedbank.jenkins.utilities.utils

import com.lesfurets.jenkins.unit.BasePipelineTest

/**
 * Base pipeline test which supports loading of the script form the class path
 */
class BasePipelineClassLoaderTest extends BasePipelineTest {

    BasePipelineClassLoaderTest() {
        helper = new PipelineClassLoaderTestHelper()
    }
}
