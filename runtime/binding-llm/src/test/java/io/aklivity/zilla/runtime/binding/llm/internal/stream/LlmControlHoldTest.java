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
import static org.hamcrest.Matchers.nullValue;

import org.junit.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonGeneratorEx;

public class LlmControlHoldTest
{
    private final MutableDirectBufferEx store = new UnsafeBufferEx(new byte[256]);

    @Test
    public void shouldHoldEveryMember()
    {
        final LlmControlHold hold = new LlmControlHold(() -> store);
        final int members = 100;

        for (int index = 0; index < members; index++)
        {
            assertThat(hold.begin("member" + index), is(true));
            assertThat(hold.put(buffer("1"), 0, 1), is(true));
            assertThat(hold.end(), equalTo("member" + index));
        }

        assertThat(hold.count(), equalTo(members));
        assertThat(hold.held(), equalTo(members));
        assertThat(hold.failed(), is(false));
    }

    @Test
    public void shouldHoldValueInPieces()
    {
        final LlmControlHold hold = new LlmControlHold(() -> store);

        hold.begin("note");
        hold.put(buffer("\"ab"), 0, 3);
        hold.put(buffer("cd\""), 0, 3);
        hold.end();

        final int index = hold.indexOf("note");

        assertThat(hold.length(index), equalTo(6));
        assertThat(hold.buffer().getStringWithoutLengthUtf8(hold.offset(index), hold.length(index)), equalTo("\"abcd\""));
    }

    @Test
    public void shouldFindLastMemberByName()
    {
        final LlmControlHold hold = new LlmControlHold(() -> store);

        hold.begin("usage");
        hold.put(buffer("1"), 0, 1);
        hold.end();
        hold.begin("other");
        hold.put(buffer("2"), 0, 1);
        hold.end();
        hold.begin("usage");
        hold.put(buffer("3"), 0, 1);
        hold.end();

        assertThat(hold.indexOf("usage"), equalTo(2));
        assertThat(hold.indexOf("absent"), equalTo(-1));
        assertThat(hold.name(1), equalTo("other"));
    }

    @Test
    public void shouldFailWithoutStore()
    {
        final LlmControlHold hold = new LlmControlHold(() -> null);

        assertThat(hold.begin("note"), is(false));
        assertThat(hold.failed(), is(true));
        assertThat(hold.end(), nullValue());
    }

    @Test
    public void shouldFailWhenStoreIsFull()
    {
        final LlmControlHold hold = new LlmControlHold(() -> store);

        hold.begin("note");

        assertThat(hold.put(buffer("x".repeat(256)), 0, 256), is(true));
        assertThat(hold.put(buffer("x"), 0, 1), is(false));
        assertThat(hold.failed(), is(true));
    }

    @Test
    public void shouldWriteIncludedMembers()
    {
        final LlmControlHold hold = new LlmControlHold(() -> store);

        hold.begin("a");
        hold.put(buffer("1"), 0, 1);
        hold.end();
        hold.begin("b");
        hold.put(buffer("\"two\""), 0, 5);
        hold.end();

        final JsonGeneratorEx generator = JsonEx.createGenerator();
        final MutableDirectBufferEx output = new UnsafeBufferEx(new byte[64]);

        generator.wrap(output, 0, output.capacity());
        generator.writeStartObject();
        hold.write(generator, name -> !"a".equals(name));
        generator.writeEnd();

        assertThat(output.getStringWithoutLengthUtf8(0, generator.length()), equalTo("{\"b\":\"two\"}"));
    }

    @Test
    public void shouldWriteMembersOfKind()
    {
        final LlmControlHold hold = new LlmControlHold(() -> store);

        hold.begin("a", 0);
        hold.put(buffer("1"), 0, 1);
        hold.end();
        hold.begin("b", 1);
        hold.put(buffer("2"), 0, 1);
        hold.end();

        final JsonGeneratorEx generator = JsonEx.createGenerator();
        final MutableDirectBufferEx output = new UnsafeBufferEx(new byte[64]);

        generator.wrap(output, 0, output.capacity());
        generator.writeStartObject();
        hold.write(generator, 1, name -> true);
        generator.writeEnd();

        assertThat(output.getStringWithoutLengthUtf8(0, generator.length()), equalTo("{\"b\":2}"));
    }

    @Test
    public void shouldResetForReuse()
    {
        final LlmControlHold hold = new LlmControlHold(() -> store);

        hold.begin("a");
        hold.put(buffer("1"), 0, 1);
        hold.end();
        hold.reset();

        assertThat(hold.count(), equalTo(0));
        assertThat(hold.held(), equalTo(0));
        assertThat(hold.failed(), is(false));
    }

    private static DirectBufferEx buffer(
        String text)
    {
        return new UnsafeBufferEx(text.getBytes(UTF_8));
    }
}
