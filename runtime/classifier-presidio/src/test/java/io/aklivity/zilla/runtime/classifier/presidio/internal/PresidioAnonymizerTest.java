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
import static org.hamcrest.Matchers.matchesPattern;

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
import io.aklivity.zilla.runtime.engine.classifier.Anonymizer;
import io.aklivity.zilla.runtime.engine.classifier.Deanonymizer;
import io.aklivity.zilla.runtime.engine.classifier.Deanonymizer.Completion;
import io.aklivity.zilla.runtime.engine.classifier.Deanonymizer.Result;
import io.aklivity.zilla.runtime.engine.classifier.Deanonymizer.Status;

public class PresidioAnonymizerTest
{
    private static final String OPEN = "⟦";
    private static final String CLOSE = "⟧";
    private static final String NONCE = "[0-9a-f]{8}";
    private static final long SCOPE = 7L;
    private static final long CONTEXT = 99L;

    private final DispatchQueue dispatch = new DispatchQueue();
    private final RecordingCompletion completion = new RecordingCompletion();
    private final RecordingValueConsumer sink = new RecordingValueConsumer();

    private AnalyzerStub analyzer;
    private PresidioClassifierHandler handler;
    private Anonymizer anonymizer;

    @Before
    public void init() throws IOException
    {
        analyzer = new AnalyzerStub();
        handler = new PresidioClassifierHandler(dispatch.engine(), classifier(PresidioClassifierOptionsConfig.builder()
            .endpoint(analyzer.endpoint())
            .label("person", "PERSON")
            .label("email", "EMAIL_ADDRESS")
            .build()));
        anonymizer = handler.initAnonymizer(List.of("person", "email"));
    }

    @After
    public void close()
    {
        handler.close();
        analyzer.close();
    }

    @Test
    public void shouldSubstituteLocatedSpan() throws Exception
    {
        String text = "reach out to jane.doe@example.com for details";
        analyzer.respond(200, "[{\"end\":33,\"entity_type\":\"EMAIL_ADDRESS\",\"score\":0.99,\"start\":13}]");

        anonymize(text);

        assertThat(sink.text(), matchesPattern("reach out to " + token("EMAIL_ADDRESS", 1) + " for details"));
        assertThat(completion.completions(), equalTo(1));
        assertThat(completion.consumed(), equalTo(text.getBytes(UTF_8).length));
        assertThat(completion.contextId(), equalTo(CONTEXT));
    }

    @Test
    public void shouldWriteSubstitutionUnderLabelAlias() throws Exception
    {
        analyzer.respond(200, "[{\"end\":33,\"entity_type\":\"EMAIL_ADDRESS\",\"score\":0.99,\"start\":13}]");

        anonymize("reach out to jane.doe@example.com for details");

        assertThat(sink.spans(), contains("email"));
    }

    @Test
    public void shouldRequestAnalysisOfValueForLabelEntities() throws Exception
    {
        anonymize("reach out to jane.doe@example.com for details");

        assertThat(analyzer.requests().size(), equalTo(1));
        assertThat(analyzer.requests().get(0), containsString("\"text\":\"reach out to jane.doe@example.com for details\""));
        assertThat(analyzer.requests().get(0), containsString("\"entities\":[\"PERSON\",\"EMAIL_ADDRESS\"]"));
        assertThat(analyzer.requests().get(0), containsString("\"language\":\"en\""));
    }

    @Test
    public void shouldReuseSurrogateForRepeatedOriginal() throws Exception
    {
        analyzer.respond(200, "[{\"end\":6,\"entity_type\":\"EMAIL_ADDRESS\",\"start\":0}," +
            "{\"end\":17,\"entity_type\":\"EMAIL_ADDRESS\",\"start\":11}]");

        anonymize("a@b.co and a@b.co");

        assertThat(sink.text(), matchesPattern(token("EMAIL_ADDRESS", 1) + " and " + token("EMAIL_ADDRESS", 1)));
        assertThat(sink.text().split(" and ")[0], equalTo(sink.text().split(" and ")[1]));
    }

    @Test
    public void shouldSubstituteDistinctSpansWithDistinctSurrogates() throws Exception
    {
        analyzer.respond(200, "[{\"end\":4,\"entity_type\":\"PERSON\",\"start\":0}," +
            "{\"end\":21,\"entity_type\":\"EMAIL_ADDRESS\",\"start\":11}]");

        anonymize("Jane wrote jane@x.org");

        assertThat(sink.text(), matchesPattern(token("PERSON", 1) + " wrote " + token("EMAIL_ADDRESS", 1)));
        assertThat(sink.spans(), contains("person", "email"));
    }

