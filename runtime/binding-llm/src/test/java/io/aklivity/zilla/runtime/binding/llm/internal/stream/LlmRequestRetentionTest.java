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
package io.aklivity.zilla.runtime.binding.llm.internal.stream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import java.io.File;
import java.nio.ByteBuffer;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.ExpandableArrayBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

public class LlmRequestRetentionTest
{
    private static final int KIND_DATA = 0;
    private static final int KIND_FLUSH = 1;
    private static final int KIND_END = 2;

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    private final ByteBuffer io = ByteBuffer.allocateDirect(64);
    private final UnsafeBufferEx ioBuffer = new UnsafeBufferEx(io);

    @Test
    public void shouldRetainAndReplayFromMemory() throws Exception
    {
        final LlmRetentionBudget budget = new LlmRetentionBudget(1024, 1024);
        final LlmRequestRetention retention = retention(budget, 1024, 256);

        assertThat(retention.append(KIND_DATA, 0x03, bytes("Hello"), bytes("ex")), is(true));
        assertThat(retention.append(KIND_END, 0, bytes(""), bytes("")), is(true));

        retention.rewind();
        assertThat(retention.kind(), equalTo(KIND_DATA));
        assertThat(retention.flags(), equalTo(0x03));
        assertThat(retention.payloadRemaining(), equalTo(5));
        assertThat(read(retention, 5), equalTo("Hello"));
        assertThat(extension(retention), equalTo("ex"));
        retention.advance();
        assertThat(retention.kind(), equalTo(KIND_END));
        retention.advance();
        assertThat(retention.hasRecord(), is(false));
        assertThat(budget.memoryHeld() > 0, is(true));
        assertThat(budget.diskHeld(), equalTo(0L));

        retention.close();
        assertThat(budget.memoryHeld(), equalTo(0L));
    }

    @Test
    public void shouldHaveRecordsOnlyWhileSentAgain() throws Exception
    {
        final LlmRetentionBudget budget = new LlmRetentionBudget(1024, 1024);
        final LlmRequestRetention retention = retention(budget, 1024, 256);

        retention.append(KIND_DATA, 0x03, bytes("Hello"), bytes(""));
        assertThat(retention.hasRecord(), is(false));

        retention.rewind();
        assertThat(retention.hasRecord(), is(true));

        retention.append(KIND_DATA, 0x03, bytes("World"), bytes(""));
        retention.advance();
        assertThat(retention.hasRecord(), is(true));
        retention.advance();
        assertThat(retention.hasRecord(), is(false));

        retention.append(KIND_DATA, 0x03, bytes("!"), bytes(""));
        assertThat(retention.hasRecord(), is(false));
        retention.close();
    }

    @Test
    public void shouldReplayPayloadInFragments() throws Exception
    {
        final LlmRetentionBudget budget = new LlmRetentionBudget(1024, 1024);
        final LlmRequestRetention retention = retention(budget, 1024, 256);

        retention.append(KIND_DATA, 0x03, bytes("Hello World"), bytes(""));

        retention.rewind();
        assertThat(read(retention, 4), equalTo("Hell"));
        assertThat(retention.payloadRemaining(), equalTo(7));
        assertThat(read(retention, 100), equalTo("o World"));
        assertThat(retention.payloadRemaining(), equalTo(0));
        retention.close();
    }

    @Test
    public void shouldSpillToFileWithoutDirectoryEntry() throws Exception
    {
        final LlmRetentionBudget budget = new LlmRetentionBudget(1024, 4096);
        final LlmRequestRetention retention = retention(budget, 1024, 32);

        assertThat(retention.append(KIND_DATA, 0x03, bytes("0123456789012345678901234567890123456789"), bytes("")),
            is(true));
        assertThat(retention.append(KIND_FLUSH, 0, bytes(""), bytes("fx")), is(true));

        assertThat(folder.getRoot().list().length, equalTo(0));
        assertThat(budget.memoryHeld(), equalTo(0L));
        assertThat(budget.diskHeld() > 0, is(true));

        retention.rewind();
        assertThat(read(retention, 100), equalTo("0123456789012345678901234567890123456789"));
        retention.advance();
        assertThat(retention.kind(), equalTo(KIND_FLUSH));
        assertThat(extension(retention), equalTo("fx"));

        retention.close();
        assertThat(budget.diskHeld(), equalTo(0L));
        assertThat(folder.getRoot().list().length, equalTo(0));
    }

