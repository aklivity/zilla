/*
 * Copyright 2021-2022 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
grammar Cog;

specification
   : scope
   ;

scope
   : KW_SCOPE ID LEFT_BRACE option * definition * RIGHT_BRACE
   ;

option
   : KW_OPTION optionByteOrder SEMICOLON
   ;

optionByteOrder
   : KW_BYTEORDER (KW_NATIVE | KW_NETWORK)
   ;

scoped_name
   : (DOUBLE_COLON)? ID (DOUBLE_COLON ID)*
   ;

definition
   : type_decl
   | scope
   ;

positive_int_const
   : HEX_LITERAL
   | UNSIGNED_INTEGER_LITERAL
   ;

type_decl
   : constr_type_spec
   ;

type_declarator
   : type_spec declarators
   ;

type_spec
   : simple_type_spec
   | constr_type_spec
   ;

simple_type_spec
   : base_type_spec
   | scoped_name
   ;

base_type_spec
   : integer_type
   | octets_type
   | string8_type
   | string16_type
   | string32_type
   | varstring_type
   ;

constr_type_spec
   : enum_type
   | struct_type
   | list_type
   | union_type
   | variant_type
   | typedef_type
   | map_type
   ;

declarators
   : declarator (COMMA declarator)*
   ;

declarator
   : ID
   ;

integer_type
   : signed_integer_type
   | unsigned_integer_type
   ;

signed_integer_type
   : int8_type
   | int16_type
   | int24_type
   | int32_type
   | int64_type
   | varint32_type
   | varint64_type
   | varuint32n_type
   ;

unsigned_integer_type
   : uint8_type
   | uint16_type
   | uint24_type
   | uint32_type
   | uint64_type
   | varuint32_type
   ;

int8_type
   : KW_INT8
   ;

int16_type
   : KW_INT16
   ;

int24_type
   : KW_INT24
   ;

int32_type
   : KW_INT32
   ;

int64_type
   : KW_INT64
   ;

uint8_type
   : KW_UINT8
   ;

uint16_type
   : KW_UINT16
   ;

uint24_type
   : KW_UINT24
   ;

uint32_type
   : KW_UINT32
   ;

uint64_type
   : KW_UINT64
   ;
   
varuint32_type
   : KW_VARUINT32
   ;

varuint32n_type
   : KW_VARUINT32N
   ;

varint32_type
   : KW_VARINT32
   ;
   
varint64_type
   : KW_VARINT64
   ;

octets_type
   : KW_OCTETS LEFT_SQUARE_BRACKET (positive_int_const | ID) RIGHT_SQUARE_BRACKET
   ;

unbounded_octets_type
   : KW_OCTETS
   ;

enum_type
   : KW_ENUM ID (LEFT_BRACKET enum_explicit_type RIGHT_BRACKET)? LEFT_BRACE enum_values RIGHT_BRACE
   ;

enum_explicit_type
   : int8_type
   | int16_type
   | int24_type
   | int32_type
   | int64_type
   | unsigned_integer_type
   | string8_type
   | string16_type
   | string32_type
   | declarator
   ;

enum_values
   : enum_value_non_terminal * enum_value_terminal
   ;

enum_value_non_terminal
   : enum_value COMMA
   ;

enum_value_terminal
   : enum_value
   ;

enum_value
   : ID (LEFT_BRACKET (int_literal | string_literal) RIGHT_BRACKET)?
   ;

struct_type
   : KW_STRUCT ID (KW_EXTENDS scoped_name)? (LEFT_SQUARE_BRACKET type_id RIGHT_SQUARE_BRACKET)? LEFT_BRACE member_list RIGHT_BRACE
   ;

type_id
   : uint_literal
   ;
   
member_list
   : member * unbounded_member?
   ;

member
   : type_spec declarators SEMICOLON
   | uint_member_with_default SEMICOLON
   | int_member_with_default SEMICOLON
   | string_member_with_default SEMICOLON
   | string_member_with_null_default SEMICOLON
   | octets_member_with_default SEMICOLON
   | enum_member_with_default SEMICOLON
   | integer_array_member SEMICOLON
   | varint_array_member SEMICOLON
   | array_member SEMICOLON
   ;
   
uint_member_with_default
   : unsigned_integer_type declarator EQUALS uint_literal
   ;

int_member_with_default 
   : signed_integer_type declarator EQUALS int_literal
   ;

string_member_with_default
   : string8_type declarator EQUALS string_literal
   | string16_type declarator EQUALS string_literal
   | string32_type declarator EQUALS string_literal
   | varstring_type declarator EQUALS string_literal
   ;

string_member_with_null_default
   : string8_type declarator default_null
   | string16_type declarator default_null
   | string32_type declarator default_null
   | varstring_type declarator default_null
   ;

octets_member_with_default
   : octets_type declarator default_null
   ;

enum_member_with_default
   : scoped_name declarator EQUALS ID
   ;

integer_array_member
   : int8_type LEFT_SQUARE_BRACKET (positive_int_const | ID) RIGHT_SQUARE_BRACKET declarator default_null?
   | int16_type LEFT_SQUARE_BRACKET (positive_int_const | ID) RIGHT_SQUARE_BRACKET declarator default_null?
   | int24_type LEFT_SQUARE_BRACKET (positive_int_const | ID) RIGHT_SQUARE_BRACKET declarator default_null?
   | int32_type LEFT_SQUARE_BRACKET (positive_int_const | ID) RIGHT_SQUARE_BRACKET declarator default_null?
   | int64_type LEFT_SQUARE_BRACKET (positive_int_const | ID) RIGHT_SQUARE_BRACKET declarator default_null?
   | uint8_type LEFT_SQUARE_BRACKET (positive_int_const | ID) RIGHT_SQUARE_BRACKET declarator default_null?
   | uint16_type LEFT_SQUARE_BRACKET (positive_int_const | ID) RIGHT_SQUARE_BRACKET declarator default_null?
   | uint24_type LEFT_SQUARE_BRACKET (positive_int_const | ID) RIGHT_SQUARE_BRACKET declarator default_null?
   | uint32_type LEFT_SQUARE_BRACKET (positive_int_const | ID) RIGHT_SQUARE_BRACKET declarator default_null?
   | uint64_type LEFT_SQUARE_BRACKET (positive_int_const | ID) RIGHT_SQUARE_BRACKET declarator default_null?
   ;

varint_array_member
   : varint32_type LEFT_SQUARE_BRACKET RIGHT_SQUARE_BRACKET declarator default_null?
   | varint64_type LEFT_SQUARE_BRACKET RIGHT_SQUARE_BRACKET declarator default_null?
   ;

array_member
   : array_type declarators
   ;

default_null
   : '= null'
   ;

list_type
   : KW_LIST (list_params)? ID (list_using)? LEFT_BRACE list_members RIGHT_BRACE
   ;

list_params
   : LEFT_ANG_BRACKET unsigned_integer_type COMMA unsigned_integer_type (COMMA uint_literal)? RIGHT_ANG_BRACKET
   ;

list_using
   : KW_USING declarator
   ;

list_members
   : list_member* list_unbounded_member?
   ;

list_member
   : KW_REQUIRED? type_spec declarators SEMICOLON
   | uint_member_with_default SEMICOLON
   | int_member_with_default SEMICOLON
   | octets_member_with_default SEMICOLON
   | non_primitive_member_with_default SEMICOLON
   | KW_REQUIRED? integer_array_member SEMICOLON
   | KW_REQUIRED? varint_array_member SEMICOLON
   | KW_REQUIRED? array_member SEMICOLON
   | KW_REQUIRED? member_with_parametric_type SEMICOLON
   ;

non_primitive_member_with_default
   : type_spec declarator EQUALS (ID | int_literal)
   ;

list_unbounded_member
   : KW_REQUIRED? unbounded_octets_member
   ;

unbounded_member
   : unbounded_octets_member
   ;
   
unbounded_octets_member
   : unbounded_octets_type declarators SEMICOLON
   ;

member_with_parametric_type
   : membertype=declarator LEFT_ANG_BRACKET param1=declarator (COMMA param2=declarator)? RIGHT_ANG_BRACKET name=declarator
   ;

map_type
   : KW_MAP ID LEFT_ANG_BRACKET keytype=scoped_name COMMA valuetype=scoped_name RIGHT_ANG_BRACKET KW_USING
   templatetype=scoped_name SEMICOLON
   ;

union_type
   : KW_UNION ID KW_SWITCH LEFT_BRACKET (KW_UINT8 | kindtype=scoped_name) RIGHT_BRACKET (KW_EXTENDS supertype=scoped_name)?
   LEFT_BRACE case_list RIGHT_BRACE
   ;

case_list
   : case_member *
   ;

case_member
   : KW_CASE (uint_literal | ID) COLON member
   ;

variant_type
   : KW_VARIANT ID KW_SWITCH LEFT_BRACKET kind RIGHT_BRACKET KW_OF variant_of_type LEFT_BRACE variant_case_list RIGHT_BRACE
   | KW_VARIANT ID KW_SWITCH LEFT_BRACKET kind RIGHT_BRACKET LEFT_BRACE variant_case_list_without_of RIGHT_BRACE
   ;

kind
   : KW_UINT8
   | scoped_name
   ;

variant_of_type
   : integer_type
   | string_type
   | list_keyword
   | array_keyword
   | map_keyword
   | octets_keyword
   ;

variant_case_list
   : variant_case_member *
   ;

variant_case_list_without_of
   : (variant_case_member_no_type * variant_case_member_without_of +) +
   ;

variant_case_member
   : KW_CASE variant_case_value COLON variant_member SEMICOLON
   ;

variant_case_member_no_type
   : KW_CASE variant_case_value COLON
   ;

variant_case_member_without_of
   : KW_CASE variant_case_value COLON variant_member_without_of SEMICOLON
   ;

variant_case_value
   : uint_literal
   | declarator
   ;

variant_member
   : integer_type
   | string8_type
   | string16_type
   | string32_type
   | variant_int_literal
   | variant_list_member
   | variant_array_member
   | variant_map_member
   | variant_octets_member
   | defined_variant_member
   ;

variant_member_without_of
   : variant_member
   | defined_variant_member
   | defined_variant_member_with_parametric_type
   ;

defined_variant_member
   : declarator
   ;

defined_variant_member_with_parametric_type
   :  membertype=declarator LEFT_ANG_BRACKET param1=declarator (COMMA param2=declarator)? RIGHT_ANG_BRACKET
   ;

variant_list_member
   : KW_LIST LEFT_ANG_BRACKET uint32_type COMMA uint32_type (COMMA uint_literal)? RIGHT_ANG_BRACKET
   | KW_LIST LEFT_ANG_BRACKET uint8_type COMMA uint8_type (COMMA uint_literal)? RIGHT_ANG_BRACKET
   | KW_LIST LEFT_ANG_BRACKET UNSIGNED_INTEGER_LITERAL COMMA UNSIGNED_INTEGER_LITERAL RIGHT_ANG_BRACKET
   ;

variant_array_member
   : KW_ARRAY LEFT_ANG_BRACKET uint32_type COMMA uint32_type RIGHT_ANG_BRACKET
   | KW_ARRAY LEFT_ANG_BRACKET uint16_type COMMA uint16_type RIGHT_ANG_BRACKET
   | KW_ARRAY LEFT_ANG_BRACKET uint8_type COMMA uint8_type RIGHT_ANG_BRACKET
   ;

variant_map_member
   : KW_MAP LEFT_ANG_BRACKET uint32_type RIGHT_ANG_BRACKET
   | KW_MAP LEFT_ANG_BRACKET uint16_type RIGHT_ANG_BRACKET
   | KW_MAP LEFT_ANG_BRACKET uint8_type RIGHT_ANG_BRACKET
   ;

variant_octets_member
   : KW_OCTETS LEFT_SQUARE_BRACKET uint32_type RIGHT_SQUARE_BRACKET
   | KW_OCTETS LEFT_SQUARE_BRACKET uint16_type RIGHT_SQUARE_BRACKET
   | KW_OCTETS LEFT_SQUARE_BRACKET uint8_type RIGHT_SQUARE_BRACKET
   ;

typedef_type
   : KW_TYPEDEF originaltype=ID KW_AS typedeftype=ID SEMICOLON
   | KW_TYPEDEF originaltype=ID LEFT_ANG_BRACKET keytype=ID COMMA valuetype=ID RIGHT_ANG_BRACKET KW_AS typedeftype=ID
   LEFT_ANG_BRACKET valuetype=ID RIGHT_ANG_BRACKET SEMICOLON
   ;

array_type
   : simple_type_spec LEFT_SQUARE_BRACKET RIGHT_SQUARE_BRACKET
   ;

map_keyword
    : KW_MAP
    ;

string_type
   : KW_STRING
   ;

string8_type
   : /* KW_STRING LEFT_ANG_BRACKET positive_int_const RIGHT_ANG_BRACKET
   | */ KW_STRING8
   ;

