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
import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.aklivity.zilla.runtime.binding.agui.a2a.internal.types.stream.A2aDataExFW;

final class ScenarioScripts
{
    private static final String ROOT = "/io/aklivity/zilla/specs/binding/agui/a2a/streams/application/";
    private static final Pattern PAYLOAD_SINGLE = Pattern.compile("^write '(.*)'$");
    private static final Pattern PAYLOAD_DOUBLE = Pattern.compile("^write \"(.*)\"$");
    private static final Pattern FLAGS = Pattern.compile("^write option zilla:flags \"(\\w+)\"$");
    private static final Pattern MESSAGE_ID = Pattern.compile("\\.messageId\\(\"([^\"]*)\"\\)");
    private static final Pattern NAME = Pattern.compile("\\.name\\(\"([^\"]*)\"\\)");
    private static final Pattern ACTIVITY_TYPE = Pattern.compile("\\.activityType\\(\"([^\"]*)\"\\)");
    private static final Pattern TYPE = Pattern.compile("\\.type\\(\"([^\"]*)\"\\)");
    private static final Pattern OUTCOME = Pattern.compile("\\.outcome\\(\"([^\"]*)\"\\)");
    private static final Pattern CODE = Pattern.compile("\\.code\\(\"([^\"]*)\"\\)");
    private static final Pattern MESSAGE = Pattern.compile("\\.message\\(\"([^\"]*)\"\\)");
    private static final Pattern REASON = Pattern.compile("\\.reason\\(\"([^\"]*)\"\\)");

    final List<String> events = new ArrayList<>();
    final List<A2aExSpec> exs = new ArrayList<>();
    final List<ResponseHarness.Frame> expected = new ArrayList<>();
    boolean error;
    boolean abort;
    String abortReason;
    String expectedOutcome;
    String expectedCode;
    String expectedMessage;

    static ScenarioScripts load(
        String scenario) throws IOException
    {
        final ScenarioScripts scripts = new ScenarioScripts();

        scripts.a2a(read("a2a", scenario));
        scripts.agui(read("agui", scenario));

        return scripts;
    }

    private void a2a(
        String script)
    {
        int start = script.indexOf(".messageStream()");
        start = start != -1 ? start : script.indexOf(".messageSend()");

        final String stream = script.substring(start);

        final String[] lines = stream.split("\n");
        String chain = null;

        for (int index = 0; index < lines.length; index++)
        {
            final String line = lines[index].trim();
            final Matcher payload = PAYLOAD_SINGLE.matcher(line);

            if (line.startsWith("write zilla:data.ext ${a2a:dataEx()"))
            {
                final StringBuilder builder = new StringBuilder(line);

                while (!lines[index].trim().endsWith(".build()}"))
                {
                    builder.append(lines[++index].trim());
                }

                chain = builder.toString();
            }
            else if (payload.matches())
            {
                events.add(payload.group(1));
                exs.add(chain != null ? a2aExOf(chain) : null);
                chain = null;
            }
        }

        abort = stream.contains("zilla:abort.ext");
        error = stream.contains("outcome(\"ERROR\")");

        final Matcher reason = REASON.matcher(stream);
        abortReason = abort && reason.find() ? reason.group(1) : null;
    }

