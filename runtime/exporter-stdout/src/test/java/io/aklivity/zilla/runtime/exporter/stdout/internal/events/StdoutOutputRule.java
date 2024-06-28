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
package io.aklivity.zilla.runtime.exporter.stdout.internal.events;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.matchesPattern;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.regex.Pattern;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

public final class StdoutOutputRule implements TestRule
{
    public static final PrintStream OUT;

    private static final ByteArrayOutputStream BOS;

    static
    {
        BOS = new ByteArrayOutputStream();
        OUT = new PrintStream(BOS);
    }

    private volatile Pattern expected;

    @Override
    public Statement apply(
        Statement base,
        Description description)
    {
        return new Statement()
        {
            @Override
            public void evaluate() throws Throwable
            {
                BOS.reset();

                base.evaluate();

                while (BOS.size() == 0)
                {
                    OUT.flush();
                    Thread.onSpinWait();
                }

                final Pattern expect = Objects.requireNonNull(expected);
                final String actual = BOS.toString(StandardCharsets.UTF_8);

                assertThat(actual, matchesPattern(expect));
            }
        };
    }

    public void expect(
        Pattern expected)
    {
        this.expected = expected;
    }
}
