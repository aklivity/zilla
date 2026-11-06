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
package io.aklivity.zilla.runtime.binding.a2a.internal.dialect;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import java.util.List;

import org.junit.Test;

public class A2aDialectsTest
{
    private final A2aDialects all = new A2aDialects(null);
    private final A2aDialects v10 = new A2aDialects(List.of("1.0"));
    private final A2aDialects v03 = new A2aDialects(List.of("0.3"));

    @Test
    public void shouldSelectDialectByExplicitVersion()
    {
        assertThat(all.resolve("1.0", "message/send").version(), equalTo("1.0"));
        assertThat(all.resolve("0.3", "SendMessage").version(), equalTo("0.3"));
        assertThat(all.resolve("1.0.0", null).version(), equalTo("1.0"));
        assertThat(all.resolve("0.3.0", null).version(), equalTo("0.3"));
    }

    @Test
    public void shouldRejectUnknownVersion()
    {
        assertThat(all.resolve("2.0", "SendMessage"), nullValue());
        assertThat(all.resolveCard("2.0"), nullValue());
    }

    @Test
    public void shouldDefaultToLegacyWhenVersionAbsent()
    {
        assertThat(all.resolve(null, "message/send").version(), equalTo("0.3"));
        assertThat(all.resolve("", "tasks/get").version(), equalTo("0.3"));
    }

    @Test
    public void shouldSelectCurrentWhenVersionAbsentAndMethodIsCurrent()
    {
        assertThat(all.resolve(null, "SendMessage").version(), equalTo("1.0"));
        assertThat(all.resolve(" ", "ListTasks").version(), equalTo("1.0"));
    }

    @Test
    public void shouldFallBackWhenMethodUnknown()
    {
        assertThat(all.resolve(null, "Unknown").version(), equalTo("0.3"));
        assertThat(v10.resolve(null, "Unknown").version(), equalTo("1.0"));
        assertThat(new A2aDialects(List.of()).resolve(null, "Unknown").version(), equalTo("0.3"));
    }

    @Test
    public void shouldLockDownVersions()
    {
        assertThat(v10.resolve("0.3", "message/send"), nullValue());
        assertThat(v10.resolve(null, "message/send"), nullValue());
        assertThat(v10.resolve(null, "SendMessage").version(), equalTo("1.0"));
        assertThat(v03.resolve("1.0", "SendMessage"), nullValue());
        assertThat(v03.resolve(null, "SendMessage"), nullValue());
        assertThat(v03.resolve(null, "message/send").version(), equalTo("0.3"));
    }

    @Test
    public void shouldResolveCardDialect()
    {
        assertThat(all.resolveCard(null).version(), equalTo("1.0"));
        assertThat(all.resolveCard("0.3").version(), equalTo("0.3"));
        assertThat(v03.resolveCard(null).version(), equalTo("0.3"));
        assertThat(v10.resolveCard("0.3"), nullValue());
        assertThat(v10.legacy(), nullValue());
        assertThat(all.legacy().version(), equalTo("0.3"));
    }
}
