/*
 * Copyright 2021-2023 Aklivity Inc
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
package io.aklivity.zilla.runtime.command.config.internal.openapi;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

public class OpenApiConfigGeneratorTest
{
    @Test
    public void shouldGenerateConfig() throws Exception
    {
        // GIVEN
        Path expectedPath = Path.of(getClass().getResource("zilla.yaml").toURI());
        String expectedResult = Files.readString(expectedPath);
        Path inputPath = Path.of(getClass().getResource("openapi.yaml").toURI());
        OpenApiConfigGenerator generator = new OpenApiConfigGenerator(inputPath);

        // WHEN
        String result = generator.generateConfig();

        // THEN
        assertThat(result, equalTo(expectedResult));
    }
}
