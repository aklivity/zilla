/*
 * Copyright 2021-2026 Aklivity Inc
 *
 * Licensed under the Aklivity Community License (the "License"); you may not use
 * this file except in compliance with the License.  You may obtain a copy of the
 * License at
 *
 *   https://www.aklivity.io/aklivity-community-license/
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */
package io.aklivity.zilla.config.engine;

class TestLeafConfigBuilder extends ConfigBuilder.Extensible<TestLeafConfig, TestLeafConfigBuilder>
{
    private String name;

    @Override
    protected Class<TestLeafConfigBuilder> thisType()
    {
        return TestLeafConfigBuilder.class;
    }

    TestLeafConfigBuilder name(
        String name)
    {
        this.name = name;
        return this;
    }

    @Override
    public TestLeafConfig build()
    {
        return new TestLeafConfig(name, extensions());
    }
}
