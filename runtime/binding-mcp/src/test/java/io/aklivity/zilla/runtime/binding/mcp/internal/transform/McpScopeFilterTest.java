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
package io.aklivity.zilla.runtime.binding.mcp.internal.transform;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;

import org.junit.Ignore;
import org.junit.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonGeneratorEx;
import io.aklivity.zilla.runtime.common.json.JsonPipeline;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;
import io.aklivity.zilla.runtime.common.json.JsonSink;

public class McpScopeFilterTest
{
    private static final BiPredicate<CharSequence, List<String>> ADMIT_ALL = (name, scopes) -> true;

    @Test
    public void shouldArmOnArrayKeyThatFragmentsAcrossInputWindows()
    {
        // the arm key is longer than the feed window and fragments across STARVED windows before
        // onOuterKey's decline-until-complete resolves it; items must still pass through correctly once
        // the key completes. Every outer key is forwarded regardless of match, and this stage does not
        // deliver verbatim bytes, so declining and forwarding the whole reassembled key afterward is safe.
        String arrayKey = "x".repeat(40);
        String json = "{\"" + arrayKey + "\":[{\"name\":\"alpha\",\"x\":1}]}";
        assertEquals(json, filterWindowed(arrayKey, Map.of(), ADMIT_ALL, json, 12));
    }

    @Test
    public void shouldResolveNameKeyThatFragmentsAcrossInputWindows()
    {
        // "name" (the item's own key, always swallowed and replaced by a synthetic constant regardless of
        // outcome) fragments across STARVED windows before onNameKey's decline-until-complete resolves it.
        // Values here are long enough that they never themselves fragment at this window, isolating the key
        // fragmentation this test targets from the separate (also fixed) name-value handling.
        String json = "{\"tools\":[{\"name\":\"alphabetagamma\",\"x\":1}]}";
        assertEquals(json, filterWindowed("tools", Map.of(), ADMIT_ALL, json, 3));
    }

    @Test
    public void shouldResolveNameValueThatFragmentsAcrossInputWindows()
    {
        // the name VALUE itself (not just its key) is long enough to fragment across the feed window;
        // resolveItem() must not look up scopesByName/admits against a prefix -- a related bug in the same
        // method fixed alongside the key-fragmentation issue this class targets
        String json = "{\"tools\":[{\"name\":\"alphabetagammadelta\",\"x\":1}]}";
        assertEquals(json, filterWindowed("tools", Map.of(), ADMIT_ALL, json, 6));
    }

    @Test
    public void shouldPassThroughUnscopedResourceWithMultipleFieldsAfterName()
    {
        // a resource item (unlike the two-field {"name":...,"x":1} tools fixture above) carries several
        // fields after "name" -- uri, description, mimeType -- mirroring a real upstream MCP resource; an
        // item absent from scopesByName (unguarded, or guarded only by its own toolkit route with no
        // operation-level scheme of its own) must still copy through byte-for-byte once admitted
        Map<CharSequence, List<String>> scopesByName = Map.of("featured_pets", List.of("petstore:tools"));
        String json = """
            {"resources":[{"name":"architecture.md","uri":"everything+demo://resource/architecture.md",\
            "description":"Architecture doc","mimeType":"text/markdown"}]}""";
        assertEquals(json, filterWindowed("resources", scopesByName, ADMIT_ALL, json, json.length()));
    }

    @Test
    public void shouldPassThroughMultipleResourceItemsWithMixedScopes()
    {
        // an unguarded item (no entry in scopesByName) immediately followed by a guarded, admitted item --
        // mirrors the real everything+petstore aggregated resources list this class's fix now exercises
        Map<CharSequence, List<String>> scopesByName = Map.of("featured_pets", List.of("petstore:tools"));
        String json = """
            {"resources":[{"name":"architecture.md","uri":"everything+demo://resource/architecture.md",\
            "description":"Architecture doc","mimeType":"text/markdown"},\
            {"name":"featured_pets","uri":"petstore+/pets/featured","description":"Featured pets",\
            "mimeType":"application/json","securitySchemes":[{"type":"oauth2","scopes":["petstore:tools"]}]}]}""";
        assertEquals(json, filterWindowed("resources", scopesByName, ADMIT_ALL, json, json.length()));
    }

