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
package io.aklivity.zilla.specs.metrics.http.config;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import jakarta.json.JsonObject;

import org.junit.Rule;
import org.junit.Test;

import io.aklivity.zilla.specs.engine.config.ConfigSchemaRule;

public class SchemaTest
{
    @Rule
    public final ConfigSchemaRule schema = new ConfigSchemaRule()
        .schemaPatch("io/aklivity/zilla/specs/engine/schema/binding/test.schema.patch.json")
        .schemaPatch("io/aklivity/zilla/specs/engine/schema/exporter/test.schema.patch.json")
        .schemaPatch("io/aklivity/zilla/specs/metrics/http/schema/http.schema.patch.json")
        .configurationRoot("io/aklivity/zilla/specs/metrics/http/config");

    @Test
    public void shouldValidateRequestWithContentLength()
    {
        JsonObject config = schema.validate("request.with.content.length.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateRequestWithContentLengthAttributes()
    {
        JsonObject config = schema.validate("request.with.content.length.attributes.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateResponseWithContentLength()
    {
        JsonObject config = schema.validate("response.with.content.length.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateRequestTransferEncodingChunked()
    {
        JsonObject config = schema.validate("request.transfer.encoding.chunked.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateClientSentWriteAbortOnOpenRequest()
    {
        JsonObject config = schema.validate("client.sent.write.abort.on.open.request.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateServerSentWriteAbortOnOpenResponse()
    {
        JsonObject config = schema.validate("server.sent.write.abort.on.open.response.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateClientSentReadAbortOnOpenResponse()
    {
        JsonObject config = schema.validate("client.sent.read.abort.on.open.response.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateServerSentReadAbortBeforeResponse()
    {
        JsonObject config = schema.validate("server.sent.read.abort.before.response.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateInvalidChunkedRequestNoCrlfAtEndOfChunk()
    {
        JsonObject config = schema.validate("invalid.chunked.request.no.crlf.at.end.of.chunk.yaml");

        assertThat(config, not(nullValue()));
    }
}