    private void agui(
        String script)
    {
        final String[] lines = script.substring(script.indexOf("write zilla:begin.ext")).split("\n");

        int flags = INIT | FIN;
        String ex = null;
        byte[] payload = new byte[0];

        for (int index = 0; index < lines.length; index++)
        {
            final String line = lines[index].trim();
            final Matcher flagged = FLAGS.matcher(line);
            final Matcher single = PAYLOAD_SINGLE.matcher(line);
            final Matcher quoted = PAYLOAD_DOUBLE.matcher(line);

            if (flagged.matches())
            {
                flags = flagsOf(flagged.group(1));
            }
            else if (line.startsWith("write zilla:data.ext"))
            {
                final StringBuilder chain = new StringBuilder(line);

                while (!lines[index].trim().endsWith(".build()}"))
                {
                    chain.append(lines[++index].trim());
                }

                ex = exOf(chain.toString());
            }
            else if (single.matches())
            {
                payload = single.group(1).getBytes(UTF_8);
            }
            else if (quoted.matches())
            {
                payload = quoted.group(1).replace("\\\"", "\"").getBytes(UTF_8);
            }
            else if ("write zilla:data.empty".equals(line))
            {
                payload = new byte[0];
            }
            else if ("write flush".equals(line) && (ex != null || payload.length > 0 || flags != (INIT | FIN)))
            {
                expected.add(new ResponseHarness.Frame(flags, ex, null, payload));
                ex = null;
                payload = new byte[0];
            }
            else if (line.startsWith("write zilla:end.ext") || line.startsWith("write zilla:abort.ext"))
            {
                final StringBuilder chain = new StringBuilder(line);

                while (!lines[index].trim().endsWith(".build()}"))
                {
                    chain.append(lines[++index].trim());
                }

                final Matcher outcome = OUTCOME.matcher(chain);
                final Matcher code = CODE.matcher(chain);
                final Matcher message = MESSAGE.matcher(chain);

                expectedOutcome = outcome.find() ? outcome.group(1) : line.startsWith("write zilla:end.ext") ? "NONE" : null;
                expectedCode = code.find() ? code.group(1) : null;
                expectedMessage = message.find() ? message.group(1) : null;
            }
        }
    }

    private static A2aExSpec a2aExOf(
        String chain)
    {
        final int kind = chain.contains(".task()") ? A2aDataExFW.KIND_TASK :
            chain.contains(".statusUpdate()") ? A2aDataExFW.KIND_STATUS_UPDATE :
            chain.contains(".message()") ? A2aDataExFW.KIND_MESSAGE : A2aDataExFW.KIND_ARTIFACT_UPDATE;

        return new A2aExSpec(kind, member(chain, "taskId"), member(chain, "contextId"), member(chain, "state"),
            member(chain, "messageId"), member(chain, "artifactId"), flag(chain, "append"), flag(chain, "lastChunk"));
    }

    private static String member(
        String chain,
        String name)
    {
        final Matcher matcher = Pattern.compile("\\." + name + "\\(\"([^\"]*)\"\\)").matcher(chain);

        return matcher.find() ? matcher.group(1) : null;
    }

    private static Boolean flag(
        String chain,
        String name)
    {
        final Matcher matcher = Pattern.compile("\\." + name + "\\((true|false)\\)").matcher(chain);

        return matcher.find() ? Boolean.valueOf(matcher.group(1)) : null;
    }

    private static int flagsOf(
        String option)
    {
        return switch (option)
        {
        case "init" -> INIT;
        case "fin" -> FIN;
        case "none" -> 0;
        default -> INIT | FIN;
        };
    }

    private static String exOf(
        String chain)
    {
        final Matcher id = MESSAGE_ID.matcher(chain);
        final Matcher name = NAME.matcher(chain);
        final Matcher type = TYPE.matcher(chain);
        final Matcher activityType = ACTIVITY_TYPE.matcher(chain);
        String ex = null;

        if (chain.contains(".text()") && id.find())
        {
            ex = String.format("text(%s,ASSISTANT)", id.group(1));
        }
        else if (chain.contains(".custom()") && name.find())
        {
            ex = String.format("custom(%s)", name.group(1));
        }
        else if (chain.contains(".activity()") && type.find() && id.find(0) && activityType.find())
        {
            ex = String.format("activity(%s,%s,%s)", type.group(1), id.group(1), activityType.group(1));
        }
        else if (chain.contains(".state()") && type.find())
        {
            ex = String.format("state(%s)", type.group(1));
        }

        return ex;
    }

    private static String read(
        String side,
        String scenario) throws IOException
    {
        try (InputStream input = ScenarioScripts.class.getResourceAsStream(ROOT + side + "/" + scenario + "/server.rpt"))
        {
            return new String(input.readAllBytes(), UTF_8);
        }
    }
}
