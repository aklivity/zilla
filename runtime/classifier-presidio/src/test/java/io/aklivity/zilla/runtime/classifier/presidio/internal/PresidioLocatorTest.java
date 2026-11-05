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
package io.aklivity.zilla.runtime.classifier.presidio.internal;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import org.agrona.concurrent.UnsafeBuffer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.config.classifier.presidio.PresidioClassifierOptionsConfig;
import io.aklivity.zilla.config.engine.ClassifierConfig;
import io.aklivity.zilla.config.engine.GenericClassifierConfig;
import io.aklivity.zilla.runtime.engine.classifier.Locator;

public class PresidioLocatorTest
{
    private static final long CONTEXT = 99L;

    private final DispatchQueue dispatch = new DispatchQueue();
    private final RecordingCompletion completion = new RecordingCompletion();
    private final RecordingSpans spans = new RecordingSpans();

    private AnalyzerStub analyzer;
    private PresidioClassifierHandler handler;
    private Locator locator;

    @Before
    public void init() throws IOException
    {
        analyzer = new AnalyzerStub();
        handler = new PresidioClassifierHandler(dispatch.engine(), classifier(PresidioClassifierOptionsConfig.builder()
            .endpoint(analyzer.endpoint())
            .label("person", "PERSON")
            .label("email", "EMAIL_ADDRESS")
            .build()));
        locator = handler.initLocator(List.of("person", "email"));
    }

    @After
    public void close()
    {
        handler.close();
        analyzer.close();
    }

    @Test
    public void shouldLocateSpanUnderLabelAlias() throws Exception
    {
        analyzer.respond(200, "[{\"end\":33,\"entity_type\":\"EMAIL_ADDRESS\",\"score\":0.99,\"start\":13}]");

        locate("reach out to jane.doe@example.com for details");

        assertThat(spans.spans(), contains("email:13:20"));
        assertThat(completion.completions(), equalTo(1));
        assertThat(completion.contextId(), equalTo(CONTEXT));
    }

    @Test
    public void shouldRequestAnalysisOfValueForLabelEntities() throws Exception
    {
        locate("reach out to jane.doe@example.com for details");

        assertThat(analyzer.requests().size(), equalTo(1));
        assertThat(analyzer.requests().get(0), containsString("\"text\":\"reach out to jane.doe@example.com for details\""));
        assertThat(analyzer.requests().get(0), containsString("\"entities\":[\"PERSON\",\"EMAIL_ADDRESS\"]"));
        assertThat(analyzer.requests().get(0), containsString("\"language\":\"en\""));
    }

    @Test
    public void shouldLocateSpansInOrder() throws Exception
    {
        analyzer.respond(200, "[{\"end\":21,\"entity_type\":\"EMAIL_ADDRESS\",\"start\":11}," +
            "{\"end\":4,\"entity_type\":\"PERSON\",\"start\":0}]");

        locate("Jane wrote jane@x.org");

        assertThat(spans.spans(), contains("person:0:4", "email:11:10"));
    }

    @Test
    public void shouldLocateSpansByUtf8ByteOffsets() throws Exception
    {
        analyzer.respond(200, "[{\"end\":12,\"entity_type\":\"PERSON\",\"start\":8}]");

        locate("héllo 😀 Jane smiled");

        assertThat(spans.spans(), contains("person:12:4"));
    }

    @Test
    public void shouldLocateSpanWithMultibyteContent() throws Exception
    {
        analyzer.respond(200, "[{\"end\":5,\"entity_type\":\"PERSON\",\"start\":0}]");

        locate("Zoë 😀 went");

        assertThat(spans.spans(), contains("person:0:9"));
    }

    @Test
    public void shouldLocateSpansRelativeToBuffer() throws Exception
    {
        byte[] value = "reach out to jane.doe@example.com".getBytes(UTF_8);
        UnsafeBuffer data = new UnsafeBuffer(new byte[value.length + 10]);
        data.putBytes(5, value);
        analyzer.respond(200, "[{\"end\":33,\"entity_type\":\"EMAIL_ADDRESS\",\"start\":13}]");

        locator.locate(1L, 2L, CONTEXT, data, 5, value.length, spans, completion);
        dispatch.runNext();

        assertThat(spans.spans(), contains("email:18:20"));
    }

    @Test
    public void shouldResolveOverlappingFindings() throws Exception
    {
        analyzer.respond(200, "[{\"end\":4,\"entity_type\":\"PERSON\",\"score\":0.9,\"start\":0}," +
            "{\"end\":8,\"entity_type\":\"PERSON\",\"score\":0.8,\"start\":0}]");

        locate("Jane Doe lives");

        assertThat(spans.spans(), contains("person:0:8"));
    }

