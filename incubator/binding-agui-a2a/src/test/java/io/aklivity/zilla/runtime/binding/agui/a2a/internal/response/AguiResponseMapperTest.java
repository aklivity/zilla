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
package io.aklivity.zilla.runtime.binding.agui.a2a.internal.response;

import static io.aklivity.zilla.runtime.engine.util.Flags.FIN;
import static io.aklivity.zilla.runtime.engine.util.Flags.INIT;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

import io.aklivity.zilla.runtime.binding.agui.a2a.internal.types.stream.A2aDataExFW;

public class AguiResponseMapperTest
{
    private static final int MAXIMUM = 1024;

    private static final String SUBMITTED =
        "{\"task\":{\"id\":\"t-1\",\"contextId\":\"c-1\",\"status\":{\"state\":\"TASK_STATE_SUBMITTED\"}}}";
    private static final String COMPLETED =
        "{\"statusUpdate\":{\"taskId\":\"t-1\",\"contextId\":\"c-1\",\"status\":{\"state\":\"TASK_STATE_COMPLETED\"}}}";

    @Test
    public void shouldEmitInterruptDetailWithHandleAndExpiry()
    {
        final ResponseHarness harness = new ResponseHarness(MAXIMUM);

        harness.event(SUBMITTED, 4096);
        harness.event(status("INPUT_REQUIRED", "m-2", "Proceed?"), 4096);

        assertEquals("INTERRUPT", harness.outcome);
        assertEquals("{\"outcome\":{\"interrupts\":[{\"id\":\"h-1\",\"reason\":\"input\",\"message\":\"Proceed?\"," +
            "\"expiresAt\":\"1970-01-01T00:16:40Z\"}]}}", harness.detail);
        assertTrue(harness.mapper.terminal());
    }

    @Test
    public void shouldEmitAuthorizationInterruptWithoutMessage()
    {
        final ResponseHarness harness = new ResponseHarness(MAXIMUM);

        harness.event(status("AUTH_REQUIRED", null, null), 4096);

        assertEquals("{\"outcome\":{\"interrupts\":[{\"id\":\"h-1\",\"reason\":\"authorization\"," +
            "\"expiresAt\":\"1970-01-01T00:16:40Z\"}]}}", harness.detail);
    }

    @Test
    public void shouldEmitSnapshotAheadOfFirstReplyFrame()
    {
        final ResponseHarness harness = new ResponseHarness(MAXIMUM);
        harness.snapshot = "[{\"id\":\"msg-3\",\"role\":\"user\",\"content\":\"Hello\"}]";

        harness.event(SUBMITTED, 4096);
        harness.event(artifact("a-1", "ok", false, true), 4096);
        harness.event(COMPLETED, 4096);
        harness.mapper.streamEnded(false);

        assertEquals(List.of(
            "state(MESSAGES_SNAPSHOT)=" + harness.snapshot,
            "text(a-1,ASSISTANT)=ok|fin"), harness.logical());
        assertEquals(INIT | FIN, harness.frames.get(0).flags());
        assertEquals("SUCCESS", harness.outcome);
    }

    @Test
    public void shouldNotEmitSnapshotWhenHostHasNone()
    {
        final ResponseHarness harness = new ResponseHarness(MAXIMUM);

        harness.event(SUBMITTED, 4096);
        harness.event(artifact("a-1", "ok", false, true), 4096);

        assertEquals(List.of("text(a-1,ASSISTANT)=ok|fin"), harness.logical());
    }

    @Test
    public void shouldAbortIncompleteWhenStreamEndsWithoutTerminalState()
    {
        final ResponseHarness harness = new ResponseHarness(MAXIMUM);
        harness.snapshot = "[]";

        harness.event(SUBMITTED, 4096);
        harness.mapper.streamEnded(false);
        assertEquals(502, harness.errorStatus);

        assertNull(harness.outcome);
        assertEquals("agent_incomplete", harness.errorCode);
        assertEquals(0, harness.frames.size());
    }

