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
package io.aklivity.zilla.runtime.model.protobuf.internal;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ProtobufSyntaxDetector
{
    private static final Pattern SYNTAX_PATTERN = Pattern.compile(
        "^\\s*syntax\\s*=\\s*[\"'](proto2|proto3)[\"']\\s*;", 
        Pattern.MULTILINE | Pattern.CASE_INSENSITIVE
    );
    
    private static final String PROTO2_SYNTAX = "proto2";
    private static final String PROTO3_SYNTAX = "proto3";
    
    public enum ProtoSyntax
    {
        PROTO2,
        PROTO3,
        UNKNOWN
    }
    
    private ProtobufSyntaxDetector()
    {
        // Utility class
    }
    
    /**
     * Detects the protobuf syntax version from the schema text.
     * 
     * @param schemaText the protobuf schema text
     * @return the detected syntax version
     */
    public static ProtoSyntax detectSyntax(String schemaText)
    {
        if (schemaText == null || schemaText.isEmpty())
        {
            return ProtoSyntax.UNKNOWN;
        }
        
        Matcher matcher = SYNTAX_PATTERN.matcher(schemaText);
        if (matcher.find())
        {
            String syntax = matcher.group(1).toLowerCase();
            if (PROTO2_SYNTAX.equals(syntax))
            {
                return ProtoSyntax.PROTO2;
            }
            else if (PROTO3_SYNTAX.equals(syntax))
            {
                return ProtoSyntax.PROTO3;
            }
        }
        
        // If no syntax is specified, return UNKNOWN
        return ProtoSyntax.UNKNOWN;
    }
    
    /**
     * Converts a string representation to ProtoSyntax enum.
     * 
     * @param syntax the syntax string ("proto2", "proto3")
     * @return the corresponding ProtoSyntax enum value
     */
    public static ProtoSyntax fromString(String syntax)
    {
        if (syntax == null)
        {
            return ProtoSyntax.UNKNOWN;
        }
        
        String lowerSyntax = syntax.toLowerCase();
        if (PROTO2_SYNTAX.equals(lowerSyntax))
        {
            return ProtoSyntax.PROTO2;
        }
        else if (PROTO3_SYNTAX.equals(lowerSyntax))
        {
            return ProtoSyntax.PROTO3;
        }
        
        return ProtoSyntax.UNKNOWN;
    }
}
