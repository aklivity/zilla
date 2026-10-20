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
import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmToolTransformRunner.assertRoundTrips;
import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmToolTransformRunner.assertTransforms;
import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmToolTransformRunner.json;

import java.util.Locale;

import org.junit.Test;

public class LlmGeminiToolDefinitionCanonicalTest
{
    private static final String HUGE = "x".repeat(5000);
    private static final int NESTED_LEVELS = 30;

    private final LlmGeminiToolDefinitionToCanonical to = new LlmGeminiToolDefinitionToCanonical();
    private final LlmGeminiToolDefinitionFromCanonical from = new LlmGeminiToolDefinitionFromCanonical(true);

    @Test
    public void shouldWrapDeclarationAsFunction()
    {
        assertTransforms(to,
            json("{'name':'get_weather','description':'Get the weather','parametersJsonSchema':{'type':'object'}}"),
            json("{'type':'function','function':{'name':'get_weather','description':'Get the weather'," +
                "'parameters':{'type':'object'}}}"));
    }

    @Test
    public void shouldWrapSnakeCaseJsonSchemaAsParameters()
    {
        assertTransforms(to,
            json("{'name':'get_weather','parameters_json_schema':{'type':'object','properties':{'city':{'type':'string'}}}}"),
            json("{'type':'function','function':{'name':'get_weather'," +
                "'parameters':{'type':'object','properties':{'city':{'type':'string'}}}}}"));
    }

    @Test
    public void shouldWrapOpenApiParametersAsJsonSchema()
    {
        assertTransforms(to,
            json("{'name':'get_weather','parameters':{'type':'OBJECT','properties':{'city':{'type':'STRING'," +
                "'description':'City name'}},'required':['city']}}"),
            json("{'type':'function','function':{'name':'get_weather','parameters':{'type':'object'," +
                "'properties':{'city':{'type':'string','description':'City name'}},'required':['city']}}}"));
    }

    @Test
    public void shouldWrapOpenApiParametersWithPropertyNamedType()
    {
        assertTransforms(to,
            json("{'name':'f','parameters':{'type':'OBJECT','properties':{'type':{'type':'INTEGER'},'city':{'type':'STRING'}}}}"),
            json("{'type':'function','function':{'name':'f','parameters':{'type':'object'," +
                "'properties':{'type':{'type':'integer'},'city':{'type':'string'}}}}}"));
    }

    @Test
    public void shouldWrapOpenApiParametersWithNestedSchemas()
    {
        assertTransforms(to,
            json("{'name':'f','parameters':{'type':'OBJECT','properties':{'tags':{'type':'ARRAY','items':{'type':'STRING'}}," +
                "'place':{'type':'OBJECT','properties':{'city':{'type':'STRING'}}},'n':{'type':'NUMBER','nullable':true}}}}"),
            json("{'type':'function','function':{'name':'f','parameters':{'type':'object','properties':{" +
                "'tags':{'type':'array','items':{'type':'string'}}," +
                "'place':{'type':'object','properties':{'city':{'type':'string'}}},'n':{'type':'number','nullable':true}}}}}"));
    }

    @Test
    public void shouldLowercaseTypesInAnyOfScalars()
    {
        assertTransforms(to,
            json("{'name':'f','parameters':{'type':'OBJECT','properties':{" +
                "'x':{'anyOf':[{'type':'STRING'},{'type':'INTEGER'}]}}}}"),
            json("{'type':'function','function':{'name':'f','parameters':{'type':'object','properties':{" +
                "'x':{'anyOf':[{'type':'string'},{'type':'integer'}]}}}}}"));
    }

    @Test
    public void shouldLowercaseTypesInAnyOfObjects()
    {
        assertTransforms(to,
            json("{'name':'f','parameters':{'type':'OBJECT','properties':{'x':{'anyOf':[" +
                "{'type':'OBJECT','properties':{'city':{'type':'STRING'}},'required':['city']}," +
                "{'type':'ARRAY','items':{'type':'NUMBER'}},{'type':'NULL'}]}}}}"),
            json("{'type':'function','function':{'name':'f','parameters':{'type':'object','properties':{'x':{'anyOf':[" +
                "{'type':'object','properties':{'city':{'type':'string'}},'required':['city']}," +
                "{'type':'array','items':{'type':'number'}},{'type':'null'}]}}}}}"));
    }

    @Test
    public void shouldLowercaseTypesInNestedAnyOf()
    {
        assertTransforms(to,
            json("{'name':'f','parameters':{'anyOf':[{'type':'OBJECT','properties':{'y':{'anyOf':[" +
                "{'type':'STRING'},{'anyOf':[{'type':'BOOLEAN'},{'type':'INTEGER'}]}]}}},{'type':'STRING'}]}}"),
            json("{'type':'function','function':{'name':'f','parameters':{'anyOf':[" +
                "{'type':'object','properties':{'y':{'anyOf':[{'type':'string'}," +
                "{'anyOf':[{'type':'boolean'},{'type':'integer'}]}]}}},{'type':'string'}]}}}"));
    }