    @Test
    public void shouldPassThroughSevenUnscopedItemsBeforeGuardedItemAtSmallWindow()
    {
        // full-scale reproduction of the everything+petstore aggregated resources list (7 unguarded static
        // documents ahead of 1 guarded item) fed at a small window to exercise the same STARVED/resume path
        // production hits when the document exceeds the parser's own internal read buffer
        Map<CharSequence, List<String>> scopesByName = Map.of("featured_pets", List.of("petstore:tools"));
        StringBuilder items = new StringBuilder();
        String[] docs = { "architecture", "extension", "features", "how-it-works", "instructions", "startup", "structure" };
        for (String doc : docs)
        {
            if (items.length() > 0)
            {
                items.append(',');
            }
            items.append("{\"name\":\"").append(doc).append(".md\",\"uri\":\"everything+demo://resource/static/document/")
                .append(doc).append(".md\",\"description\":\"Static document file exposed from /docs: ").append(doc)
                .append(".md\",\"mimeType\":\"text/markdown\"}");
        }
        items.append(",{\"name\":\"featured_pets\",\"uri\":\"petstore+/pets/featured\",\"description\":\"Featured pets\",")
            .append("\"mimeType\":\"application/json\",")
            .append("\"securitySchemes\":[{\"type\":\"oauth2\",\"scopes\":[\"petstore:tools\"]}]}");
        String json = "{\"resources\":[" + items + "]}";

        assertEquals(json, filterWindowed("resources", scopesByName, ADMIT_ALL, json, 64));
    }

    @Test
    @Ignore("onPending forwards container/scalar fields preceding \"name\" instead of buffering them for " +
        "replay, corrupting admitted items whose upstream shape puts \"uri\" (or anything else) before " +
        "\"name\" -- https://github.com/aklivity/zilla/issues/2073")
    public void shouldPreserveFieldsPrecedingNameKey()
    {
        // a real upstream MCP resource commonly orders "uri" before "name" (unlike Zilla's own
        // mcp_http/mcp_openapi-generated lists, which always emit "name" first) -- fields preceding "name"
        // must still reach the output once the item is admitted, not be silently dropped or forwarded
        // without their key
        String json = """
            {"resources":[{"uri":"everything+demo://resource/architecture.md","name":"architecture.md",\
            "description":"Architecture doc","mimeType":"text/markdown"}]}""";
        assertEquals(json, filterWindowed("resources", Map.of(), ADMIT_ALL, json, json.length()));
    }

    @Test
    @Ignore("see https://github.com/aklivity/zilla/issues/2073")
    public void shouldPreserveFieldsPrecedingNameKeyAcrossMultipleItems()
    {
        // the uri-before-name shape repeated across several unguarded items followed by a guarded,
        // admitted one -- mirrors the real everything+petstore aggregated resources list end to end
        Map<CharSequence, List<String>> scopesByName = Map.of("featured_pets", List.of("petstore:tools"));
        String json = """
            {"resources":[\
            {"uri":"everything+demo://resource/architecture.md","name":"architecture.md",\
            "description":"Architecture doc","mimeType":"text/markdown"},\
            {"uri":"everything+demo://resource/extension.md","name":"extension.md",\
            "description":"Extension doc","mimeType":"text/markdown"},\
            {"name":"featured_pets","uri":"petstore+/pets/featured","description":"Featured pets",\
            "mimeType":"application/json","securitySchemes":[{"type":"oauth2","scopes":["petstore:tools"]}]}\
            ]}""";
        assertEquals(json, filterWindowed("resources", scopesByName, ADMIT_ALL, json, json.length()));
    }

    private static String filterWindowed(
        String arrayKey,
        Map<CharSequence, List<String>> scopesByName,
        BiPredicate<CharSequence, List<String>> admits,
        String input,
        int window)
    {
        McpScopeFilter filter = new McpScopeFilter();
        filter.init(arrayKey, scopesByName, admits);

        JsonGeneratorEx gen = JsonEx.createGenerator();
        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[4096]);
        gen.wrap(buffer, 0, buffer.capacity());
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .transform(filter)
            .into(JsonEx.createSink(gen, Map.of(JsonSink.DELIVERY, JsonSink.Delivery.STRUCTURED)));
        pipeline.reset();

        byte[] msg = input.getBytes(UTF_8);
        int progress = 0;
        int limit = 0;
        Status status = Status.STARVED;
        int guard = 0;
        while (status == Status.STARVED && guard++ < 10_000)
        {
            limit = Math.min(limit + window, msg.length);
            boolean last = limit >= msg.length;
            status = pipeline.transform(new UnsafeBufferEx(msg), progress, limit, last);
            if (status == Status.STARVED)
            {
                progress = limit - pipeline.remaining();
            }
        }
        assertEquals(Status.COMPLETED, status);
        byte[] out = new byte[gen.length()];
        buffer.getBytes(0, out);
        return new String(out, UTF_8);
    }
}
