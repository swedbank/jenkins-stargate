package com.swedbank.jenkins.utilities

import com.lesfurets.jenkins.unit.BasePipelineTest

class BasePipelineClassLoaderTest extends BasePipelineTest {

    BasePipelineClassLoaderTest() {
        helper = new PipelineTestHelperClassLoader()
    }
}