    @Test
    public void shouldLocateNoSpansWithoutFindings() throws Exception
    {
        locate("nothing sensitive here");

        assertThat(spans.spans(), empty());
        assertThat(completion.completions(), equalTo(1));
    }

    @Test
    public void shouldIgnoreFindingForEntityNotRequested() throws Exception
    {
        analyzer.respond(200, "[{\"end\":4,\"entity_type\":\"LOCATION\",\"start\":0}]");

        locate("Rome is lovely");

        assertThat(spans.spans(), empty());
        assertThat(completion.completions(), equalTo(1));
    }

    @Test
    public void shouldNotRetainCallerBufferAfterCall() throws Exception
    {
        byte[] value = "reach out to jane.doe@example.com for details".getBytes(UTF_8);
        UnsafeBuffer data = new UnsafeBuffer(value.clone());
        CountDownLatch gate = new CountDownLatch(1);
        analyzer.hold(gate);
        analyzer.respond(200, "[{\"end\":33,\"entity_type\":\"EMAIL_ADDRESS\",\"start\":13}]");

        locator.locate(1L, 2L, CONTEXT, data, 0, value.length, spans, completion);
        data.setMemory(0, value.length, (byte) 'X');
        gate.countDown();
        dispatch.runNext();

        assertThat(analyzer.requests().get(0), containsString("reach out to jane.doe@example.com for details"));
        assertThat(spans.spans(), contains("email:13:20"));
    }

    @Test
    public void shouldCompleteEmptyValueWithoutAnalysis() throws Exception
    {
        UnsafeBuffer data = new UnsafeBuffer(new byte[0]);

        locator.locate(1L, 2L, CONTEXT, data, 0, 0, spans, completion);
        dispatch.runNext();

        assertThat(completion.completions(), equalTo(1));
        assertThat(analyzer.requests(), empty());
        assertThat(spans.spans(), empty());
    }

    @Test
    public void shouldFailOnAnalyzerErrorWithoutSpans() throws Exception
    {
        analyzer.respond(500, "boom");

        locate("reach out to jane.doe@example.com for details");

        assertThat(completion.failures(), equalTo(1));
        assertThat(completion.completions(), equalTo(0));
        assertThat(completion.failure(), instanceOf(IllegalStateException.class));
        assertThat(completion.contextId(), equalTo(CONTEXT));
        assertThat(spans.spans(), empty());
    }

    @Test
    public void shouldFailOnMalformedResponseWithoutSpans() throws Exception
    {
        analyzer.respond(200, "{\"error\":\"No text provided\"}");

        locate("reach out to jane.doe@example.com for details");

        assertThat(completion.failures(), equalTo(1));
        assertThat(completion.failure(), instanceOf(IllegalArgumentException.class));
        assertThat(spans.spans(), empty());
    }

    @Test
    public void shouldFailOnFindingBeyondValueWithoutSpans() throws Exception
    {
        analyzer.respond(200, "[{\"end\":99,\"entity_type\":\"PERSON\",\"start\":0}]");

        locate("short");

        assertThat(completion.failures(), equalTo(1));
        assertThat(completion.failure(), instanceOf(IllegalArgumentException.class));
        assertThat(spans.spans(), empty());
    }

    @Test
    public void shouldFailWhenAnalyzerUnreachable() throws Exception
    {
        analyzer.close();

        locate("reach out to jane.doe@example.com for details");

        assertThat(completion.failures(), equalTo(1));
        assertThat(spans.spans(), empty());
    }

    @Test
    public void shouldFailOnMalformedUtf8WithoutAnalysis() throws Exception
    {
        UnsafeBuffer data = new UnsafeBuffer(new byte[] {'o', 'k', (byte) 0xC3});

        locator.locate(1L, 2L, CONTEXT, data, 0, 3, spans, completion);
        dispatch.runNext();

        assertThat(completion.failures(), equalTo(1));
        assertThat(analyzer.requests(), empty());
        assertThat(spans.spans(), empty());
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectUndeclaredLabel()
    {
        handler.initLocator(List.of("PERSON"));
    }

    private void locate(
        String text) throws InterruptedException
    {
        byte[] value = text.getBytes(UTF_8);
        UnsafeBuffer data = new UnsafeBuffer(value);

        locator.locate(1L, 2L, CONTEXT, data, 0, value.length, spans, completion);
        dispatch.runNext();
    }

    private static ClassifierConfig classifier(
        PresidioClassifierOptionsConfig options)
    {
        return GenericClassifierConfig.builder()
            .namespace("test")
            .name("presidio0")
            .type("presidio")
            .options(options)
            .build();
    }
}
