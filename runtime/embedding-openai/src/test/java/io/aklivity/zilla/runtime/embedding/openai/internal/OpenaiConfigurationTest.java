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
package io.aklivity.zilla.runtime.embedding.openai.internal;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.util.Properties;

import org.junit.Test;

import io.aklivity.zilla.runtime.engine.Configuration;

public class OpenaiConfigurationTest
{
    @Test
    public void shouldVerifyConstants() throws Exception
    {
        assertThat(OpenaiConfiguration.OPENAI_ENDPOINT.name(), equalTo("zilla.embedding.openai.endpoint"));
    }

    @Test
    public void shouldReturnDefaultEndpoint()
    {
        Properties props = new Properties();
        Configuration base = new Configuration(props);
        OpenaiConfiguration config = new OpenaiConfiguration(base);

        assertThat(config.endpoint(), equalTo("https://api.openai.com/v1/embeddings"));
    }

    @Test
    public void shouldReturnConfiguredEndpoint()
    {
        Properties props = new Properties();
        props.setProperty("zilla.embedding.openai.endpoint", "http://localhost:8000/v1/embeddings");
        Configuration base = new Configuration(props);
        OpenaiConfiguration config = new OpenaiConfiguration(base);

        assertThat(config.endpoint(), equalTo("http://localhost:8000/v1/embeddings"));
    }
}
