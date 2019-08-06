package com.swedbank.jenkins.utilities.utils

import com.lesfurets.jenkins.unit.BasePipelineTest

class BasePipelineClassLoaderTest extends BasePipelineTest {

    BasePipelineClassLoaderTest() {
        helper = new PipelineClassLoaderTestHelper()
    }
}
