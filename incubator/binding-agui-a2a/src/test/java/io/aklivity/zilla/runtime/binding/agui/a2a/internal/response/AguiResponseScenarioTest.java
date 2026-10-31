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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class AguiResponseScenarioTest
{
    private static final int MAXIMUM = 8192;
    private static final List<String> FORBIDDEN = List.of("t-1", "c-1", "a2a", "A2a", "A2A", "taskId", "contextId");

    @Parameters(name = "{0}")
    public static Collection<String> scenarios()
    {
        return Arrays.asList(
            "run.text.chunks",
            "run.text.artifacts",
            "run.text.custom",
            "run.text.message",
            "run.text.replace",
            "run.text.task.artifacts",
            "run.send.message",
            "run.send.task",
            "run.status.completed.message",
            "run.status.completed.duplicate",
            "run.status.task.resend",
            "run.status.working.activity",
            "run.status.input.required",
            "run.outcome.aborted",
            "run.outcome.canceled",
            "run.outcome.completed",
            "run.outcome.end.error",
            "run.outcome.failed",
            "run.outcome.failed.default",
            "run.outcome.incomplete",
            "run.outcome.rejected",
            "run.outcome.rejected.default",
            "run.outcome.unspecified",
            "cancel.canceled",
            "interrupt.input.required",
            "interrupt.auth.required",
            "owner.context.reset.interrupt",
            "extension.messages.reset",
            "extension.tools.call",
            "extension.tools.call.text",
            "extension.tag.state",
            "extension.tag.activity",
            "extension.tag.reasoning",
            "extension.tag.step",
            "extension.tag.tool.call",
            "extension.tag.custom",
            "extension.tag.disabled",
            "extension.tag.echo.absent",
            "extension.tag.dropped",
            "extension.tag.malformed");
    }

    private final String scenario;

    public AguiResponseScenarioTest(
        String scenario)
    {
        this.scenario = scenario;
    }

    @Test
    public void shouldMapFramesExpectedByScript() throws IOException
    {
        final ScenarioScripts scripts = ScenarioScripts.load(scenario);
        final ResponseHarness harness = run(scripts, Integer.MAX_VALUE);

        assertEquals(scripts.expected.size(), harness.frames.size());

        for (int index = 0; index < scripts.expected.size(); index++)
        {
            final ResponseHarness.Frame expected = scripts.expected.get(index);
            final ResponseHarness.Frame actual = harness.frames.get(index);

            assertEquals("flags of frame " + index, expected.flags(), actual.flags());
            assertEquals("extension of frame " + index, expected.ex(), actual.ex());
            assertEquals("payload of frame " + index, expected.payloadText(), actual.payloadText());
        }

        if (scripts.expectedOutcome != null)
        {
            assertEquals(scripts.expectedOutcome, harness.outcome);
        }

        if (scripts.expectedDetail != null)
        {
            assertEquals(scripts.expectedDetail, harness.detail);
        }
        else
        {
            assertEquals(scripts.expectedCode, harness.errorCode);
            assertEquals(scripts.expectedMessage, harness.errorMessage);
        }
    }

    @Test
    public void shouldMapSameWhenSplitAcrossWindows() throws IOException
    {
        final ScenarioScripts scripts = ScenarioScripts.load(scenario);
        final ResponseHarness whole = run(scripts, Integer.MAX_VALUE);

        for (int window : new int[] {1, 3, 7, 64})
        {
            final ResponseHarness split = run(scripts, window);

            assertEquals("window " + window, whole.logical(), split.logical());
            assertEquals("window " + window, whole.terminal(), split.terminal());
        }
    }

    @Test
    public void shouldNotExposeA2aDetails() throws IOException
    {
        final ScenarioScripts scripts = ScenarioScripts.load(scenario);
        final ResponseHarness harness = run(scripts, Integer.MAX_VALUE);

        for (ResponseHarness.Frame frame : harness.frames)
        {
            final String exposed = new String(frame.exBytes(), UTF_8) + frame.payloadText();

            for (String forbidden : FORBIDDEN)
            {
                assertFalse(forbidden + " in " + frame, exposed.contains(forbidden));
            }
        }

        final String terminal = String.valueOf(harness.detail) + harness.errorCode + harness.errorMessage;

        for (String forbidden : FORBIDDEN)
        {
            assertFalse(forbidden + " in " + terminal, terminal.contains(forbidden));
        }
    }

    private ResponseHarness run(
        ScenarioScripts scripts,
        int window)
    {
        final ResponseHarness harness = new ResponseHarness(MAXIMUM, scripts.extension);

        final ResponseHarness.Frame first = scripts.expected.isEmpty() ? null : scripts.expected.get(0);

        if (first != null && first.ex() != null && first.ex().startsWith("state"))
        {
            harness.snapshot = first.payloadText();
        }

        for (int index = 0; index < scripts.events.size(); index++)
        {
            harness.event(scripts.events.get(index), scripts.exs.get(index), window);
        }

        if (scripts.abort)
        {
            harness.mapper.streamAborted(scripts.abortReason);
        }
        else
        {
            harness.mapper.streamEnded(scripts.error);
        }

        return harness;
    }
}