    @Test
    public void shouldLowercaseTypesInOneOfAndAllOf()
    {
        assertTransforms(to,
            json("{'name':'f','parameters':{'oneOf':[{'type':'STRING'},{'type':'NUMBER'}]," +
                "'allOf':[{'type':'OBJECT'},{'properties':{'a':{'type':'INTEGER'}}}]}}"),
            json("{'type':'function','function':{'name':'f','parameters':{'oneOf':[{'type':'string'},{'type':'number'}]," +
                "'allOf':[{'type':'object'},{'properties':{'a':{'type':'integer'}}}]}}}"));
    }

    @Test
    public void shouldLowercaseTypesInAdditionalProperties()
    {
        assertTransforms(to,
            json("{'name':'f','parameters':{'type':'OBJECT','additionalProperties':{'type':'OBJECT'," +
                "'additionalProperties':{'type':'STRING'}}}}"),
            json("{'type':'function','function':{'name':'f','parameters':{'type':'object','additionalProperties':{" +
                "'type':'object','additionalProperties':{'type':'string'}}}}}"));
    }

    @Test
    public void shouldKeepBooleanAdditionalProperties()
    {
        assertTransforms(to,
            json("{'name':'f','parameters':{'type':'OBJECT','properties':{'a':{'type':'OBJECT'," +
                "'additionalProperties':false}},'additionalProperties':true}}"),
            json("{'type':'function','function':{'name':'f','parameters':{'type':'object','properties':{'a':{'type':'object'," +
                "'additionalProperties':false}},'additionalProperties':true}}}"));
    }

    @Test
    public void shouldLowercaseTypesInDefinitions()
    {
        assertTransforms(to,
            json("{'name':'f','parameters':{'type':'OBJECT','$defs':{'Name':{'type':'STRING'}," +
                "'Pair':{'type':'OBJECT','properties':{'a':{'type':'INTEGER'}}}}," +
                "'definitions':{'Flag':{'type':'BOOLEAN'}}}}"),
            json("{'type':'function','function':{'name':'f','parameters':{'type':'object','$defs':{'Name':{'type':'string'}," +
                "'Pair':{'type':'object','properties':{'a':{'type':'integer'}}}}," +
                "'definitions':{'Flag':{'type':'boolean'}}}}}"));
    }

    @Test
    public void shouldKeepDefinitionNamesThatAreKeywords()
    {
        assertTransforms(to,
            json("{'name':'f','parameters':{'type':'OBJECT','$defs':{'type':{'type':'STRING'}," +
                "'anyOf':{'type':'OBJECT','properties':{'items':{'type':'ARRAY','items':{'type':'INTEGER'}}}}," +
                "'properties':{'type':'NUMBER'},'$defs':{'type':'BOOLEAN'}}}}"),
            json("{'type':'function','function':{'name':'f','parameters':{'type':'object','$defs':{'type':{'type':'string'}," +
                "'anyOf':{'type':'object','properties':{'items':{'type':'array','items':{'type':'integer'}}}}," +
                "'properties':{'type':'number'},'$defs':{'type':'boolean'}}}}}"));
    }

    @Test
    public void shouldKeepPropertyNamesThatAreKeywords()
    {
        assertTransforms(to,
            json("{'name':'f','parameters':{'type':'OBJECT','properties':{'anyOf':{'type':'STRING'}," +
                "'additionalProperties':{'type':'NUMBER'}," +
                "'properties':{'type':'OBJECT','properties':{'type':{'type':'BOOLEAN'}}}}}}"),
            json("{'type':'function','function':{'name':'f','parameters':{'type':'object','properties':{" +
                "'anyOf':{'type':'string'},'additionalProperties':{'type':'number'}," +
                "'properties':{'type':'object','properties':{'type':{'type':'boolean'}}}}}}}"));
    }

    @Test
    public void shouldLowercaseTypesInPatternProperties()
    {
        assertTransforms(to,
            json("{'name':'f','parameters':{'type':'OBJECT','patternProperties':{'^x_':{'type':'STRING'}}}}"),
            json("{'type':'function','function':{'name':'f','parameters':{'type':'object'," +
                "'patternProperties':{'^x_':{'type':'string'}}}}}"));
    }

