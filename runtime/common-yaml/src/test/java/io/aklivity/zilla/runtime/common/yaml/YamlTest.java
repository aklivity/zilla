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
package io.aklivity.zilla.runtime.common.yaml;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.json.spi.JsonProvider;

import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.yaml.json.YamlJson;
import io.aklivity.zilla.runtime.common.yaml.spi.YamlProvider;

class YamlTest
{
    @Test
    void shouldCreateNativeProviderWithoutJsonProviderRegistration()
    {
        Object provider = Yaml.provider();

        assertEquals("YamlProviderImpl", provider.getClass().getSimpleName());
        assertFalse(provider instanceof JsonProvider);
        assertNotSame(provider, YamlJson.provider());
        assertNull(Yaml.class.getClassLoader().getResource("META-INF/services/jakarta.json.spi.JsonProvider"));
    }

    @Test
    void shouldCreateNativeFactoryMethods()
    {
        assertNotNull(Yaml.provider());
        assertNotNull(Yaml.createParser(new StringReader("name: test\n")));
        assertNotNull(Yaml.createParser(new ByteArrayInputStream("name: test\n".getBytes(UTF_8))));
        assertNotNull(Yaml.createReader(new StringReader("name: test\n")));
        assertNotNull(Yaml.createReader(new ByteArrayInputStream("name: test\n".getBytes(UTF_8))));
        assertNotNull(Yaml.createGenerator(new StringWriter()));
        assertNotNull(Yaml.createGenerator(new ByteArrayOutputStream()));
        assertNotNull(Yaml.createWriter(new StringWriter()));
        assertNotNull(Yaml.createWriter(new ByteArrayOutputStream()));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldCreateNativeFactoriesWithConfig()
    {
        Map<String, Object> mutable = new HashMap<>();
        mutable.put(YamlConfig.SCALAR_RESOLUTION, false);

        YamlParserFactory parserFactory = Yaml.createParserFactory(mutable);
        YamlReaderFactory readerFactory = Yaml.createReaderFactory(mutable);
        YamlGeneratorFactory generatorFactory = Yaml.createGeneratorFactory(mutable);
        YamlWriterFactory writerFactory = Yaml.createWriterFactory(mutable);

        mutable.put(YamlConfig.SCALAR_RESOLUTION, true);

        assertEquals(false, parserFactory.getConfigInUse().get(YamlConfig.SCALAR_RESOLUTION));
        assertEquals(false, readerFactory.getConfigInUse().get(YamlConfig.SCALAR_RESOLUTION));
        assertEquals(false, generatorFactory.getConfigInUse().get(YamlConfig.SCALAR_RESOLUTION));
        assertEquals(false, writerFactory.getConfigInUse().get(YamlConfig.SCALAR_RESOLUTION));
        assertThrows(UnsupportedOperationException.class,
            () -> ((Map<String, Object>) parserFactory.getConfigInUse()).put("changed", true));

        assertEquals(YamlValue.ValueType.STRING, parserFactory
            .createParser(new ByteArrayInputStream("42\n".getBytes(UTF_8)), UTF_8)
            .parse()
            .getValueType());
        assertEquals(YamlValue.ValueType.STRING, readerFactory
            .createReader(new ByteArrayInputStream("42\n".getBytes(UTF_8)), UTF_8)
            .readValue()
            .getValueType());

        StringWriter generated = new StringWriter();
        generatorFactory.createGenerator(generated).write("test").close();
        assertEquals("test\n", generated.toString());

        ByteArrayOutputStream written = new ByteArrayOutputStream();
        writerFactory.createWriter(written, UTF_8).writeString("test");
        assertEquals("test\n", written.toString(UTF_8));

        YamlReaderFactory normalizedReader = Yaml.createReaderFactory(Map.of(
            YamlConfig.PRESERVE_SOURCE, false,
            YamlConfig.PRESERVE_COMMENTS, false));
        YamlObject normalized = normalizedReader.createReader(new StringReader("""
            # leading comment
            name: test # trailing comment
            """)).readObject();
        StringWriter normalizedOutput = new StringWriter();
        Yaml.createWriter(normalizedOutput).writeObject(normalized);
        assertEquals("name: test\n", normalizedOutput.toString());

        assertTrue(Yaml.createParserFactory(null).getConfigInUse().isEmpty());
        assertTrue(Yaml.createReaderFactory(null).getConfigInUse().isEmpty());
        assertTrue(Yaml.createGeneratorFactory(null).getConfigInUse().isEmpty());
        assertTrue(Yaml.createWriterFactory(null).getConfigInUse().isEmpty());
    }

    @Test
    void shouldApplyNativeParserFeatureConfig()
    {
        assertFeatureDisabled(YamlConfig.FEATURE_DIRECTIVES, "%YAML 1.2\n---\nname: test\n");
        assertFeatureDisabled(YamlConfig.FEATURE_DOCUMENT_MARKERS, "---\nname: test\n");
        assertFeatureDisabled(YamlConfig.FEATURE_BLOCK_SCALARS, "description: |-\n  line\n");
        assertFeatureDisabled(YamlConfig.FEATURE_FLOW_COLLECTIONS, "items: [one, two]\n");
        assertFeatureDisabled(YamlConfig.FEATURE_ANCHORS, "value: &value test\n");
        assertFeatureDisabled(YamlConfig.FEATURE_ALIASES, "value: &value test\nalias: *value\n");
        assertFeatureDisabled(YamlConfig.FEATURE_MERGE_KEYS, """
            defaults: &defaults
              name: test
            item:
              <<: *defaults
            """);
        assertFeatureDisabled(YamlConfig.FEATURE_TAGS, "value: !!str 42\n");
        assertFeatureDisabled(YamlConfig.FEATURE_COMMENTS, "name: test # comment\n");
        assertFeatureDisabled(YamlConfig.FEATURE_NON_SCALAR_KEYS, "? [a, b]\n: value\n");

        YamlReaderFactory streamDisabled = Yaml.createReaderFactory(Map.of(
            YamlConfig.FEATURE_MULTI_DOCUMENT_STREAMS, false));
        assertThrows(RuntimeException.class, () -> streamDisabled.createReader(new StringReader("""
            ---
            name: first
            ---
            name: second
            """)).readStream());
    }

    @Test
    void shouldApplyNativeGeneratorConfig()
    {
        YamlValue value = Yaml.createReader(new StringReader("""
            # leading comment
            flow: [1, 2] # line comment
            block: |-
              line
            """)).readValue();

        StringWriter exact = new StringWriter();
        Yaml.createWriter(exact).write(value);
        assertEquals("""
            # leading comment
            flow: [1, 2] # line comment
            block: |-
              line
            """, exact.toString());

        StringWriter normalized = new StringWriter();
        Yaml.createWriterFactory(Map.of(YamlConfig.PRESERVE_SOURCE, false))
            .createWriter(normalized)
            .write(value);
        assertEquals("""
            flow: [1, 2] # line comment
            block: |-
              line
            """, normalized.toString());

        StringWriter uncommented = new StringWriter();
        Yaml.createWriterFactory(Map.of(
            YamlConfig.PRESERVE_SOURCE, false,
            YamlConfig.PRESERVE_COMMENTS, false))
            .createWriter(uncommented)
            .write(value);
        assertEquals("""
            flow: [1, 2]
            block: |-
              line
            """, uncommented.toString());

        StringWriter generated = new StringWriter();
        Yaml.createGeneratorFactory(Map.of(YamlConfig.PRESERVE_COMMENTS, false))
            .createGenerator(generated)
            .writeStartObject()
            .write("name", "test")
            .writeEnd()
            .close();
        assertEquals("name: test\n", generated.toString());
    }

    @Test
    void shouldApplyNativeGeneratorFeatureConfig()
    {
        assertGeneratorFeatureDisabled(YamlConfig.FEATURE_DIRECTIVES, "%YAML 1.2\n---\nname: test\n");
        assertGeneratorFeatureDisabled(YamlConfig.FEATURE_DOCUMENT_MARKERS, "---\nname: test\n");
        assertGeneratorFeatureDisabled(YamlConfig.FEATURE_BLOCK_SCALARS, "description: |-\n  line\n");
        assertGeneratorFeatureDisabled(YamlConfig.FEATURE_FLOW_COLLECTIONS, "items: [one, two]\n");
        assertGeneratorFeatureDisabled(YamlConfig.FEATURE_ANCHORS, "value: &value test\n");
        assertGeneratorFeatureDisabled(YamlConfig.FEATURE_ALIASES, "value: &value test\nalias: *value\n");
        assertGeneratorFeatureDisabled(YamlConfig.FEATURE_MERGE_KEYS, """
            defaults: &defaults
              name: test
            item:
              <<: *defaults
            """);
        assertGeneratorFeatureDisabled(YamlConfig.FEATURE_TAGS, "value: !!str 42\n");
        assertGeneratorFeatureDisabled(YamlConfig.FEATURE_COMMENTS, "name: test # comment\n");
        assertGeneratorFeatureDisabled(YamlConfig.FEATURE_NON_SCALAR_KEYS, "? [a, b]\n: value\n");

        YamlStream stream = Yaml.createReader(new StringReader("""
            ---
            name: first
            ---
            name: second
            """)).readStream();
        StringWriter output = new StringWriter();
        YamlWriter streamDisabled = Yaml.createWriterFactory(Map.of(
            YamlConfig.FEATURE_MULTI_DOCUMENT_STREAMS, false))
            .createWriter(output);
        assertThrows(RuntimeException.class, () -> streamDisabled.writeStream(stream));

        StringWriter markersDisabled = new StringWriter();
        YamlWriter writer = Yaml.createWriterFactory(Map.of(
            YamlConfig.PRESERVE_SOURCE, false,
            YamlConfig.FEATURE_DOCUMENT_MARKERS, false))
            .createWriter(markersDisabled);
        assertThrows(RuntimeException.class, () -> writer.writeStream(stream));
    }

    private static void assertFeatureDisabled(
        String feature,
        String yaml)
    {
        YamlParser parser = Yaml.createParserFactory(Map.of(feature, false)).createParser(new StringReader(yaml));

        assertThrows(RuntimeException.class, parser::parse);
    }

    private static void assertGeneratorFeatureDisabled(
        String feature,
        String yaml)
    {
        YamlValue value = Yaml.createReader(new StringReader(yaml)).readValue();
        YamlWriter writer = Yaml.createWriterFactory(Map.of(feature, false)).createWriter(new StringWriter());

        assertThrows(RuntimeException.class, () -> writer.write(value));
    }

    @Test
    void shouldReadNativeObjectValues()
    {
        YamlProvider provider = Yaml.provider();

        YamlObject object = provider.createReader(new StringReader("""
            name: test
            items:
              - one
              - 2
            enabled: true
            missing: null
            """)).readObject();

        assertEquals(YamlValue.ValueType.OBJECT, object.getValueType());
        assertSame(object, object.asYamlObject());
        assertTrue(object.containsKey("items"));
        assertEquals(4, object.size());
        assertEquals("test", object.getString("name"));
        assertEquals("fallback", object.getString("unknown", "fallback"));

        YamlArray array = object.getArray("items");
        assertSame(array, array.asYamlArray());
        assertEquals(YamlValue.ValueType.ARRAY, array.getValueType());
        assertEquals(2, array.size());
        assertEquals("one", array.getString(0));
        assertEquals(YamlScalarType.NUMBER, array.getScalar(1).getType());
        assertEquals("2", array.getScalar(1).getString());
        assertEquals(2, array.getInt(1));

        YamlScalar enabled = object.getScalar("enabled");
        assertSame(enabled, enabled.asYamlScalar());
        assertEquals(YamlValue.ValueType.TRUE, enabled.getValueType());
        assertEquals(YamlScalarType.BOOLEAN, enabled.getType());
        assertEquals("true", enabled.getString());
        assertTrue(object.getBoolean("enabled"));

        YamlScalar missing = object.getScalar("missing");
        assertEquals(YamlValue.ValueType.NULL, missing.getValueType());
        assertEquals(YamlScalarType.NULL, missing.getType());
        assertNull(missing.getString());
        assertTrue(object.isNull("missing"));
    }

    @Test
    void shouldReadNativeArrayAndScalarValues()
    {
        YamlArray array = Yaml.createReader(new ByteArrayInputStream("- false\n- null\n".getBytes(UTF_8))).readArray();
        assertEquals(YamlValue.ValueType.FALSE, array.get(0).getValueType());
        assertEquals(YamlValue.ValueType.NULL, array.get(1).getValueType());
        assertFalse(array.getBoolean(0));
        assertTrue(array.isNull(1));

        YamlValue scalar = Yaml.createParser(new ByteArrayInputStream("42\n".getBytes(UTF_8))).parse();
        assertEquals(YamlValue.ValueType.NUMBER, scalar.getValueType());
        assertEquals(YamlScalarType.NUMBER, scalar.asYamlScalar().getType());
        assertEquals("42", scalar.asYamlScalar().getString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldCreateNativeBuilderValues()
    {
        Map<String, Object> config = new HashMap<>();
        config.put("builder", "value");
        YamlBuilderFactory factory = Yaml.createBuilderFactory(config);
        config.put("builder", "changed");

        assertEquals(Map.of("builder", "value"), factory.getConfigInUse());
        assertThrows(UnsupportedOperationException.class,
            () -> ((Map<String, Object>) factory.getConfigInUse()).put("changed", true));

        YamlArray array = factory.createArrayBuilder()
            .add("zero")
            .add(2)
            .add(3L)
            .add(new BigDecimal("4.5"))
            .add(new BigInteger("6"))
            .add(true)
            .addNull()
            .add(1, Yaml.createValue("one"))
            .set(2, Yaml.createValue(1))
            .remove(7)
            .build();

        assertEquals(7, array.size());
        assertEquals("one", array.getString(1));
        assertEquals(1, array.getInt(2));
        assertEquals(3L, array.getLong(3));
        assertEquals(new BigDecimal("4.5"), new BigDecimal(array.getString(4)));
        assertEquals(new BigInteger("6"), new BigInteger(array.getString(5)));
        assertTrue(array.getBoolean(6));

        Map<String, Object> mapped = new LinkedHashMap<>();
        mapped.put("name", "test");
        mapped.put("count", 1);
        mapped.put("items", List.of("a", "b"));
        mapped.put("nested", Map.of("enabled", true));
        YamlObject object = factory.createObjectBuilder(mapped)
            .addAll(factory.createObjectBuilder().add("merged", "yes"))
            .add("array", factory.createArrayBuilder(array))
            .add("child", factory.createObjectBuilder().add("name", "child"))
            .add("long", 7L)
            .add("double", 8.5)
            .add("decimal", new BigDecimal("9.25"))
            .add("big", new BigInteger("10"))
            .add("value", Yaml.createValue("direct"))
            .addNull("missing")
            .remove("count")
            .build();

        assertEquals("test", object.getString("name"));
        assertEquals("b", object.getArray("items").getString(1));
        assertTrue(object.getObject("nested").getBoolean("enabled"));
        assertEquals("yes", object.getString("merged"));
        assertEquals("child", object.getObject("child").getString("name"));
        assertEquals(7L, object.getLong("long"));
        assertEquals(8.5, object.getDouble("double"));
        assertEquals("direct", object.getString("value"));
        assertTrue(object.isNull("missing"));
        assertFalse(object.containsKey("count"));

        YamlObject copied = factory.createObjectBuilder(object)
            .add("copy", true)
            .build();
        assertEquals("test", copied.getString("name"));
        assertTrue(copied.getBoolean("copy"));

        YamlArray copiedArray = factory.createArrayBuilder(array)
            .add(false)
            .build();
        assertEquals(8, copiedArray.size());
        assertFalse(copiedArray.getBoolean(7));

        YamlArray indexed = Yaml.createArrayBuilder()
            .add("start")
            .add("end")
            .add(1, new BigDecimal("1.25"))
            .add(2, new BigInteger("2"))
            .add(3, 3)
            .add(4, 4L)
            .add(5, 5.5)
            .add(6, true)
            .addNull(7)
            .add(8, Yaml.createObjectBuilder().add("name", "object"))
            .add(9, Yaml.createArrayBuilder().add("array"))
            .set(0, "updated")
            .set(1, new BigDecimal("1.5"))
            .set(2, new BigInteger("20"))
            .set(3, 30)
            .set(4, 40L)
            .set(5, 50.5)
            .set(6, false)
            .setNull(7)
            .set(8, Yaml.createObjectBuilder().add("name", "updated"))
            .set(9, Yaml.createArrayBuilder().add("updated"))
            .addAll(Yaml.createArrayBuilder().add("tail"))
            .build();
        assertEquals("updated", indexed.getString(0));
        assertEquals(new BigDecimal("1.5"), new BigDecimal(indexed.getString(1)));
        assertEquals(new BigInteger("20"), new BigInteger(indexed.getString(2)));
        assertEquals(30, indexed.getInt(3));
        assertEquals(40L, indexed.getLong(4));
        assertEquals(50.5, indexed.getDouble(5));
        assertFalse(indexed.getBoolean(6));
        assertTrue(indexed.isNull(7));
        assertEquals("updated", indexed.getObject(8).getString("name"));
        assertEquals("updated", indexed.getArray(9).getString(0));
        assertEquals("tail", indexed.getString(11));

        assertTrue(Yaml.createObjectBuilder(Map.of("static", true)).build().getBoolean("static"));
        assertEquals("test", Yaml.createObjectBuilder(object).build().getString("name"));
        assertEquals(2, Yaml.createArrayBuilder(List.of("a", "b")).build().size());
        assertEquals(array.size(), Yaml.createArrayBuilder(array).build().size());

        assertEquals(YamlValue.ValueType.STRING, Yaml.createValue("text").getValueType());
        assertEquals(YamlValue.ValueType.NUMBER, Yaml.createValue((Number) new BigDecimal("42")).getValueType());
        assertEquals(YamlValue.ValueType.TRUE, Yaml.createValue(true).getValueType());
        assertEquals(YamlValue.ValueType.NULL, Yaml.createNullValue().getValueType());
        assertThrows(IllegalArgumentException.class, () -> Yaml.createValue(Double.NaN));

        StringWriter output = new StringWriter();
        Yaml.createWriter(output).writeObject(object);
        assertEquals("""
            name: test
            items:
              - a
              - b
            nested:
              enabled: true
            merged: yes
            array:
              - zero
              - one
              - 1
              - 3
              - 4.5
              - 6
              - true
            child:
              name: child
            long: 7
            double: 8.5
            decimal: 9.25
            big: 10
            value: direct
            missing: null
            """, output.toString());
    }

    @Test
    void shouldCreateNativeBuilderValuesWithMetadata()
    {
        YamlValue description = Yaml.createValue("one\ntwo\n")
            .withAnchor("desc")
            .withTag("tag:yaml.org,2002:str")
            .withStyle("|-")
            .withLeadingComment("# before description")
            .withLineComment("# after description");
        YamlObject defaults = Yaml.createObjectBuilder()
            .withAnchor("defaults")
            .withTag("!defaults")
            .withStyle("flow")
            .withLineComment("# after defaults")
            .add("enabled", true)
            .build();
        YamlArray key = Yaml.createArrayBuilder()
            .withStyle("flow")
            .add("key")
            .build();
        YamlObject object = Yaml.createObjectBuilder()
            .withLeadingComments(List.of("# before root"))
            .add("description", description)
            .add("defaults", defaults)
            .add("alias", Yaml.createNullValue().withAlias("defaults"))
            .add(key, Yaml.createValue("mapped"))
            .add("items", Yaml.createArrayBuilder()
                .withStyle("flow")
                .withLeadingComment("# before items")
                .add("one")
                .add(2))
            .build();
        YamlObject copied = Yaml.createObjectBuilder(defaults).build();
        YamlObject copiedParsed = Yaml.createObjectBuilder(
            Yaml.createReader(new StringReader("name: test\n")).readObject())
            .add("copy", true)
            .build();
        StringWriter output = new StringWriter();
        StringWriter copiedOutput = new StringWriter();

        assertEquals("desc", description.getAnchor());
        assertEquals("tag:yaml.org,2002:str", description.getTag());
        assertEquals("|-", description.getStyle());
        assertEquals(List.of("# before description"), description.getLeadingComments());
        assertEquals("# after description", description.getLineComment());
        assertEquals("defaults", copied.getAnchor());
        assertEquals("!defaults", copied.getTag());
        assertEquals("flow", copied.getStyle());
        assertEquals("# after defaults", copied.getLineComment());

        Yaml.createWriter(output).writeObject(object);
        Yaml.createWriter(copiedOutput).writeObject(copiedParsed);

        assertEquals("""
            # before root
            # before description
            description: &desc !!str |- # after description
              one
              two
            defaults: &defaults !defaults {enabled: true} # after defaults
            alias: *defaults
            ? [key]
            : mapped
            # before items
            items: [one, 2]
            """, output.toString());
        assertEquals("""
            name: test
            copy: true
            """, copiedOutput.toString());
    }

    @Test
    void shouldParseNativeEvents()
    {
        YamlParser parser = Yaml.createParser(new StringReader("""
            name: test
            items:
              - 1
              - false
            """));
        List<String> events = new ArrayList<>();

        while (parser.hasNext())
        {
            YamlEvent event = parser.next();
            events.add(event + (parser.getString() != null ? ":" + parser.getString() : ""));
            assertSame(parser.getValue(), parser.getValue());
            if (event == YamlEvent.START_OBJECT)
            {
                assertEquals(YamlValue.ValueType.OBJECT, parser.getObject().getValueType());
            }
            if (event == YamlEvent.START_ARRAY)
            {
                assertEquals(YamlValue.ValueType.ARRAY, parser.getArray().getValueType());
            }
            if (event == YamlEvent.VALUE_NUMBER)
            {
                assertEquals(YamlScalarType.NUMBER, parser.getScalar().getType());
            }
        }

        assertEquals(List.of(
            "START_OBJECT",
            "KEY_NAME:name",
            "VALUE_STRING:test",
            "KEY_NAME:items",
            "START_ARRAY",
            "VALUE_NUMBER:1",
            "VALUE_FALSE",
            "END_ARRAY",
            "END_OBJECT"), events);
        assertFalse(parser.hasNext());
        assertThrows(IllegalStateException.class, parser::next);
        assertThrows(IllegalStateException.class, parser::parse);
    }

    @Test
    void shouldStreamRawReferences()
    {
        YamlParser parser = Yaml.createParser(new StringReader("""
            base: &base
              host: localhost
            use:
              <<: *base
            typed: !!str 42
            """));
        List<String> events = new ArrayList<>();
        while (parser.hasNext())
        {
            YamlEvent event = parser.next();
            StringBuilder token = new StringBuilder(event.toString());
            if (parser.getString() != null)
            {
                token.append(':').append(parser.getString());
            }
            if (parser.getAlias() != null)
            {
                token.append('*').append(parser.getAlias());
            }
            if (parser.getAnchor() != null)
            {
                token.append('&').append(parser.getAnchor());
            }
            if (parser.getTag() != null)
            {
                token.append('!').append(parser.getTag());
            }
            events.add(token.toString());
        }

        assertEquals(List.of(
            "START_OBJECT",
            "KEY_NAME:base",
            "START_OBJECT&base",
            "KEY_NAME:host",
            "VALUE_STRING:localhost",
            "END_OBJECT",
            "KEY_NAME:use",
            "START_OBJECT",
            "KEY_NAME:<<",
            "ALIAS*base",
            "END_OBJECT",
            "KEY_NAME:typed",
            "VALUE_NUMBER:42!tag:yaml.org,2002:str",
            "END_OBJECT"), events);
    }

    @Test
    void shouldResolveReferencesViaReader()
    {
        YamlObject object = Yaml.createReader(new StringReader("""
            base: &base
              host: localhost
            use:
              <<: *base
            typed: !!str 42
            """)).readObject();

        assertEquals("localhost", object.getObject("use").getString("host"));
        assertEquals(YamlScalarType.STRING, object.getScalar("typed").getType());
    }

    @Test
    void shouldGenerateNativeValues()
    {
        YamlObject object = Yaml.createReader(new StringReader("""
            name: test
            items:
              - one
              - 2
            enabled: true
            missing: null
            """)).readObject();
        StringWriter generated = new StringWriter();

        Yaml.createGenerator(generated).write(object).close();

        assertEquals("""
            name: test
            items:
              - one
              - 2
            enabled: true
            missing: null
            """, generated.toString());

        StringWriter streamed = new StringWriter();
        Yaml.createGenerator(streamed)
            .writeStartObject()
            .write("name", "test")
            .writeStartArray("items")
            .write("one")
            .write(2)
            .writeEnd()
            .write("enabled", true)
            .writeNull("missing")
            .writeEnd()
            .close();

        assertEquals("""
            name: test
            items:
              - one
              - 2
            enabled: true
            missing: null
            """, streamed.toString());

        ByteArrayOutputStream named = new ByteArrayOutputStream();
        YamlValue direct = Yaml.createValue("direct");
        YamlGenerator namedGenerator = Yaml.createGenerator(named)
            .writeStartObject()
            .writeStartObject("nested")
            .write("int", 1)
            .write("long", 2L)
            .write("double", 3.5)
            .write("value", direct)
            .writeEnd()
            .write("flag", false)
            .writeEnd();
        namedGenerator.flush();
        namedGenerator.close();

        assertEquals("""
            nested:
              int: 1
              long: 2
              double: 3.5
              value: direct
            flag: false
            """, named.toString(UTF_8));
    }

    @Test
    void shouldRoundTripNativeSource()
    {
        String yaml = """
            %YAML 1.2
            ---
            # leading comment
            defaults: &defaults
              name: !!str test
              description: |-
                line one
                line two
            flow: [1, "two", {enabled: true}]
            item:
              <<: *defaults
              description: >-
                folded
                text
            ...
            """;
        YamlValue value = Yaml.createReader(new StringReader(yaml)).readValue();
        StringWriter generated = new StringWriter();

        Yaml.createWriter(generated).write(value);

        assertEquals(yaml, generated.toString());
    }

    @Test
    void shouldRoundTripNativeNonScalarKeys()
    {
        String yaml = """
            ? [a, b]
            : value
            ? {name: test}
            :
              nested: true
            """;
        YamlValue value = Yaml.createReader(new StringReader(yaml)).readValue();
        StringWriter generated = new StringWriter();

        Yaml.createWriter(generated).write(value);

        assertEquals(yaml, generated.toString());

        YamlParser parser = Yaml.createParser(new StringReader(yaml));
        assertEquals(YamlEvent.START_OBJECT, parser.next());
        YamlEvent key = parser.next();
        assertEquals(YamlEvent.KEY_NAME, key);
        assertNull(parser.getString());
        assertEquals(YamlValue.ValueType.ARRAY, parser.getValue().getValueType());
    }

    @Test
    void shouldRoundTripNativeFlowNonScalarKeys()
    {
        String yaml = """
            {? [a, b]: value, ? {name: test}: {nested: true}}
            """;
        YamlObject object = Yaml.createReader(new StringReader(yaml)).readObject();
        StringWriter generated = new StringWriter();

        Yaml.createWriter(generated).writeObject(object);

        assertEquals(yaml, generated.toString());

        YamlParser parser = Yaml.createParser(new StringReader(yaml));
        assertEquals(YamlEvent.START_OBJECT, parser.next());
        YamlEvent key = parser.next();
        assertEquals(YamlEvent.KEY_NAME, key);
        assertNull(parser.getString());
        assertEquals(YamlValue.ValueType.ARRAY, parser.getValue().getValueType());
    }

    @Test
    void shouldRoundTripNativeDecoratedMappingKeys()
    {
        String yaml = """
            %TAG !e! tag:example.com,2026:
            ---
            ? &key !e!name "name"
            : value
            ? *key
            : alias value
            ? &items !e!items [a, b]
            : tagged key
            """;
        YamlObject object = Yaml.createReader(new StringReader(yaml)).readObject();
        StringWriter generated = new StringWriter();

        Yaml.createWriter(generated).writeObject(object);

        assertEquals(yaml, generated.toString());

        YamlReaderFactory normalizedReader = Yaml.createReaderFactory(Map.of(YamlConfig.PRESERVE_SOURCE, false));
        YamlObject normalized = normalizedReader.createReader(new StringReader(yaml)).readObject();
        StringWriter normalizedOutput = new StringWriter();

        Yaml.createWriter(normalizedOutput).writeObject(normalized);

        assertEquals("""
            ? &key !<tag:example.com,2026:name> "name"
            : value
            ? *key
            : "alias value"
            ? &items !<tag:example.com,2026:items> [a, b]
            : "tagged key"
            """, normalizedOutput.toString());
    }

    @Test
    void shouldRejectUnknownTagHandles()
    {
        assertThrows(RuntimeException.class, () -> Yaml.createReader(new StringReader("value: !missing!name test\n"))
            .readValue());
        assertThrows(RuntimeException.class, () -> Yaml.createReader(new StringReader("""
            %TAG !e! tag:example.com,2026:
            ---
            value: !e!name test
            ---
            value: !e!name test
            """)).readStream());
    }

    @Test
    void shouldReadAndWriteNativeDocumentStream()
    {
        String yaml = """
            ---
            name: first
            ...
            ---
            name: second
            """;
        YamlStream stream = Yaml.createReader(new StringReader(yaml)).readStream();
        StringWriter generated = new StringWriter();
        List<String> names = new ArrayList<>();

        for (YamlDocument document : stream)
        {
            names.add(document.getValue().asYamlObject().getString("name"));
        }
        Yaml.createWriter(generated).writeStream(stream);

        assertEquals(2, stream.size());
        assertEquals("first", stream.getDocument(0).getValue().asYamlObject().getString("name"));
        assertEquals(List.of("first", "second"), names);
        assertEquals(yaml, generated.toString());
    }

    @Test
    void shouldGenerateParsedNativeChildStyleMetadata()
    {
        YamlObject object = Yaml.createReader(new StringReader("""
            single: 'it''s'
            double: "line\\nA"
            block: |-
              one
              two
            # before scalar
            commented: value # after scalar
            flow: [1, "two", {enabled: true}]
            tagged: !!str "42"
            anchored: &anchored value
            alias: *anchored
            items:
              # before item
              - alias: *anchored
              - item # after item
            custom: !custom "tagged"
            """)).readObject();
        StringWriter single = new StringWriter();
        StringWriter doub = new StringWriter();
        StringWriter block = new StringWriter();
        StringWriter flow = new StringWriter();
        StringWriter commented = new StringWriter();
        StringWriter tagged = new StringWriter();
        StringWriter anchored = new StringWriter();
        StringWriter alias = new StringWriter();
        StringWriter items = new StringWriter();
        StringWriter custom = new StringWriter();

        Yaml.createWriter(single).write(object.getScalar("single"));
        Yaml.createWriter(doub).write(object.getScalar("double"));
        Yaml.createWriter(block).write(object.getScalar("block"));
        Yaml.createWriter(flow).write(object.getArray("flow"));
        Yaml.createWriter(commented).write(object.getScalar("commented"));
        Yaml.createWriter(tagged).write(object.getScalar("tagged"));
        Yaml.createWriter(anchored).write(object.getScalar("anchored"));
        Yaml.createWriter(alias).write(object.getScalar("alias"));
        Yaml.createWriter(items).write(object.getArray("items"));
        Yaml.createWriter(custom).write(object.getScalar("custom"));

        assertEquals("'it''s'\n", single.toString());
        assertEquals("\"line\\nA\"\n", doub.toString());
        assertEquals("""
            |-
              one
              two
            """, block.toString());
        assertEquals("[1, \"two\", {enabled: true}]\n", flow.toString());
        assertEquals("""
            # before scalar
            value # after scalar
            """, commented.toString());
        assertEquals("!!str \"42\"\n", tagged.toString());
        assertEquals("&anchored value\n", anchored.toString());
        assertEquals("*anchored\n", alias.toString());
        assertEquals("""
            # before item
            - alias: *anchored
            - item # after item
            """, items.toString());
        assertEquals("!custom \"tagged\"\n", custom.toString());
    }

    @Test
    void shouldResolveNativeMergePrecedenceAndExplicitOverrides()
    {
        YamlObject object = Yaml.createReaderFactory(Map.of(YamlConfig.PRESERVE_SOURCE, false))
            .createReader(new StringReader("""
            base: &base
              host: localhost
              port: 7114
              nested:
                base: true
            tls: &tls
              port: 443
              secure: true
              nested:
                tls: true
            route:
              <<: [*base, *tls]
              host: example.com
              nested:
                route: true
            """))
            .readObject();

        YamlObject route = object.getObject("route");
        assertEquals("example.com", route.getString("host"));
        assertEquals(7114, route.getInt("port"));
        assertTrue(route.getBoolean("secure"));
        assertTrue(route.getObject("nested").getBoolean("route"));
        assertFalse(route.getObject("nested").containsKey("base"));
        assertFalse(route.getObject("nested").containsKey("tls"));

        StringWriter generated = new StringWriter();
        Yaml.createWriterFactory(Map.of(YamlConfig.PRESERVE_SOURCE, false))
            .createWriter(generated)
            .writeObject(object);

        assertEquals("""
            base:
              host: localhost
              port: 7114
              nested:
                base: true
            tls:
              port: 443
              secure: true
              nested:
                tls: true
            route:
              port: 7114
              secure: true
              host: example.com
              nested:
                route: true
            """, generated.toString());
    }

    @Test
    void shouldResolveNativeSequenceOfMergeAliases()
    {
        YamlObject object = Yaml.createReader(new StringReader("""
            one: &one
              first: true
              shared: one
            two: &two
              second: true
              shared: two
            route:
              <<: [*one, *two]
            """)).readObject();

        YamlObject route = object.getObject("route");
        assertTrue(route.getBoolean("first"));
        assertTrue(route.getBoolean("second"));
        assertEquals("one", route.getString("shared"));
    }

    @Test
    void shouldParseNativeAnchoredValuesAndCompactSequences()
    {
        YamlValue anchored = Yaml.createReader(new StringReader("""
            &root
            name: test
            """)).readValue();
        assertEquals("root", anchored.getAnchor());
        assertEquals("test", anchored.asYamlObject().getString("name"));

        YamlValue taggedScalar = Yaml.createReader(new StringReader("""
            !tagged
            value
            """)).readValue();
        assertEquals("!tagged", taggedScalar.getTag());
        assertEquals("value", taggedScalar.asYamlScalar().getString());

        YamlObject object = Yaml.createReader(new StringReader("""
            nested: - name: one
              - two
            keyed:
              ? [a]
              : - value
                -
            """)).readObject();

        YamlArray nested = object.getArray("nested");
        assertEquals("one", nested.getObject(0).getString("name"));
        assertEquals("two", nested.getString(1));

        YamlParser parser = Yaml.createParser(new StringReader("""
            keyed:
              ? [a]
              : - value
                -
            """));
        assertEquals(YamlEvent.START_OBJECT, parser.next());
        assertEquals(YamlEvent.KEY_NAME, parser.next());
        assertEquals(YamlEvent.START_OBJECT, parser.next());
        YamlEvent key = parser.next();
        assertEquals(YamlEvent.KEY_NAME, key);
        assertEquals(YamlValue.ValueType.ARRAY, parser.getValue().getValueType());
        assertEquals(YamlEvent.START_ARRAY, parser.next());
        parser.next();
        assertEquals("value", parser.getString());
        assertEquals(YamlEvent.VALUE_NULL, parser.next());
    }

    @Test
    void shouldFoldNativeMultilineQuotedScalars()
    {
        YamlObject object = Yaml.createReaderFactory(Map.of(YamlConfig.PRESERVE_SOURCE, false))
            .createReader(new StringReader("""
                single: 'one

                  two
                  three'
                double: "one
                  two"
                """))
            .readObject();

        assertEquals("one\ntwo three", object.getString("single"));
        assertEquals("one two", object.getString("double"));
    }

    @Test
    void shouldPreserveNativeAliasNodesWhenWritingChildren()
    {
        YamlObject object = Yaml.createReaderFactory(Map.of(YamlConfig.PRESERVE_SOURCE, false))
            .createReader(new StringReader("""
            defaults: &defaults
              name: test
              nested:
                value: one
            alias: *defaults
            aliases:
              - *defaults
            """))
            .readObject();
        StringWriter alias = new StringWriter();
        StringWriter aliases = new StringWriter();
        StringWriter generated = new StringWriter();

        YamlObject resolved = object.getObject("alias");
        assertEquals("test", resolved.getString("name"));
        assertEquals("one", resolved.getObject("nested").getString("value"));
        assertEquals("test", object.getArray("aliases").getObject(0).getString("name"));

        Yaml.createWriter(alias).writeObject(resolved);
        Yaml.createWriter(aliases).writeArray(object.getArray("aliases"));
        Yaml.createWriterFactory(Map.of(YamlConfig.PRESERVE_SOURCE, false))
            .createWriter(generated)
            .writeObject(object);

        assertEquals("*defaults\n", alias.toString());
        assertEquals("- *defaults\n", aliases.toString());
        assertEquals("""
            defaults:
              name: test
              nested:
                value: one
            alias: *defaults
            aliases:
              - *defaults
            """, generated.toString());
    }

    @Test
    void shouldRejectInvalidNativeAliasAndMergeDocuments()
    {
        assertThrows(RuntimeException.class, () -> Yaml.createReader(new StringReader("value: *missing\n")).readValue());
        assertThrows(RuntimeException.class, () -> Yaml.createReader(new StringReader("""
            defaults: &defaults scalar
            route:
              <<: *defaults
            """)).readValue());
        assertThrows(RuntimeException.class, () -> Yaml.createReader(new StringReader("""
            defaults: &defaults
              - name: one
              - two
            route:
              <<: *defaults
            """)).readValue());
    }

    @Test
    void shouldResolveDuplicateNativeAnchorToLatestValue()
    {
        YamlObject object = Yaml.createReader(new StringReader("""
            first: &dup one
            second: &dup two
            alias: *dup
            """)).readObject();

        assertEquals("two", object.getString("alias"));
    }

    @Test
    void shouldWriteNativeValues()
    {
        YamlObject object = Yaml.createReader(new StringReader("name: test\n")).readObject();
        StringWriter objectWriter = new StringWriter();
        ByteArrayOutputStream arrayWriter = new ByteArrayOutputStream();
        StringWriter stringWriter = new StringWriter();
        StringWriter numberWriter = new StringWriter();
        StringWriter booleanWriter = new StringWriter();
        StringWriter nullWriter = new StringWriter();

        Yaml.createWriter(objectWriter).writeObject(object);
        Yaml.provider().createWriter(arrayWriter).writeArray(
            Yaml.createReader(new StringReader("- one\n")).readArray());
        Yaml.createWriter(stringWriter).writeString("test");
        Yaml.createWriter(numberWriter).writeNumber(42);
        Yaml.createWriter(booleanWriter).writeBoolean(true);
        Yaml.createWriter(nullWriter).writeNull();

        assertEquals("name: test\n", objectWriter.toString());
        assertEquals("- one\n", arrayWriter.toString(UTF_8));
        assertEquals("test\n", stringWriter.toString());
        assertEquals("42\n", numberWriter.toString());
        assertEquals("true\n", booleanWriter.toString());
        assertEquals("null\n", nullWriter.toString());
    }

    @Test
    void shouldRejectInvalidNativeUsage()
    {
        YamlParser parser = Yaml.createParser(new StringReader("name: test\n"));
        parser.parse();

        assertThrows(IllegalStateException.class, parser::parse);
        assertThrows(IllegalStateException.class, () -> Yaml.createReader(new StringReader("42\n")).read());
        assertThrows(IllegalStateException.class, () -> Yaml.createReader(new StringReader("42\n")).readObject());
        assertThrows(IllegalStateException.class, () -> Yaml.createReader(new StringReader("42\n")).readArray());

        StringWriter writer = new StringWriter();
        YamlGenerator generator = Yaml.createGenerator(writer);
        generator.write(Yaml.createReader(new StringReader("name: test\n")).readObject());
        assertThrows(IllegalStateException.class,
            () -> generator.write(Yaml.createReader(new StringReader("name: other\n")).readObject()));
        generator.close();
        assertThrows(IllegalStateException.class,
            () -> generator.write(Yaml.createReader(new StringReader("name: closed\n")).readObject()));

        assertThrows(IllegalStateException.class, () -> Yaml.createGenerator(new StringWriter()).writeKey("name"));
        assertThrows(IllegalStateException.class, () -> Yaml.createGenerator(new StringWriter()).writeEnd());
        assertThrows(IllegalArgumentException.class, () -> Yaml.createGenerator(new StringWriter()).write(Double.NaN));

        YamlGenerator open = Yaml.createGenerator(new StringWriter()).writeStartObject().writeKey("name");
        assertThrows(IllegalStateException.class, open::writeEnd);

        YamlGenerator incomplete = Yaml.createGenerator(new StringWriter()).writeStartArray();
        assertThrows(IllegalStateException.class, incomplete::close);
    }
}