    @Test
    public void shouldDeduplicateCompletedMessageAgainstStreamedChunks()
    {
        final ResponseHarness harness = new ResponseHarness(MAXIMUM);

        harness.event(SUBMITTED, 4096);
        harness.event(artifact("a-1", "Partly ", false, false), 4096);
        harness.event(artifact("a-1", "cloudy", true, true), 4096);
        harness.event(status("COMPLETED", "m-2", "Partly cloudy"), 4096);
        harness.mapper.streamEnded(false);

        assertEquals(List.of("text(a-1,ASSISTANT)=Partly cloudy|fin"), harness.logical());
        assertEquals("SUCCESS", harness.outcome);
    }

    @Test
    public void shouldGenerateIdWhenCompletedMessageIdAlreadyUsed()
    {
        final ResponseHarness harness = new ResponseHarness(MAXIMUM);

        harness.event(SUBMITTED, 4096);
        harness.event(artifact("a-1", "one", false, true), 4096);
        harness.event(status("COMPLETED", "a-1", "two"), 4096);

        final String reply = harness.logical().get(1);

        assertTrue(reply, reply.startsWith("text("));
        assertNotEquals("text(a-1,ASSISTANT)=two|fin", reply);
        assertTrue(reply, reply.endsWith(",ASSISTANT)=two|fin"));
    }

    @Test
    public void shouldGenerateIdWhenDataExHasNoMessageId()
    {
        final ResponseHarness harness = new ResponseHarness(MAXIMUM);
        final String json = "{\"message\":{\"messageId\":\"m-2\",\"role\":\"ROLE_AGENT\",\"parts\":[{\"text\":\"hi\"}]}}";

        harness.event(json, new A2aExSpec(A2aDataExFW.KIND_MESSAGE, null, null, null, null, null, null, null), 4096);
        harness.mapper.streamEnded(false);

        final String reply = harness.logical().get(0);

        assertTrue(reply, reply.startsWith("text("));
        assertNotEquals("text(m-2,ASSISTANT)=hi|fin", reply);
        assertTrue(reply, reply.endsWith(",ASSISTANT)=hi|fin"));
    }

    @Test
    public void shouldOpenNewMessageWhenAppendTargetAlreadyClosed()
    {
        final ResponseHarness harness = new ResponseHarness(MAXIMUM);

        harness.event(SUBMITTED, 4096);
        harness.event(artifact("a-1", "one", false, true), 4096);
        harness.event(artifact("a-1", "two", true, true), 4096);

        assertEquals(List.of("text(a-1,ASSISTANT)=one|fin", "text(a-1#1,ASSISTANT)=two|fin"), harness.logical());
    }

    @Test
    public void shouldCloseOpenMessageWhenAnotherArtifactStarts()
    {
        final ResponseHarness harness = new ResponseHarness(MAXIMUM);

        harness.event(SUBMITTED, 4096);
        harness.event(artifact("a-1", "one", false, false), 4096);
        harness.event(artifact("a-2", "two", false, true), 4096);

        assertEquals(List.of("text(a-1,ASSISTANT)=one|fin", "text(a-2,ASSISTANT)=two|fin"), harness.logical());
    }

    @Test
    public void shouldDiscardWorkingReplyWhenFailed()
    {
        final ResponseHarness harness = new ResponseHarness(MAXIMUM);

        harness.event(status("WORKING", "m-2", "Looking"), 4096);
        harness.event(status("FAILED", "m-3", "Broken"), 4096);
        harness.mapper.streamEnded(false);

        assertEquals(0, harness.frames.size());
        assertEquals(502, harness.errorStatus);
        assertEquals("agent_failed", harness.errorCode);
        assertEquals("Broken", harness.errorMessage);
    }

    @Test
    public void shouldAbortRejectedWithUnprocessableStatus()
    {
        final ResponseHarness harness = new ResponseHarness(MAXIMUM);

        harness.event(status("REJECTED", null, null), 4096);

        assertEquals(422, harness.errorStatus);
        assertEquals("agent_rejected", harness.errorCode);
        assertEquals("Agent declined", harness.errorMessage);
    }