    @Test
    public void shouldLowercaseTypesInTupleItems()
    {
        assertTransforms(to,
            json("{'name':'f','parameters':{'type':'ARRAY','items':[{'type':'STRING'},{'type':'NUMBER'}]}}"),
            json("{'type':'function','function':{'name':'f','parameters':{'type':'array'," +
                "'items':[{'type':'string'},{'type':'number'}]}}}"));
    }

    @Test
    public void shouldLowercaseArrayValuedType()
    {
        assertTransforms(to,
            json("{'name':'f','parameters':{'type':['OBJECT','NULL'],'properties':{'a':{'type':['STRING','NULL']}," +
                "'b':{'type':['integer','NULL','custom']}}}}"),
            json("{'type':'function','function':{'name':'f','parameters':{'type':['object','null'],'properties':{" +
                "'a':{'type':['string','null']},'b':{'type':['integer','null','custom']}}}}}"));
    }

    @Test
    public void shouldLowercaseEmptyArrayValuedType()
    {
        assertTransforms(to,
            json("{'name':'f','parameters':{'type':[],'properties':{'a':{'type':'STRING'}}}}"),
            json("{'type':'function','function':{'name':'f','parameters':{'type':[],'properties':{'a':{'type':'string'}}}}}"));
    }

    @Test
    public void shouldRejectTypeThatIsNeitherStringNorArray()
    {
        assertRejects(to, json("{'name':'f','parameters':{'type':{'a':1}}}"));
        assertRejects(to, json("{'name':'f','parameters':{'type':5}}"));
        assertRejects(to, json("{'name':'f','parameters':{'type':['STRING',5]}}"));
    }

    @Test
    public void shouldKeepLowercaseTypesUnchanged()
    {
        final String schema = "{'type':'object','properties':{'a':{'anyOf':[{'type':'string'},{'type':['integer','null']}]}," +
            "'m':{'type':'object','additionalProperties':{'type':'number'}}},'$defs':{'D':{'type':'boolean'}}}";

        assertTransforms(to,
            json("{'name':'f','parameters':" + schema + "}"),
            json("{'type':'function','function':{'name':'f','parameters':" + schema + "}}"));
    }

    @Test
    public void shouldKeepValuesThatAreNotSchemas()
    {
        assertTransforms(to,
            json("{'name':'f','parameters':{'type':'OBJECT','required':['STRING'],'properties':{'a':{'type':'STRING'," +
                "'enum':['STRING','NUMBER'],'default':{'type':'STRING','anyOf':[{'type':'OBJECT'}]}," +
                "'example':[{'type':'ARRAY','items':{'type':'NULL'}}],'description':'STRING','title':'OBJECT'}}}}"),
            json("{'type':'function','function':{'name':'f','parameters':{'type':'object','required':['STRING'],'properties':{" +
                "'a':{'type':'string','enum':['STRING','NUMBER'],'default':{'type':'STRING','anyOf':[{'type':'OBJECT'}]}," +
                "'example':[{'type':'ARRAY','items':{'type':'NULL'}}],'description':'STRING','title':'OBJECT'}}}}}"));
    }

    @Test
    public void shouldKeepBooleanSchemas()
    {
        assertTransforms(to,
            json("{'name':'f','parameters':{'type':'OBJECT','properties':{'a':true,'b':{'type':'STRING'}}," +
                "'items':false,'anyOf':[true,{'type':'STRING'}]}}"),
            json("{'type':'function','function':{'name':'f','parameters':{'type':'object','properties':{'a':true," +
                "'b':{'type':'string'}},'items':false,'anyOf':[true,{'type':'string'}]}}}"));
    }

    @Test
    public void shouldLowercaseTypesNestedPastTheFormerNamesBound()
    {
        assertTransforms(to,
            json("{'name':'f','parameters':" +
                nested("{'type':'OBJECT','properties':{'p':", "{'type':'ARRAY','items':{'type':'NUMBER'}}") + "}"),
            json("{'type':'function','function':{'name':'f','parameters':" +
                nested("{'type':'object','properties':{'p':", "{'type':'array','items':{'type':'number'}}") + "}}"));
    }

    @Test
    public void shouldKeepPropertyNamesThatAreKeywordsPastTheFormerNamesBound()
    {
        assertTransforms(to,
            json("{'name':'f','parameters':" +
                nested("{'type':'OBJECT','properties':{'p':",
                    "{'type':'OBJECT','properties':{'type':true,'items':false}}") + "}"),
            json("{'type':'function','function':{'name':'f','parameters':" +
                nested("{'type':'object','properties':{'p':", "{'type':'object','properties':{'type':true,'items':false}}") +
                "}}"));
    }

    @Test
    public void shouldLowercaseTypesIndependentOfDefaultLocale()
    {
        final Locale locale = Locale.getDefault();

        try
        {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));