    @Test
    public void shouldSendAllToDiskWhenMemoryThresholdIsZero() throws Exception
    {
        final LlmRetentionBudget budget = new LlmRetentionBudget(1024, 4096);
        final LlmRequestRetention retention = retention(budget, 1024, 0);

        assertThat(retention.append(KIND_DATA, 0x03, bytes("Hi"), bytes("")), is(true));

        assertThat(budget.memoryHeld(), equalTo(0L));
        assertThat(budget.diskHeld() > 0, is(true));

        retention.rewind();
        assertThat(read(retention, 10), equalTo("Hi"));
        retention.close();
    }

    @Test
    public void shouldNotRetainRequestAbovePerRequestBound() throws Exception
    {
        final LlmRetentionBudget budget = new LlmRetentionBudget(1024, 4096);
        final LlmRequestRetention retention = retention(budget, 40, 1024);

        assertThat(retention.append(KIND_DATA, 0x03, bytes("0123456789012345678901234567890123456789"), bytes("")),
            is(false));

        assertThat(budget.memoryHeld(), equalTo(0L));
        assertThat(budget.diskHeld(), equalTo(0L));
    }

    @Test
    public void shouldNotRetainRequestAboveTotalMemory() throws Exception
    {
        final LlmRetentionBudget budget = new LlmRetentionBudget(48, 4096);
        final LlmRequestRetention first = retention(budget, 1024, 1024);
        final LlmRequestRetention second = retention(budget, 1024, 1024);

        assertThat(first.append(KIND_DATA, 0x03, bytes("0123456789"), bytes("")), is(true));
        assertThat(second.append(KIND_DATA, 0x03, bytes("0123456789"), bytes("")), is(false));

        first.close();
        assertThat(budget.memoryHeld(), equalTo(0L));
    }

    @Test
    public void shouldNotRetainRequestAboveTotalDisk() throws Exception
    {
        final LlmRetentionBudget budget = new LlmRetentionBudget(1024, 40);
        final LlmRequestRetention retention = retention(budget, 1024, 0);

        assertThat(retention.append(KIND_DATA, 0x03, bytes("0123456789012345678901234567890123456789"), bytes("")),
            is(false));

        assertThat(budget.diskHeld(), equalTo(0L));
        assertThat(folder.getRoot().list().length, equalTo(0));
    }

    @Test
    public void shouldNotBoundDiskWhenUnlimited() throws Exception
    {
        final LlmRetentionBudget budget = new LlmRetentionBudget(1024, -1L);
        final LlmRequestRetention retention = retention(budget, 1024, 0);

        assertThat(retention.append(KIND_DATA, 0x03, bytes("0123456789012345678901234567890123456789"), bytes("")),
            is(true));

        retention.close();
        assertThat(budget.diskHeld(), equalTo(0L));
    }

    @Test
    public void shouldCloseIdempotently() throws Exception
    {
        final LlmRetentionBudget budget = new LlmRetentionBudget(1024, 4096);
        final LlmRequestRetention retention = retention(budget, 1024, 0);

        retention.append(KIND_DATA, 0x03, bytes("Hi"), bytes(""));
        retention.close();
        retention.close();

        assertThat(budget.diskHeld(), equalTo(0L));
    }

    private LlmRequestRetention retention(
        LlmRetentionBudget budget,
        int requestMax,
        int memoryMax)
    {
        final File directory = folder.getRoot();
        return new LlmRequestRetention(budget, directory.toPath(), requestMax, memoryMax, io, ioBuffer);
    }

    private static String read(
        LlmRequestRetention retention,
        int max)
    {
        final ExpandableArrayBufferEx buffer = new ExpandableArrayBufferEx(max);
        final int length = retention.readPayload(buffer, 0, max);
        return buffer.getStringWithoutLengthUtf8(0, length);
    }

    private static String extension(
        LlmRequestRetention retention)
    {
        final ExpandableArrayBufferEx buffer = new ExpandableArrayBufferEx(64);
        final int length = retention.readExtension(buffer, 0);
        return buffer.getStringWithoutLengthUtf8(0, length);
    }

    private static DirectBufferEx bytes(
        String value)
    {
        return new UnsafeBufferEx(value.getBytes(UTF_8));
    }
}
