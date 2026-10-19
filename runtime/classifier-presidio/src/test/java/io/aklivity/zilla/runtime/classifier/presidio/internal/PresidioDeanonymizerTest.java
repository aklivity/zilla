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
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.sameInstance;

import java.util.Set;

import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

import io.aklivity.zilla.runtime.engine.classifier.Deanonymizer.Completion;
import io.aklivity.zilla.runtime.engine.classifier.Deanonymizer.Result;
import io.aklivity.zilla.runtime.engine.classifier.Deanonymizer.Status;

public class PresidioDeanonymizerTest
{
    private static final long SCOPE = 7L;
    private static final String OPEN = "⟦";
    private static final String CLOSE = "⟧";

    private final PresidioSurrogates surrogates = new PresidioSurrogates();
    private final PresidioDeanonymizer deanonymizer = new PresidioDeanonymizer(surrogates, Set.of("person", "email"));
    private final RecordingValueConsumer sink = new RecordingValueConsumer();
    private final Result result = new Result();

    @Test
    public void shouldRestoreResolvableSurrogate()
    {
        String jane = surrogates.mint(SCOPE, "PERSON", "person", "Jane Doe").token;

        deanonymize("Dear " + jane + ", welcome", Completion.COMPLETE);

        assertThat(sink.text(), equalTo("Dear Jane Doe, welcome"));
        assertThat(sink.spans(), contains("person"));
        assertThat(result.status(), equalTo(Status.COMPLETE));
        assertThat(result.unresolved(), equalTo(0));
    }

    @Test
    public void shouldReportConsumedAndProduced()
    {
        String jane = surrogates.mint(SCOPE, "PERSON", "person", "Jane Doe").token;
        String text = "Dear " + jane + ", welcome";

        deanonymize(text, Completion.COMPLETE);

        assertThat(result.consumed(), equalTo(text.getBytes(UTF_8).length));
        assertThat(result.produced(), equalTo("Dear Jane Doe, welcome".getBytes(UTF_8).length));
    }

    @Test
    public void shouldRestoreRepeatedSurrogate()
    {
        String jane = surrogates.mint(SCOPE, "PERSON", "person", "Jane").token;

        deanonymize(jane + " and " + jane, Completion.COMPLETE);

        assertThat(sink.text(), equalTo("Jane and Jane"));
        assertThat(result.unresolved(), equalTo(0));
    }

    @Test
    public void shouldRestoreMultibyteOriginal()
    {
        String name = surrogates.mint(SCOPE, "PERSON", "person", "Zoë 😀").token;

        deanonymize("hi " + name, Completion.COMPLETE);

        assertThat(sink.text(), equalTo("hi Zoë 😀"));
    }

    @Test
    public void shouldWriteUnresolvedSurrogateThroughAndCountIt()
    {
        String unknown = OPEN + "PERSON_9:00000000" + CLOSE;

        deanonymize("Dear " + unknown + ", welcome", Completion.COMPLETE);

        assertThat(sink.text(), equalTo("Dear " + unknown + ", welcome"));
        assertThat(sink.spans(), empty());
        assertThat(result.status(), equalTo(Status.COMPLETE));
        assertThat(result.unresolved(), equalTo(1));
    }

    @Test
    public void shouldCountOnlyUnresolvedWhenMixed()
    {
        String jane = surrogates.mint(SCOPE, "PERSON", "person", "Jane").token;
        String unknown = OPEN + "PERSON_9:00000000" + CLOSE;
        String other = OPEN + "EMAIL_ADDRESS_4:00000000" + CLOSE;

        deanonymize(jane + " " + unknown + " " + other, Completion.COMPLETE);

        assertThat(sink.text(), equalTo("Jane " + unknown + " " + other));
        assertThat(result.unresolved(), equalTo(2));
    }

    @Test
    public void shouldNotRestoreSurrogateMintedInAnotherScope()
    {
        String elsewhere = surrogates.mint(SCOPE + 1, "PERSON", "person", "Mallory").token;

        deanonymize("Dear " + elsewhere, Completion.COMPLETE);

        assertThat(sink.text(), equalTo("Dear " + elsewhere));
        assertThat(result.unresolved(), equalTo(1));
    }

    @Test
    public void shouldNotRestoreSurrogateForLabelNotRequested()
    {
        String ssn = surrogates.mint(SCOPE, "US_SSN", "ssn", "123-45-6789").token;

        deanonymize("id " + ssn, Completion.COMPLETE);

        assertThat(sink.text(), equalTo("id " + ssn));
        assertThat(result.unresolved(), equalTo(1));
    }