    @Test
    public void shouldLocateSpansByCodePointOffsets() throws Exception
    {
        String text = "héllo 😀 Jane smiled";
        analyzer.respond(200, "[{\"end\":12,\"entity_type\":\"PERSON\",\"start\":8}]");

        anonymize(text);

        assertThat(sink.text(), matchesPattern("héllo 😀 " + token("PERSON", 1) + " smiled"));
        assertThat(completion.consumed(), equalTo(text.getBytes(UTF_8).length));
    }

    @Test
    public void shouldPassValueThroughWithoutFindings() throws Exception
    {
        String text = "nothing sensitive here";

        anonymize(text);

        assertThat(sink.text(), equalTo(text));
        assertThat(sink.spans(), empty());
        assertThat(completion.consumed(), equalTo(text.getBytes(UTF_8).length));
    }

    @Test
    public void shouldSubstituteValueAtIndex() throws Exception
    {
        byte[] value = "reach out to jane.doe@example.com".getBytes(UTF_8);
        UnsafeBuffer data = new UnsafeBuffer(new byte[value.length + 10]);
        data.putBytes(5, value);
        analyzer.respond(200, "[{\"end\":33,\"entity_type\":\"EMAIL_ADDRESS\",\"start\":13}]");

        anonymizer.anonymize(1L, 2L, SCOPE, CONTEXT, data, 5, value.length, sink, completion);
        dispatch.runNext();

        assertThat(sink.text(), matchesPattern("reach out to " + token("EMAIL_ADDRESS", 1)));
        assertThat(completion.consumed(), equalTo(value.length));
    }

    @Test
    public void shouldResolveOverlappingFindings() throws Exception
    {
        analyzer.respond(200, "[{\"end\":4,\"entity_type\":\"PERSON\",\"score\":0.9,\"start\":0}," +
            "{\"end\":8,\"entity_type\":\"PERSON\",\"score\":0.8,\"start\":0}]");

        anonymize("Jane Doe lives");

        assertThat(sink.text(), matchesPattern(token("PERSON", 1) + " lives"));
        assertThat(sink.spans(), contains("person"));
    }

    @Test
    public void shouldNotRetainCallerBufferAfterCall() throws Exception
    {
        byte[] value = "reach out to jane.doe@example.com for details".getBytes(UTF_8);
        UnsafeBuffer data = new UnsafeBuffer(value.clone());
        CountDownLatch gate = new CountDownLatch(1);
        analyzer.hold(gate);
        analyzer.respond(200, "[{\"end\":33,\"entity_type\":\"EMAIL_ADDRESS\",\"start\":13}]");

        anonymizer.anonymize(1L, 2L, SCOPE, CONTEXT, data, 0, value.length, sink, completion);
        data.setMemory(0, value.length, (byte) 'X');
        gate.countDown();
        dispatch.runNext();

        assertThat(sink.text(), matchesPattern("reach out to " + token("EMAIL_ADDRESS", 1) + " for details"));
    }

    @Test
    public void shouldCompleteEmptyValueWithoutAnalysis() throws Exception
    {
        UnsafeBuffer data = new UnsafeBuffer(new byte[0]);

        anonymizer.anonymize(1L, 2L, SCOPE, CONTEXT, data, 0, 0, sink, completion);
        dispatch.runNext();

        assertThat(completion.completions(), equalTo(1));
        assertThat(completion.consumed(), equalTo(0));
        assertThat(analyzer.requests(), empty());
        assertThat(sink.size(), equalTo(0));
    }

    @Test
    public void shouldFailOnAnalyzerErrorWithoutWriting() throws Exception
    {
        analyzer.respond(500, "boom");

        anonymize("reach out to jane.doe@example.com for details");

        assertThat(completion.failures(), equalTo(1));
        assertThat(completion.completions(), equalTo(0));
        assertThat(completion.failure(), instanceOf(IllegalStateException.class));
        assertThat(completion.contextId(), equalTo(CONTEXT));
        assertThat(sink.size(), equalTo(0));
    }

    @Test
    public void shouldFailOnMalformedResponseWithoutWriting() throws Exception
    {
        analyzer.respond(200, "{\"error\":\"No text provided\"}");

        anonymize("reach out to jane.doe@example.com for details");

        assertThat(completion.failures(), equalTo(1));
        assertThat(completion.failure(), instanceOf(IllegalArgumentException.class));
        assertThat(sink.size(), equalTo(0));
    }

    @Test
    public void shouldFailOnFindingBeyondValueWithoutWriting() throws Exception
    {
        analyzer.respond(200, "[{\"end\":99,\"entity_type\":\"PERSON\",\"start\":0}]");

        anonymize("short");

        assertThat(completion.failures(), equalTo(1));
        assertThat(completion.failure(), instanceOf(IllegalArgumentException.class));
        assertThat(sink.size(), equalTo(0));
    }

