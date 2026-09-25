/*
 * Copyright 2021-2026 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.build.maven.plugins.zpm.internal;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThrows;

import java.util.Map;

import org.junit.Test;

public class ZpmTemplateTest
{
    @Test
    public void shouldSubstituteProperties()
    {
        String config = ZpmTemplate.substitute(
            "[\"io.aklivity.zilla:runtime:${VERSION}\", \"${EXT}\"]",
            Map.of("VERSION", "1.2.3", "EXT", "engine-ext"));

        assertThat(config, equalTo("[\"io.aklivity.zilla:runtime:1.2.3\", \"engine-ext\"]"));
    }

    @Test
    public void shouldRejectUndefinedProperty()
    {
        assertThrows(IllegalArgumentException.class, () -> ZpmTemplate.substitute("${VERSION}", Map.of()));
    }

    @Test
    public void shouldExtractDependencies()
    {
        String config = """
            {
              "repositories": [ "https://repo.maven.apache.org/maven2/" ],
              "imports": [ "io.aklivity.zilla:runtime:1.0" ],
              "dependencies":
              [
                "io.aklivity.zilla:engine",
                "org.slf4j:slf4j-simple:2.0.17"
              ]
            }
            """;

        assertThat(ZpmTemplate.dependencies(config), contains("io.aklivity.zilla:engine", "org.slf4j:slf4j-simple"));
    }

    @Test
    public void shouldExtractNoDependencies()
    {
        assertThat(ZpmTemplate.dependencies("{ \"repositories\": [] }"), empty());
    }
}