    @Test
    public void shouldEmitFileAsBase64Data()
    {
        final ResponseHarness harness = new ResponseHarness(MAXIMUM);

        harness.event(SUBMITTED, 4096);
        harness.event("{\"artifactUpdate\":{\"taskId\":\"t-1\",\"contextId\":\"c-1\",\"artifact\":{\"artifactId\":\"a-1\"," +
            "\"parts\":[{\"raw\":\"aGk=\",\"mediaType\":\"text/plain\",\"filename\":\"hi.txt\"}]},\"lastChunk\":true}}", 4096);

        assertEquals(List.of("custom(agent.file)={\"mimeType\":\"text/plain\",\"name\":\"hi.txt\",\"data\":\"aGk=\"}"),
            harness.logical());
    }

    @Test
    public void shouldCloseOpenTextBeforeData()
    {
        final ResponseHarness harness = new ResponseHarness(MAXIMUM);

        harness.event(SUBMITTED, 4096);
        harness.event(artifact("a-1", "one", false, false), 4096);
        harness.event("{\"artifactUpdate\":{\"taskId\":\"t-1\",\"contextId\":\"c-1\",\"artifact\":{\"artifactId\":\"a-2\"," +
            "\"parts\":[{\"data\":{\"k\": [1, 2]}}]},\"lastChunk\":true}}", 4096);

        assertEquals(List.of("text(a-1,ASSISTANT)=one|fin", "custom(agent.data)={\"k\": [1, 2]}"), harness.logical());
    }

    @Test
    public void shouldIgnoreEventsAfterTerminal()
    {
        final ResponseHarness harness = new ResponseHarness(MAXIMUM);

        harness.event(status("INPUT_REQUIRED", "m-2", "Proceed?"), 4096);
        harness.event(artifact("a-1", "late", false, true), 4096);
        harness.mapper.streamEnded(false);

        assertEquals(0, harness.frames.size());
        assertEquals("INTERRUPT", harness.outcome);
    }

    @Test
    public void shouldAbortWhenEventMalformed()
    {
        final ResponseHarness harness = new ResponseHarness(MAXIMUM);

        harness.event("{\"task\":[}", 4096);

        assertEquals(502, harness.errorStatus);
        assertEquals("agent_invalid_response", harness.errorCode);
    }

    @Test
    public void shouldAbortWhenStatusMessageLargerThanSlot()
    {
        final ResponseHarness harness = new ResponseHarness(MAXIMUM);

        harness.event(status("COMPLETED", "m-2", "x".repeat(5 * MAXIMUM)), 700);

        assertEquals("agent_invalid_response", harness.errorCode);
        assertEquals("Event too large", harness.errorMessage);
    }

    @Test
    public void shouldIgnoreEventsWithoutKnownShape()
    {
        final ResponseHarness harness = new ResponseHarness(MAXIMUM);

        harness.event("{\"id\":\"t-1\",\"status\":{\"state\":\"TASK_STATE_CANCELED\"}}", 4096);
        harness.event(COMPLETED, 4096);
        harness.mapper.streamEnded(false);

        assertEquals(0, harness.frames.size());
        assertEquals("SUCCESS", harness.outcome);
    }

    private static String artifact(
        String artifactId,
        String text,
        boolean append,
        boolean lastChunk)
    {
        return String.format("{\"artifactUpdate\":{\"taskId\":\"t-1\",\"contextId\":\"c-1\"," +
            "\"artifact\":{\"artifactId\":\"%s\"," +
            "\"parts\":[{\"text\":\"%s\"}]},\"append\":%b,\"lastChunk\":%b}}", artifactId, text, append, lastChunk);
    }

    private static String status(
        String state,
        String messageId,
        String text)
    {
        final String message = text != null
            ? String.format(",\"message\":{\"messageId\":\"%s\",\"role\":\"ROLE_AGENT\",\"parts\":[{\"text\":\"%s\"}]}",
                messageId, text)
            : "";

        return String.format("{\"statusUpdate\":{\"taskId\":\"t-1\",\"contextId\":\"c-1\",\"status\":" +
            "{\"state\":\"TASK_STATE_%s\"%s}}}", state, message);
    }
}