    @Test
    public void shouldNotTreatLiteralSurrogateNameAsSurrogate()
    {
        deanonymize("PERSON_1 and EMAIL_ADDRESS_2 are plain text", Completion.COMPLETE);

        assertThat(sink.text(), equalTo("PERSON_1 and EMAIL_ADDRESS_2 are plain text"));
        assertThat(result.unresolved(), equalTo(0));
    }

    @Test
    public void shouldNotTreatMalformedTokenAsSurrogate()
    {
        String text = "a " + OPEN + "not a token" + CLOSE + " b " + OPEN + "person_1:00000000" + CLOSE;

        deanonymize(text, Completion.COMPLETE);

        assertThat(sink.text(), equalTo(text));
        assertThat(result.unresolved(), equalTo(0));
    }

    @Test
    public void shouldNotTreatOverlongTokenAsSurrogate()
    {
        String text = OPEN + "PERSON_1:" + "0".repeat(200) + CLOSE;

        deanonymize(text, Completion.COMPLETE);

        assertThat(sink.text(), equalTo(text));
        assertThat(result.unresolved(), equalTo(0));
    }

    @Test
    public void shouldNotHoldBackOverlongLabelWhenIncomplete()
    {
        String text = "a " + OPEN + "A".repeat(120);

        deanonymize(text, Completion.INCOMPLETE);

        assertThat(sink.text(), equalTo(text));
        assertThat(result.consumed(), equalTo(text.getBytes(UTF_8).length));
        assertThat(result.unresolved(), equalTo(0));
    }

    @Test
    public void shouldCompleteEmptyValue()
    {
        deanonymize("", Completion.COMPLETE);

        assertThat(result.status(), equalTo(Status.COMPLETE));
        assertThat(result.consumed(), equalTo(0));
        assertThat(result.produced(), equalTo(0));
        assertThat(result.unresolved(), equalTo(0));
    }

    @Test
    public void shouldAwaitMoreInputWhenIncompleteAndFullyConsumed()
    {
        deanonymize("plain text", Completion.INCOMPLETE);

        assertThat(sink.text(), equalTo("plain text"));
        assertThat(result.status(), equalTo(Status.UNDERFLOW));
        assertThat(result.consumed(), equalTo(10));
    }

    @Test
    public void shouldHoldBackTrailingPartialTokenWhenIncomplete()
    {
        String unknown = OPEN + "PERSON_9:00000000" + CLOSE;
        String head = "Dear ";
        String partial = unknown.substring(0, 8);

        deanonymize(head + partial, Completion.INCOMPLETE);

        assertThat(sink.text(), equalTo(head));
        assertThat(result.status(), equalTo(Status.UNDERFLOW));
        assertThat(result.consumed(), equalTo(head.length()));
        assertThat(result.unresolved(), equalTo(0));
    }

    @Test
    public void shouldCountTrailingPartialTokenWhenComplete()
    {
        String unknown = OPEN + "PERSON_9:00000000" + CLOSE;
        String partial = unknown.substring(0, 8);

        deanonymize("Dear " + partial, Completion.COMPLETE);

        assertThat(sink.text(), equalTo("Dear " + partial));
        assertThat(result.status(), equalTo(Status.COMPLETE));
        assertThat(result.unresolved(), equalTo(1));
    }

    @Test
    public void shouldHoldBackDanglingOpenerBytesWhenIncomplete()
    {
        byte[] opener = OPEN.getBytes(UTF_8);
        byte[] data = new byte[2 + 2];
        data[0] = 'o';
        data[1] = 'k';
        data[2] = opener[0];
        data[3] = opener[1];

        deanonymize(data, Completion.INCOMPLETE);

        assertThat(sink.text(), equalTo("ok"));
        assertThat(result.consumed(), equalTo(2));
        assertThat(result.unresolved(), equalTo(0));
    }

    @Test
    public void shouldWriteDanglingOpenerBytesThroughWithoutCountingWhenComplete()
    {
        byte[] opener = OPEN.getBytes(UTF_8);
        byte[] data = new byte[] {'o', 'k', opener[0], opener[1]};

        deanonymize(data, Completion.COMPLETE);

        assertThat(sink.size(), equalTo(4));
        assertThat(result.consumed(), equalTo(4));
        assertThat(result.unresolved(), equalTo(0));
    }

    @Test
    public void shouldRestoreSurrogateStraddlingFragments()
    {
        String jane = surrogates.mint(SCOPE, "PERSON", "person", "Jane").token;
        String value = "Dear " + jane + "!";
        int split = "Dear ".length() + 5;

        deanonymize(value.substring(0, split), Completion.INCOMPLETE);
        int held = result.consumed();
        String remainder = value.substring(held);
        deanonymize(remainder, Completion.COMPLETE);

        assertThat(sink.text(), equalTo("Dear Jane!"));
        assertThat(result.unresolved(), equalTo(0));
    }

