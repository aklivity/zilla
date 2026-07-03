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
package io.aklivity.zilla.runtime.common.openapi.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Optional;

import org.junit.Test;

import io.aklivity.zilla.runtime.common.openapi.model.Openapi;
import io.aklivity.zilla.runtime.common.openapi.view.OpenapiOperationView;
import io.aklivity.zilla.runtime.common.openapi.view.OpenapiSecuritySchemeView;
import io.aklivity.zilla.runtime.common.openapi.view.OpenapiView;

public class OpenapiParserTest
{
    private static final String SPEC =
        "{" +
        "  \"openapi\": \"3.1.0\"," +
        "  \"info\": { \"title\": \"sample\", \"version\": \"1.0.0\" }," +
        "  \"x-zilla-sample\": { \"key\": \"root-value\" }," +
        "  \"paths\": {" +
        "    \"/items\": {" +
        "      \"get\": {" +
        "        \"operationId\": \"listItems\"," +
        "        \"x-zilla-sample\": { \"key\": \"operation-value\" }," +
        "        \"responses\": { \"200\": { \"description\": \"ok\" } }" +
        "      }" +
        "    }" +
        "  }," +
        "  \"components\": {" +
        "    \"securitySchemes\": {" +
        "      \"bearerAuth\": {" +
        "        \"type\": \"http\"," +
        "        \"scheme\": \"bearer\"," +
        "        \"x-zilla-sample\": { \"key\": \"scheme-value\" }" +
        "      }" +
        "    }" +
        "  }" +
        "}";

    public static final class SampleExtension
    {
        public String key;
    }

    @Test
    public void shouldExposeSpecificationExtensionsGenerically()
    {
        OpenapiParser parser = new OpenapiParser();

        Openapi model = parser.parse(SPEC);
        OpenapiView view = OpenapiView.of(model);
        OpenapiOperationView operation = view.operations.get("listItems");

        assertTrue(view.hasExtension("x-zilla-sample"));
        Optional<SampleExtension> rootExtension = view.extension("x-zilla-sample", SampleExtension.class);
        assertTrue(rootExtension.isPresent());
        assertEquals("root-value", rootExtension.get().key);

        assertTrue(operation.hasExtension("x-zilla-sample"));
        Optional<SampleExtension> operationExtension = operation.extension("x-zilla-sample", SampleExtension.class);
        assertTrue(operationExtension.isPresent());
        assertEquals("operation-value", operationExtension.get().key);

        assertFalse(operation.hasExtension("x-zilla-unknown"));
        assertFalse(operation.extension("x-zilla-unknown", SampleExtension.class).isPresent());
    }

    @Test
    public void shouldExposeSecuritySchemeExtensionGenerically()
    {
        OpenapiParser parser = new OpenapiParser();

        Openapi model = parser.parse(SPEC);
        OpenapiView view = OpenapiView.of(model);
        OpenapiSecuritySchemeView scheme = view.components.securitySchemes.get("bearerAuth");

        assertTrue(scheme.hasExtension("x-zilla-sample"));
        Optional<SampleExtension> extension = scheme.extension("x-zilla-sample", SampleExtension.class);
        assertTrue(extension.isPresent());
        assertEquals("scheme-value", extension.get().key);
    }

    @Test
    public void shouldBindRegisteredExtensionTypeEagerly()
    {
        OpenapiParser parser = new OpenapiParserFactory()
            .withExtension("x-zilla-sample", SampleExtension.class)
            .createParser();

        Openapi model = parser.parse(SPEC);
        OpenapiView view = OpenapiView.of(model);
        OpenapiOperationView operation = view.operations.get("listItems");
        OpenapiSecuritySchemeView scheme = view.components.securitySchemes.get("bearerAuth");

        assertEquals("root-value", view.extension("x-zilla-sample", SampleExtension.class).get().key);
        assertEquals("operation-value", operation.extension("x-zilla-sample", SampleExtension.class).get().key);
        assertEquals("scheme-value", scheme.extension("x-zilla-sample", SampleExtension.class).get().key);
    }
}
