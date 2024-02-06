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
package io.aklivity.zilla.runtime.binding.asyncapi.internal.mqtt.proxy;

import static org.junit.Assert.assertEquals;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.binding.asyncapi.internal.ConfigGenerator;

public class AsyncApiMqttProxyConfigGeneratorTest
{
    @Test
    public void shouldGeneratePlainConfig() throws Exception
    {
        try (InputStream input = getClass().getResourceAsStream("plain/asyncapi.yaml"))
        {
            // GIVEN
            String expected = Files.readString(Path.of(getClass().getResource("plain/zilla.yaml").getFile()));
            //ConfigGenerator generator = new AsyncApiMqttProxyConfigGenerator(input);

            // WHEN
            //String actual = generator.generate();

            // THEN
            //assertEquals(expected, actual);
        }
    }

    @Test
    public void shouldGenerateValidatorConfig() throws Exception
    {
        try (InputStream input = getClass().getResourceAsStream("validator/asyncapi.yaml"))
        {
            // GIVEN
            String expected = Files.readString(Path.of(getClass().getResource("validator/zilla.yaml").getFile()));
            //ConfigGenerator generator = new AsyncApiMqttProxyConfigGenerator(input);

            // WHEN
            //String actual = generator.generate();

            // THEN
            //assertEquals(expected, actual);
        }
    }

    @Test
    public void shouldGenerateTlsConfig() throws Exception
    {
        try (InputStream input = getClass().getResourceAsStream("tls/asyncapi.yaml"))
        {
            // GIVEN
            String expected = Files.readString(Path.of(getClass().getResource("tls/zilla.yaml").getFile()));
            //ConfigGenerator generator = new AsyncApiMqttProxyConfigGenerator(input);

            // WHEN
            //String actual = generator.generate();

            // THEN
            //assertEquals(expected, actual);
        }
    }

    @Test
    public void shouldGenerateCompleteConfig() throws Exception
    {
        try (InputStream input = getClass().getResourceAsStream("complete/asyncapi.yaml"))
        {
            // GIVEN
            String expected = Files.readString(Path.of(getClass().getResource("complete/zilla.yaml").getFile()));
            //ConfigGenerator generator = new AsyncApiMqttProxyConfigGenerator(input);

            // WHEN
            //String actual = generator.generate();

            // THEN
            //assertEquals(expected, actual);
        }
    }
}