string16_type
   : /* KW_STRING16 LEFT_ANG_BRACKET positive_int_const RIGHT_ANG_BRACKET
   | */ KW_STRING16
   ;

string32_type
   : KW_STRING32
   ;

varstring_type
   : KW_VARSTRING
   ;

list_keyword
   : KW_LIST
   ;

array_keyword
   : KW_ARRAY
   ;

octets_keyword
   : KW_OCTETS LEFT_SQUARE_BRACKET RIGHT_SQUARE_BRACKET
   ;

int_literal
   : MINUS ? uint_literal
   ;

uint_literal
   : UNSIGNED_INTEGER_LITERAL
   | HEX_LITERAL
   ;

string_literal
   : STRING_LITERAL
   ;

variant_int_literal
   : UNSIGNED_INTEGER_LITERAL
   ;


UNSIGNED_INTEGER_LITERAL
   : ('0' | '1' .. '9' '0' .. '9'*) INTEGER_TYPE_SUFFIX?
   ;

HEX_LITERAL
   : '0' ('x' | 'X') HEX_DIGIT+ INTEGER_TYPE_SUFFIX?
   ;

STRING_LITERAL
   : QUOTE (~["\r\n])* QUOTE
   ;


fragment HEX_DIGIT
   : ('0' .. '9' | 'a' .. 'f' | 'A' .. 'F' | '_')
   ;


fragment INTEGER_TYPE_SUFFIX
   : ('l' | 'L')
   ;


fragment LETTER
   : '\u0024' | '\u0041' .. '\u005a' | '\u005f' | '\u0061' .. '\u007a' | '\u00c0' .. '\u00d6' | '\u00d8' .. '\u00f6' | '\u00f8' .. '\u00ff' | '\u0100' .. '\u1fff' | '\u3040' .. '\u318f' | '\u3300' .. '\u337f' | '\u3400' .. '\u3d2d' | '\u4e00' .. '\u9fff' | '\uf900' .. '\ufaff'
   ;


fragment ID_DIGIT
   : '\u0030' .. '\u0039' | '\u0660' .. '\u0669' | '\u06f0' .. '\u06f9' | '\u0966' .. '\u096f' | '\u09e6' .. '\u09ef' | '\u0a66' .. '\u0a6f' | '\u0ae6' .. '\u0aef' | '\u0b66' .. '\u0b6f' | '\u0be7' .. '\u0bef' | '\u0c66' .. '\u0c6f' | '\u0ce6' .. '\u0cef' | '\u0d66' .. '\u0d6f' | '\u0e50' .. '\u0e59' | '\u0ed0' .. '\u0ed9' | '\u1040' .. '\u1049'
   ;


MINUS
   : '-'
   ;


SEMICOLON
   : ';'
   ;


COLON
   : ':'
   ;


COMMA
   : ','
   ;


EQUALS
   : '='
   ;


LEFT_BRACE
   : '{'
   ;


RIGHT_BRACE
   : '}'
   ;


LEFT_SQUARE_BRACKET
   : '['
   ;


RIGHT_SQUARE_BRACKET
   : ']'
   ;


LEFT_BRACKET
   : '('
   ;


RIGHT_BRACKET
   : ')'
   ;


QUOTE
   : '"'
   ;


SLASH
   : '/'
   ;


LEFT_ANG_BRACKET
   : '<'
   ;


RIGHT_ANG_BRACKET
   : '>'
   ;


DOUBLE_COLON
   : '::'
   ;


KW_STRING
   : 'string'
   ;


KW_STRING8
   : 'string8'
   ;


KW_STRING16
   : 'string16'
   ;


KW_STRING32
   : 'string32'
   ;


KW_VARSTRING
   : 'varstring'
   ;


KW_SWITCH
   : 'switch'
   ;


KW_OF
   : 'of'
   ;


KW_CASE
   : 'case'
   ;


KW_DEFAULT
   : 'default'
   ;


KW_LIST
   : 'list'
   ;


KW_USING
   : 'using'
   ;


KW_ARRAY
   : 'array'
   ;


KW_MAP
   : 'map'
   ;


KW_REQUIRED
   : 'required'
   ;


KW_OCTETS
   : 'octets'
   ;


KW_ENUM
   : 'enum'
   ;


KW_STRUCT
   : 'struct'
   ;


KW_EXTENDS
   : 'extends'
   ;


KW_READONLY
   : 'readonly'
   ;


KW_INT8
   : 'int8'
   ;


KW_INT16
   : 'int16'
   ;


KW_INT24
   : 'int24'
   ;


KW_INT32
   : 'int32'
   ;


KW_INT64
   : 'int64'
   ;


KW_UINT8
   : 'uint8'
   ;


KW_UINT16
   : 'uint16'
   ;


KW_UINT24
   : 'uint24'
   ;


KW_UINT32
   : 'uint32'
   ;


KW_UINT64
   : 'uint64'
   ;


KW_VARUINT32
   : 'varuint32'
   ;


KW_VARUINT32N
   : 'varuint32n'
   ;


KW_VARINT32
   : 'varint32'
   ;


KW_VARINT64
   : 'varint64'
   ;


KW_UNION
   : 'union'
   ;


KW_VARIANT
   : 'variant'
   ;


KW_TYPEDEF
   : 'typedef'
   ;


KW_AS
   : 'as'
   ;


KW_SCOPE
   : 'scope'
   ;


KW_OPTION
   : 'option'
   ;


KW_BYTEORDER
   : 'byteorder'
   ;


KW_NATIVE
   : 'native'
   ;


KW_NETWORK
   : 'network'
   ;


ID
   : LETTER (LETTER | ID_DIGIT)*
   ;


WS
   : (' ' | '\r' | '\t' | '\u000C' | '\n') -> channel (HIDDEN)
   ;


COMMENT
   : '/*' .*? '*/' -> channel (HIDDEN)
   ;


LINE_COMMENT
   : '//' ~ ('\n' | '\r')* '\r'? '\n' -> channel (HIDDEN)
   ;
