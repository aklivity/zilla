/*
 * Copyright 2021-2024 Aklivity Inc
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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

public final class StdoutOutputRule implements TestRule
{
    public static final PrintStream OUT;

    private static final PipedInputStream IN;

    static
    {
        try
        {
            IN = new PipedInputStream();
            OUT = new PrintStream(new PipedOutputStream(IN));
        }
        catch (IOException ex)
        {
            throw new RuntimeException(ex);
        }
    }

    private volatile List<Pattern> expectedPatterns;

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
                base.evaluate();

                try (BufferedReader in = new BufferedReader(new InputStreamReader(IN)))
                {
                    for (Pattern expected : expectedPatterns)
                    {
                        String actual = in.readLine();
                        assertThat(actual, matchesPattern(expected));
                    }
                }
            }
        };
    }

    public void expect(
        Pattern... expected)
    {
        this.expectedPatterns = Arrays.asList(expected);
    }
}
