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

import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmToolTransformRunner.assertRejects;
import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmToolTransformRunner.assertTransforms;
import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmToolTransformRunner.json;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import org.junit.Test;

import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;

public class LlmSharedMemberTest
{
    private final ToShared decoder = new ToShared();
    private final FromShared client = new FromShared("foo");
    private final FromShared foreign = new FromShared("bar");
    private final FromShared sibling = new FromShared("foo/b");

    @Test
    public void shouldEmitSharedMemberInPlaceOfDialectMember()
    {
        assertTransforms(decoder, json("{'alpha_native':7}"), json("{'x:alpha':7}"));
    }

    @Test
    public void shouldKeepDialectPrefixOnMemberThatIsNotShared()
    {
        assertTransforms(decoder, json("{'other':7}"), json("{'foo:other':7}"));
    }

    @Test
    public void shouldEmitSharedAndDialectMembersTogether()
    {
        assertTransforms(decoder,
            json("{'other':1,'alpha_native':2,'beta_native':{'a':[1,{'b':'c'}]}}"),
            json("{'foo:other':1,'x:alpha':2,'x:beta':{'a':[1,{'b':'c'}]}}"));
    }

    @Test
    public void shouldRestoreSharedMemberInNativeForm()
    {
        assertTransforms(client, json("{'x:alpha':7}"), json("{'alpha_native':7}"));
    }

    @Test
    public void shouldRestoreVendorPrefixedMemberInEveryVariantOfVendor()
    {
        assertTransforms(sibling, json("{'foo:other':7}"), json("{'other':7}"));
        assertTransforms(client, json("{'foo:other':7}"), json("{'other':7}"));
    }

    @Test
    public void shouldRestoreVariantPrefixedMemberOnlyInThatVariant()
    {
        assertTransforms(sibling, json("{'foo/b:other':7}"), json("{'other':7}"));
        assertTransforms(client, json("{'foo/b:other':7}"), json("{}"));
        assertTransforms(foreign, json("{'foo/b:other':7}"), json("{}"));
    }

    @Test
    public void shouldDropVendorPrefixedMemberAcrossVendors()
    {
        assertTransforms(foreign, json("{'foo:other':7}"), json("{}"));
    }

    @Test
    public void shouldRestoreSharedMemberAcrossDialects()
    {
        assertTransforms(foreign, json("{'x:alpha':7}"), json("{'alpha_native':7}"));
    }

    @Test
    public void shouldDropSharedMemberThatTheClientDoesNotKnow()
    {
        assertTransforms(client, json("{'x:unknown':7}"), json("{}"));
    }

    @Test
    public void shouldDropSharedMemberHoldingAnObject()
    {
        assertTransforms(client, json("{'x:unknown':{'a':[1,{'b':'c'}]}}"), json("{}"));
    }

    @Test
    public void shouldRejectSharedMemberThatChangesBehaviour()
    {
        assertRejects(client, json("{'x:beta':1}"));

        assertThat(client.rejectedMember(), equalTo("beta"));
    }

    @Test
    public void shouldClearRejectedMemberOnReset()
    {
        assertRejects(client, json("{'x:beta':1}"));

        client.reset();

        assertThat(client.rejectedMember(), nullValue());
    }

    @Test
    public void shouldKeepOrDropEachKeyIndependently()
    {
        assertTransforms(client,
            json("{'foo:a':1,'x:alpha':2,'bar:b':3,'x:unknown':4,'plain':5,'x:gamma':6}"),
            json("{'a':1,'alpha_native':2,'plain':5}"));
    }

    @Test
    public void shouldDropOwnPrefixedMemberAcrossDialectsButKeepSharedMember()
    {
        assertTransforms(foreign,
            json("{'foo:a':1,'x:alpha':2}"),
            json("{'alpha_native':2}"));
    }

    @Test
    public void shouldNotTreatPrefixThatStartsWithXAsShared()
    {
        assertTransforms(client, json("{'xx:alpha':1,'x':2}"), json("{'x':2}"));
    }

    private static final class ToShared extends LlmToolTransform
    {
        @Override
        protected int member(
            int depth,
            CharSequence key)
        {
            return matches(key, "alpha_native") ? share("alpha")
                : matches(key, "beta_native") ? share("beta")
                : prefix("foo", key);
        }

        @Override
        protected void onOpen()
        {
        }

        @Override
        protected Status onClose(
            int depth)
        {
            pass(JsonEvent.END_OBJECT);

            return Status.ADVANCED;
        }
    }

    private static final class FromShared extends LlmToolTransform
    {
        FromShared(
            String dialect)
        {
            super(MODE_OBJECT, dialect);
        }

        @Override
        protected int member(
            int depth,
            CharSequence key)
        {
            return unprefix(key);
        }

        @Override
        protected int shared(
            CharSequence member)
        {
            final int action;

            if (matches(member, "alpha"))
            {
                target = "alpha_native";
                action = PASS_VALUE;
            }
            else if (matches(member, "beta"))
            {
                action = reject(member);
            }
            else
            {
                action = DROP;
            }

            return action;
        }

        @Override
        protected void onOpen()
        {
        }

        @Override
        protected Status onClose(
            int depth)
        {
            pass(JsonEvent.END_OBJECT);

            return Status.ADVANCED;
        }
    }
}