    @Test
    public void shouldFailWhenAnalyzerUnreachable() throws Exception
    {
        analyzer.close();

        anonymize("reach out to jane.doe@example.com for details");

        assertThat(completion.failures(), equalTo(1));
        assertThat(sink.size(), equalTo(0));
    }

    @Test
    public void shouldStopBeforeSubstitutionRefusedByConsumer() throws Exception
    {
        RecordingValueConsumer small = new RecordingValueConsumer(13);
        analyzer.respond(200, "[{\"end\":33,\"entity_type\":\"EMAIL_ADDRESS\",\"start\":13}]");

        anonymize("reach out to jane.doe@example.com for details", small);

        assertThat(small.text(), equalTo("reach out to "));
        assertThat(completion.consumed(), equalTo(13));
        assertThat(completion.completions(), equalTo(1));
    }

    @Test
    public void shouldStopAtShortWrite() throws Exception
    {
        RecordingValueConsumer small = new RecordingValueConsumer(5);
        analyzer.respond(200, "[{\"end\":33,\"entity_type\":\"EMAIL_ADDRESS\",\"start\":13}]");

        anonymize("reach out to jane.doe@example.com for details", small);

        assertThat(small.text(), equalTo("reach"));
        assertThat(completion.consumed(), equalTo(5));
    }

    @Test
    public void shouldIgnoreFindingForEntityNotRequested() throws Exception
    {
        analyzer.respond(200, "[{\"end\":4,\"entity_type\":\"LOCATION\",\"start\":0}]");

        anonymize("Rome is lovely");

        assertThat(sink.text(), equalTo("Rome is lovely"));
        assertThat(sink.spans(), empty());
    }

    @Test
    public void shouldFailOnMalformedUtf8WithoutAnalysis() throws Exception
    {
        UnsafeBuffer data = new UnsafeBuffer(new byte[] {'o', 'k', (byte) 0xC3});

        anonymizer.anonymize(1L, 2L, SCOPE, CONTEXT, data, 0, 3, sink, completion);
        dispatch.runNext();

        assertThat(completion.failures(), equalTo(1));
        assertThat(analyzer.requests(), empty());
        assertThat(sink.size(), equalTo(0));
    }

    @Test
    public void shouldRestoreWhatWasSubstitutedInSameScope() throws Exception
    {
        String original = "reach out to jane.doe@example.com for details";
        analyzer.respond(200, "[{\"end\":33,\"entity_type\":\"EMAIL_ADDRESS\",\"start\":13}]");
        anonymize(original);

        RecordingValueConsumer restored = new RecordingValueConsumer();
        Result result = deanonymize(SCOPE, sink.text(), restored);

        assertThat(restored.text(), equalTo(original));
        assertThat(result.status(), equalTo(Status.COMPLETE));
        assertThat(result.unresolved(), equalTo(0));
    }

    @Test
    public void shouldNotRestoreWhatWasSubstitutedInAnotherScope() throws Exception
    {
        analyzer.respond(200, "[{\"end\":33,\"entity_type\":\"EMAIL_ADDRESS\",\"start\":13}]");
        anonymize("reach out to jane.doe@example.com for details");

        RecordingValueConsumer restored = new RecordingValueConsumer();
        Result result = deanonymize(SCOPE + 1, sink.text(), restored);

        assertThat(restored.text(), equalTo(sink.text()));
        assertThat(result.unresolved(), equalTo(1));
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectUndeclaredLabel()
    {
        handler.initAnonymizer(List.of("PERSON"));
    }

    private void anonymize(
        String text) throws InterruptedException
    {
        anonymize(text, sink);
    }

    private void anonymize(
        String text,
        RecordingValueConsumer consumer) throws InterruptedException
    {
        byte[] value = text.getBytes(UTF_8);
        UnsafeBuffer data = new UnsafeBuffer(value);

        anonymizer.anonymize(1L, 2L, SCOPE, CONTEXT, data, 0, value.length, consumer, completion);
        dispatch.runNext();
    }

    private Result deanonymize(
        long scopeId,
        String text,
        RecordingValueConsumer consumer)
    {
        Deanonymizer deanonymizer = handler.initDeanonymizer(List.of("person", "email"));
        byte[] value = text.getBytes(UTF_8);

        return deanonymizer.deanonymize(1L, 2L, scopeId, new UnsafeBuffer(value), 0, value.length, Completion.COMPLETE,
            consumer, new Result());
    }

    private static String token(
        String entity,
        int ordinal)
    {
        return OPEN + entity + "_" + ordinal + ":" + NONCE + CLOSE;
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
