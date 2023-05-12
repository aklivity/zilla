/*
 * Copyright 2021-2023 Aklivity Inc.
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
package io.aklivity.zilla.build.maven.plugins.flyweight.internal;

import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

public class GenerateMojoTest
{
    @Rule
    public GenerateMojoRule generator = new GenerateMojoRule()
            .packageName("io.aklivity.zilla.build.maven.plugins.flyweight.internal.test.types")
            .inputDirectory("src/test/resources/test-project")
            .outputDirectory("target/generated-test-sources/test-aklivity");

    public GenerateMojoTest() throws Exception
    {
    }

    @Test
    // Regenerate from test.idl so it's included in code coverage report
    public void shouldGenerateTestIdl()
        throws Exception
    {
        generator.scopeNames("test")
            .generate();
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldNotGenerateInvalidIntArrayLengthHasDefault()
        throws Exception
    {
        generator.scopeNames("invalidIntArrayLengthHasDefault")
            .generate();
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldNotGenerateInvalidIntArrayLengthNotUnsigned()
        throws Exception
    {
        generator.scopeNames("invalidIntArrayLengthNotUnsigned")
            .generate();
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldNotGenerateInvalidIntArrayWithDefaultLengthNotSigned()
        throws Exception
    {
        generator.scopeNames("invalidIntArrayWithDefaultLengthNotSigned")
            .generate();
    }

    @Test(expected = ParseCancellationException.class)
    public void shouldNotGenerateInvalidStructOctetsNotLast()
        throws Exception
    {
        generator.scopeNames("invalidOctetsNotLast")
            .generate();
    }

    @Test(expected = ParseCancellationException.class)
    @Ignore("TODO: validate this in the grammar by defining unbounded_struct_type")
    public void shouldNotGenerateInvalidStructOctetsNotLastNested()
        throws Exception
    {
        generator.scopeNames("invalidOctetsNotLastNested")
                 .generate();
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldNotGenerateUnrecognizedType()
        throws Exception
    {
        generator.scopeNames("invalidUnrecognizedType")
            .generate();
    }

}
