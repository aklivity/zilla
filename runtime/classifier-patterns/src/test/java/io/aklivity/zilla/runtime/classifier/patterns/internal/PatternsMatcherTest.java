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
package io.aklivity.zilla.runtime.classifier.patterns.internal;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.util.List;
import java.util.regex.Pattern;

import org.junit.Test;

public class PatternsMatcherTest
{
    private static final long STEPS = 1_000_000L;

    @Test
    public void shouldFindMatchWithOffsets()
    {
        PatternsMatcher matcher = new PatternsMatcher(0.0, List.of("key"), List.of(Pattern.compile("AKIA[0-9A-Z]{16}")));

        boolean found = matcher.find("my key AKIAIOSFODNN7EXAMPLE leaked", STEPS);

        assertThat(found, equalTo(true));
        assertThat(matcher.label(), equalTo("key"));
        assertThat(matcher.start(), equalTo(7));
        assertThat(matcher.end(), equalTo(27));
    }

    @Test
    public void shouldNotFindMissingMatch()
    {
        PatternsMatcher matcher = new PatternsMatcher(0.0, List.of("key"), List.of(Pattern.compile("AKIA[0-9A-Z]{16}")));

        assertThat(matcher.find("nothing to see here", STEPS), equalTo(false));
    }

    @Test
    public void shouldReportLabelOfFirstMatchingPattern()
    {
        PatternsMatcher matcher = new PatternsMatcher(0.0,
            List.of("first", "second"),
            List.of(Pattern.compile("alpha"), Pattern.compile("beta")));

        boolean found = matcher.find("only beta here", STEPS);

        assertThat(found, equalTo(true));
        assertThat(matcher.label(), equalTo("second"));
        assertThat(matcher.start(), equalTo(5));
        assertThat(matcher.end(), equalTo(9));
    }

    @Test
    public void shouldIgnoreMatchBelowEntropy()
    {
        PatternsMatcher matcher = new PatternsMatcher(3.0, List.of("key"), List.of(Pattern.compile("AKIA[0-9A-Z]{16}")));

        assertThat(matcher.find("my key AKIAAAAAAAAAAAAAAAAA is a placeholder", STEPS), equalTo(false));
    }

    @Test
    public void shouldAcceptMatchAtEntropy()
    {
        PatternsMatcher matcher = new PatternsMatcher(2.0, List.of("code"), List.of(Pattern.compile("[a-d]{8}")));

        assertThat(matcher.find("abcdabcd", STEPS), equalTo(true));
    }

    @Test
    public void shouldContinueToLaterCandidateAboveEntropy()
    {
        PatternsMatcher matcher = new PatternsMatcher(3.0, List.of("key"), List.of(Pattern.compile("AKIA[0-9A-Z]{16}")));

        boolean found = matcher.find("AKIAAAAAAAAAAAAAAAAA then AKIAIOSFODNN7EXAMPLE", STEPS);

        assertThat(found, equalTo(true));
        assertThat(matcher.start(), equalTo(26));
        assertThat(matcher.end(), equalTo(46));
    }

    @Test
    public void shouldIgnoreEmptyMatch()
    {
        PatternsMatcher matcher = new PatternsMatcher(0.0, List.of("any"), List.of(Pattern.compile("x*")));

        assertThat(matcher.find("abc", STEPS), equalTo(false));
    }

    @Test
    public void shouldFindMatchAfterEmptyMatch()
    {
        PatternsMatcher matcher = new PatternsMatcher(0.0, List.of("any"), List.of(Pattern.compile("x*")));

        assertThat(matcher.find("abxxc", STEPS), equalTo(true));
        assertThat(matcher.start(), equalTo(2));
        assertThat(matcher.end(), equalTo(4));
    }

    @Test
    public void shouldHonorInlineFlags()
    {
        PatternsMatcher matcher = new PatternsMatcher(0.0, List.of("greeting"), List.of(Pattern.compile("(?i)hello")));

        assertThat(matcher.find("Say HELLO", STEPS), equalTo(true));
    }

    @Test
    public void shouldFindAgainAfterPreviousFind()
    {
        PatternsMatcher matcher = new PatternsMatcher(0.0, List.of("word"), List.of(Pattern.compile("beta")));

        assertThat(matcher.find("beta", STEPS), equalTo(true));
        assertThat(matcher.find("alpha", STEPS), equalTo(false));
        assertThat(matcher.find("a beta", STEPS), equalTo(true));
        assertThat(matcher.start(), equalTo(2));
    }

    @Test(expected = IllegalStateException.class)
    public void shouldFailWhenStepsExceeded()
    {
        PatternsMatcher matcher = new PatternsMatcher(0.0, List.of("pathological"), List.of(Pattern.compile("(.*a){12}b")));

        matcher.find("a".repeat(40), STEPS);
    }

    @Test
    public void shouldLocateEveryMatchInAscendingOrder()
    {
        PatternsMatcher matcher = new PatternsMatcher(0.0,
            List.of("digits", "word"),
            List.of(Pattern.compile("[0-9]+"), Pattern.compile("[a-z]+")));

        int count = matcher.locate("ab 12 cd 345", STEPS);

        assertThat(count, equalTo(4));
        assertThat(matcher.label(0), equalTo("word"));
        assertThat(matcher.start(0), equalTo(0));
        assertThat(matcher.end(0), equalTo(2));
        assertThat(matcher.label(1), equalTo("digits"));
        assertThat(matcher.start(1), equalTo(3));
        assertThat(matcher.end(1), equalTo(5));
        assertThat(matcher.label(2), equalTo("word"));
        assertThat(matcher.start(2), equalTo(6));
        assertThat(matcher.label(3), equalTo("digits"));
        assertThat(matcher.start(3), equalTo(9));
        assertThat(matcher.end(3), equalTo(12));
    }

    @Test
    public void shouldLocateLongestOfOverlappingMatches()
    {
        PatternsMatcher matcher = new PatternsMatcher(0.0,
            List.of("short", "long"),
            List.of(Pattern.compile("ab"), Pattern.compile("abcd")));

        int count = matcher.locate("xabcdx", STEPS);

        assertThat(count, equalTo(1));
        assertThat(matcher.label(0), equalTo("long"));
        assertThat(matcher.start(0), equalTo(1));
        assertThat(matcher.end(0), equalTo(5));
    }

    @Test
    public void shouldLocateNoMatchesBelowEntropy()
    {
        PatternsMatcher matcher = new PatternsMatcher(3.0, List.of("key"), List.of(Pattern.compile("AKIA[0-9A-Z]{16}")));

        assertThat(matcher.locate("my key AKIAAAAAAAAAAAAAAAAA is a placeholder", STEPS), equalTo(0));
    }

    @Test(expected = IllegalStateException.class)
    public void shouldFailLocateWhenStepsExceeded()
    {
        PatternsMatcher matcher = new PatternsMatcher(0.0, List.of("pathological"), List.of(Pattern.compile("(.*a){12}b")));

        matcher.locate("a".repeat(40), STEPS);
    }

    @Test
    public void shouldKeepFindUnchangedAfterLocate()
    {
        PatternsMatcher matcher = new PatternsMatcher(0.0, List.of("word"), List.of(Pattern.compile("beta")));

        matcher.locate("beta beta", STEPS);

        assertThat(matcher.find("a beta", STEPS), equalTo(true));
        assertThat(matcher.start(), equalTo(2));
    }
}