    @Test
    public void shouldCountUnresolvedSurrogateStraddlingFragmentsExactlyOnce()
    {
        String unknown = OPEN + "PERSON_9:00000000" + CLOSE;
        String value = "Dear " + unknown + "!";
        int split = "Dear ".length() + 7;
        int total = 0;

        deanonymize(value.substring(0, split), Completion.INCOMPLETE);
        total += result.unresolved();
        int held = result.consumed();
        String remainder = value.substring(held);
        deanonymize(remainder, Completion.COMPLETE);
        total += result.unresolved();

        assertThat(sink.text(), equalTo("Dear " + unknown + "!"));
        assertThat(total, equalTo(1));
    }

    @Test
    public void shouldSumUnresolvedAcrossOverflowAndResume()
    {
        String first = OPEN + "PERSON_8:00000000" + CLOSE;
        String second = OPEN + "PERSON_9:00000000" + CLOSE;
        String value = first + " " + second;
        RecordingValueConsumer small = new RecordingValueConsumer(first.getBytes(UTF_8).length);
        int total = 0;

        byte[] data = value.getBytes(UTF_8);
        deanonymizer.deanonymize(1L, 2L, SCOPE, new UnsafeBuffer(data), 0, data.length, Completion.COMPLETE, small, result);
        assertThat(result.status(), equalTo(Status.OVERFLOW));
        total += result.unresolved();
        int consumed = result.consumed();

        small.drain(1000);
        byte[] rest = java.util.Arrays.copyOfRange(data, consumed, data.length);
        deanonymizer.deanonymize(1L, 2L, SCOPE, new UnsafeBuffer(rest), 0, rest.length, Completion.COMPLETE, small, result);
        total += result.unresolved();

        assertThat(small.text(), equalTo(value));
        assertThat(result.status(), equalTo(Status.COMPLETE));
        assertThat(total, equalTo(2));
    }

    @Test
    public void shouldOverflowWhenRestoredValueIsRefused()
    {
        String jane = surrogates.mint(SCOPE, "PERSON", "person", "Jane Doe").token;
        RecordingValueConsumer small = new RecordingValueConsumer(5);

        byte[] data = ("Dear " + jane).getBytes(UTF_8);
        deanonymizer.deanonymize(1L, 2L, SCOPE, new UnsafeBuffer(data), 0, data.length, Completion.COMPLETE, small, result);

        assertThat(small.text(), equalTo("Dear "));
        assertThat(result.status(), equalTo(Status.OVERFLOW));
        assertThat(result.consumed(), equalTo(5));
        assertThat(result.produced(), equalTo(5));
        assertThat(result.unresolved(), equalTo(0));
    }

    @Test
    public void shouldOverflowOnShortLiteralWrite()
    {
        RecordingValueConsumer small = new RecordingValueConsumer(3);

        byte[] data = "abcdef".getBytes(UTF_8);
        deanonymizer.deanonymize(1L, 2L, SCOPE, new UnsafeBuffer(data), 0, data.length, Completion.COMPLETE, small, result);

        assertThat(small.text(), equalTo("abc"));
        assertThat(result.status(), equalTo(Status.OVERFLOW));
        assertThat(result.consumed(), equalTo(3));
    }

    @Test
    public void shouldDeanonymizeValueAtIndex()
    {
        String jane = surrogates.mint(SCOPE, "PERSON", "person", "Jane").token;
        byte[] value = jane.getBytes(UTF_8);
        UnsafeBuffer data = new UnsafeBuffer(new byte[value.length + 9]);
        data.putBytes(4, value);

        deanonymizer.deanonymize(1L, 2L, SCOPE, data, 4, value.length, Completion.COMPLETE, sink, result);

        assertThat(sink.text(), equalTo("Jane"));
        assertThat(result.consumed(), equalTo(value.length));
    }

    @Test
    public void shouldReuseResultAcrossCalls()
    {
        Result before = result;
        String unknown = OPEN + "PERSON_9:00000000" + CLOSE;

        deanonymize(unknown, Completion.COMPLETE);
        assertThat(result.unresolved(), equalTo(1));
        deanonymize("clean", Completion.COMPLETE);

        assertThat(result, sameInstance(before));
        assertThat(result.unresolved(), equalTo(0));
    }

    private void deanonymize(
        String text,
        Completion completion)
    {
        deanonymize(text.getBytes(UTF_8), completion);
    }

    private void deanonymize(
        byte[] data,
        Completion completion)
    {
        deanonymizer.deanonymize(1L, 2L, SCOPE, new UnsafeBuffer(data), 0, data.length, completion, sink, result);
    }
}