            assertTransforms(to,
                json("{'name':'f','parameters':{'type':'OBJECT','properties':{'a':{'type':'INTEGER'}}}}"),
                json("{'type':'function','function':{'name':'f','parameters':{'type':'object','properties':{" +
                    "'a':{'type':'integer'}}}}}"));
        }
        finally
        {
            Locale.setDefault(locale);
        }
    }

    @Test
    public void shouldPassJsonSchemaByteForByte()
    {
        final String schema = "{'type':'OBJECT','properties':{'x':{'anyOf':[{'type':'STRING'},{'type':['INTEGER','NULL']}]}}," +
            "'additionalProperties':{'type':'NUMBER'},'$defs':{'D':{'type':'BOOLEAN'}}}";

        assertTransforms(to,
            json("{'name':'f','parametersJsonSchema':" + schema + "}"),
            json("{'type':'function','function':{'name':'f','parameters':" + schema + "}}"));
        assertTransforms(to,
            json("{'name':'f','parameters_json_schema':" + schema + "}"),
            json("{'type':'function','function':{'name':'f','parameters':" + schema + "}}"));
    }

    @Test
    public void shouldKeepOpenApiTypeThatIsNotAnEnumName()
    {
        assertTransforms(to,
            json("{'name':'f','parameters':{'type':'custom','description':'TYPE_UNSPECIFIED'}}"),
            json("{'type':'function','function':{'name':'f','parameters':{'type':'custom','description':'TYPE_UNSPECIFIED'}}}"));
    }

    @Test
    public void shouldWrapOpenApiParametersLargerThanWindow()
    {
        assertTransforms(to,
            json("{'name':'f','parameters':{'type':'OBJECT','description':'" + HUGE + "','properties':{'a':{'type':'STRING'}}}}"),
            json("{'type':'function','function':{'name':'f','parameters':{'type':'object','description':'" + HUGE +
                "','properties':{'a':{'type':'string'}}}}}"));
    }

    @Test
    public void shouldWrapMinimalDeclaration()
    {
        assertTransforms(to,
            json("{'name':'ping'}"),
            json("{'type':'function','function':{'name':'ping'}}"));
    }

    @Test
    public void shouldDropNonStringDescription()
    {
        assertTransforms(to,
            json("{'name':'ping','description':null}"),
            json("{'type':'function','function':{'name':'ping'}}"));
    }

    @Test
    public void shouldCarryNativeOnlyMembers()
    {
        assertTransforms(to,
            json("{'name':'ping','behavior':'NON_BLOCKING','parametersJsonSchema':{'a':1}}"),
            json("{'type':'function','function':{'name':'ping','gemini:behavior':'NON_BLOCKING','parameters':{'a':1}}}"));
    }

    @Test
    public void shouldWrapParametersLargerThanWindow()
    {
        assertTransforms(to,
            json("{'name':'ping','parametersJsonSchema':{'description':'" + HUGE + "'}}"),
            json("{'type':'function','function':{'name':'ping','parameters':{'description':'" + HUGE + "'}}}"));
    }

    @Test
    public void shouldRejectDeclarationWithoutName()
    {
        assertRejects(to, json("{'description':'Get the weather'}"));
    }

    @Test
    public void shouldUnwrapFunctionAsDeclaration()
    {
        assertTransforms(from,
            json("{'type':'function','function':{'name':'get_weather','description':'Get the weather'," +
                "'parameters':{'type':'object'}}}"),
            json("{'name':'get_weather','description':'Get the weather','parametersJsonSchema':{'type':'object'}}"));
    }

    @Test
    public void shouldUnwrapFunctionWithoutParameters()
    {
        assertTransforms(from,
            json("{'type':'function','function':{'name':'ping'}}"),
            json("{'name':'ping'}"));
    }

    @Test
    public void shouldRestoreOwnPrefixedMembers()
    {
        assertTransforms(from,
            json("{'type':'function','function':{'name':'ping','gemini:behavior':'NON_BLOCKING','openai:strict':true}}"),
            json("{'name':'ping','behavior':'NON_BLOCKING'}"));
    }

    @Test
    public void shouldRejectFunctionWithoutName()
    {
        assertRejects(from, json("{'type':'function','function':{'description':'x'}}"));
    }

    @Test
    public void shouldRoundTripDeclaration()
    {
        assertRoundTrips(to, from,
            json("{'name':'get_weather','description':'Get the weather','behavior':'NON_BLOCKING'," +
                "'parametersJsonSchema':{'type':'object','properties':{'city':{'type':'string'}}}}"));
    }

    private static String nested(
        String open,
        String innermost)
    {
        return open.repeat(NESTED_LEVELS) + innermost + "}}".repeat(NESTED_LEVELS);
    }
}
