/*
 * Copyright 2021-2021 Aklivity Inc.
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
package io.aklivity.zilla.runtime.cog.http.internal.hpack;

import static java.nio.ByteOrder.BIG_ENDIAN;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

public final class HpackHuffman
{

    private static final int[][] CODES =
    {
        /*                                                          code
                                    code as bits                 as hex    len
               sym                 aligned to MSB                aligned    in
                                                                 to LSB    bits
         */
        /*    (  0)  |11111111|11000                      */      {0x1ff8, 13},
        /*    (  1)  |11111111|11111111|1011000           */    {0x7fffd8, 23},
        /*    (  2)  |11111111|11111111|11111110|0010     */   {0xfffffe2, 28},
        /*    (  3)  |11111111|11111111|11111110|0011     */   {0xfffffe3, 28},
        /*    (  4)  |11111111|11111111|11111110|0100     */   {0xfffffe4, 28},
        /*    (  5)  |11111111|11111111|11111110|0101     */   {0xfffffe5, 28},
        /*    (  6)  |11111111|11111111|11111110|0110     */   {0xfffffe6, 28},
        /*    (  7)  |11111111|11111111|11111110|0111     */   {0xfffffe7, 28},
        /*    (  8)  |11111111|11111111|11111110|1000     */   {0xfffffe8, 28},
        /*    (  9)  |11111111|11111111|11101010          */    {0xffffea, 24},
        /*    ( 10)  |11111111|11111111|11111111|111100   */  {0x3ffffffc, 30},
        /*    ( 11)  |11111111|11111111|11111110|1001     */   {0xfffffe9, 28},
        /*    ( 12)  |11111111|11111111|11111110|1010     */   {0xfffffea, 28},
        /*    ( 13)  |11111111|11111111|11111111|111101   */  {0x3ffffffd, 30},
        /*    ( 14)  |11111111|11111111|11111110|1011     */   {0xfffffeb, 28},
        /*    ( 15)  |11111111|11111111|11111110|1100     */   {0xfffffec, 28},
        /*    ( 16)  |11111111|11111111|11111110|1101     */   {0xfffffed, 28},
        /*    ( 17)  |11111111|11111111|11111110|1110     */   {0xfffffee, 28},
        /*    ( 18)  |11111111|11111111|11111110|1111     */   {0xfffffef, 28},
        /*    ( 19)  |11111111|11111111|11111111|0000     */   {0xffffff0, 28},
        /*    ( 20)  |11111111|11111111|11111111|0001     */   {0xffffff1, 28},
        /*    ( 21)  |11111111|11111111|11111111|0010     */   {0xffffff2, 28},
        /*    ( 22)  |11111111|11111111|11111111|111110   */  {0x3ffffffe, 30},
        /*    ( 23)  |11111111|11111111|11111111|0011     */   {0xffffff3, 28},
        /*    ( 24)  |11111111|11111111|11111111|0100     */   {0xffffff4, 28},
        /*    ( 25)  |11111111|11111111|11111111|0101     */   {0xffffff5, 28},
        /*    ( 26)  |11111111|11111111|11111111|0110     */   {0xffffff6, 28},
        /*    ( 27)  |11111111|11111111|11111111|0111     */   {0xffffff7, 28},
        /*    ( 28)  |11111111|11111111|11111111|1000     */   {0xffffff8, 28},
        /*    ( 29)  |11111111|11111111|11111111|1001     */   {0xffffff9, 28},
        /*    ( 30)  |11111111|11111111|11111111|1010     */   {0xffffffa, 28},
        /*    ( 31)  |11111111|11111111|11111111|1011     */   {0xffffffb, 28},
        /*' ' ( 32)  |010100                              */         {0x14, 6},
        /*'!' ( 33)  |11111110|00                         */       {0x3f8, 10},
        /*'"' ( 34)  |11111110|01                         */       {0x3f9, 10},
        /*'#' ( 35)  |11111111|1010                       */       {0xffa, 12},
        /*'$' ( 36)  |11111111|11001                      */      {0x1ff9, 13},
        /*'%' ( 37)  |010101                              */         {0x15, 6},
        /*'&' ( 38)  |11111000                            */         {0xf8, 8},
        /*''' ( 39)  |11111111|010                        */        {0x7fa, 11},
        /*'(' ( 40)  |11111110|10                         */       {0x3fa, 10},
        /*')' ( 41)  |11111110|11                         */       {0x3fb, 10},
        /*'*' ( 42)  |11111001                            */         {0xf9, 8},
        /*'+' ( 43)  |11111111|011                        */       {0x7fb, 11},
        /*',' ( 44)  |11111010                            */         {0xfa, 8},
        /*'-' ( 45)  |010110                              */         {0x16, 6},
        /*'.' ( 46)  |010111                              */         {0x17, 6},
        /*'/' ( 47)  |011000                              */         {0x18, 6},
        /*'0' ( 48)  |00000                               */          {0x0, 5},
        /*'1' ( 49)  |00001                               */          {0x1, 5},
        /*'2' ( 50)  |00010                               */          {0x2, 5},
        /*'3' ( 51)  |011001                              */         {0x19, 6},
        /*'4' ( 52)  |011010                              */         {0x1a, 6},
        /*'5' ( 53)  |011011                              */         {0x1b, 6},
        /*'6' ( 54)  |011100                              */         {0x1c, 6},
        /*'7' ( 55)  |011101                              */         {0x1d, 6},
        /*'8' ( 56)  |011110                              */         {0x1e, 6},
        /*'9' ( 57)  |011111                              */         {0x1f, 6},
        /*':' ( 58)  |1011100                             */         {0x5c, 7},
        /*';' ( 59)  |11111011                            */         {0xfb, 8},
        /*'<' ( 60)  |11111111|1111100                    */      {0x7ffc, 15},
        /*'=' ( 61)  |100000                              */         {0x20, 6},
        /*'>' ( 62)  |11111111|1011                       */       {0xffb, 12},
        /*'?' ( 63)  |11111111|00                         */       {0x3fc, 10},
        /*'@' ( 64)  |11111111|11010                      */      {0x1ffa, 13},
        /*'A' ( 65)  |100001                              */         {0x21, 6},
        /*'B' ( 66)  |1011101                             */         {0x5d, 7},
        /*'C' ( 67)  |1011110                             */         {0x5e, 7},
        /*'D' ( 68)  |1011111                             */         {0x5f, 7},
        /*'E' ( 69)  |1100000                             */         {0x60, 7},
        /*'F' ( 70)  |1100001                             */         {0x61, 7},
        /*'G' ( 71)  |1100010                             */         {0x62, 7},
        /*'H' ( 72)  |1100011                             */         {0x63, 7},
        /*'I' ( 73)  |1100100                             */         {0x64, 7},
        /*'J' ( 74)  |1100101                             */         {0x65, 7},
        /*'K' ( 75)  |1100110                             */         {0x66, 7},
        /*'L' ( 76)  |1100111                             */         {0x67, 7},
        /*'M' ( 77)  |1101000                             */         {0x68, 7},
        /*'N' ( 78)  |1101001                             */         {0x69, 7},
        /*'O' ( 79)  |1101010                             */         {0x6a, 7},
        /*'P' ( 80)  |1101011                             */         {0x6b, 7},
        /*'Q' ( 81)  |1101100                             */         {0x6c, 7},
        /*'R' ( 82)  |1101101                             */         {0x6d, 7},
        /*'S' ( 83)  |1101110                             */         {0x6e, 7},
        /*'T' ( 84)  |1101111                             */         {0x6f, 7},
        /*'U' ( 85)  |1110000                             */         {0x70, 7},
        /*'V' ( 86)  |1110001                             */         {0x71, 7},
        /*'W' ( 87)  |1110010                             */         {0x72, 7},
        /*'X' ( 88)  |11111100                            */         {0xfc, 8},
        /*'Y' ( 89)  |1110011                             */         {0x73, 7},
        /*'Z' ( 90)  |11111101                            */         {0xfd, 8},
        /*'[' ( 91)  |11111111|11011                      */      {0x1ffb, 13},
        /*'\' ( 92)  |11111111|11111110|000               */     {0x7fff0, 19},
        /*']' ( 93)  |11111111|11100                      */      {0x1ffc, 13},
        /*'^' ( 94)  |11111111|111100                     */      {0x3ffc, 14},
        /*'_' ( 95)  |100010                              */         {0x22, 6},
        /*'`' ( 96)  |11111111|1111101                    */      {0x7ffd, 15},
        /*'a' ( 97)  |00011                               */          {0x3, 5},
        /*'b' ( 98)  |100011                              */         {0x23, 6},
        /*'c' ( 99)  |00100                               */          {0x4, 5},
        /*'d' (100)  |100100                              */         {0x24, 6},
        /*'e' (101)  |00101                               */          {0x5, 5},
        /*'f' (102)  |100101                              */         {0x25, 6},
        /*'g' (103)  |100110                              */         {0x26, 6},
        /*'h' (104)  |100111                              */         {0x27, 6},
        /*'i' (105)  |00110                               */          {0x6, 5},
        /*'j' (106)  |1110100                             */         {0x74, 7},
        /*'k' (107)  |1110101                             */         {0x75, 7},
        /*'l' (108)  |101000                              */         {0x28, 6},
        /*'m' (109)  |101001                              */         {0x29, 6},
        /*'n' (110)  |101010                              */         {0x2a, 6},
        /*'o' (111)  |00111                               */          {0x7, 5},
        /*'p' (112)  |101011                              */         {0x2b, 6},
        /*'q' (113)  |1110110                             */         {0x76, 7},
        /*'r' (114)  |101100                              */         {0x2c, 6},
        /*'s' (115)  |01000                               */          {0x8, 5},
        /*'t' (116)  |01001                               */          {0x9, 5},
        /*'u' (117)  |101101                              */         {0x2d, 6},
        /*'v' (118)  |1110111                             */         {0x77, 7},
        /*'w' (119)  |1111000                             */         {0x78, 7},
        /*'x' (120)  |1111001                             */         {0x79, 7},
        /*'y' (121)  |1111010                             */         {0x7a, 7},
        /*'z' (122)  |1111011                             */         {0x7b, 7},
        /*'{' (123)  |11111111|1111110                    */      {0x7ffe, 15},
        /*'|' (124)  |11111111|100                        */       {0x7fc, 11},
        /*'}' (125)  |11111111|111101                     */      {0x3ffd, 14},
        /*'~' (126)  |11111111|11101                      */      {0x1ffd, 13},
        /*    (127)  |11111111|11111111|11111111|1100     */   {0xffffffc, 28},
        /*    (128)  |11111111|11111110|0110              */     {0xfffe6, 20},
        /*    (129)  |11111111|11111111|010010            */    {0x3fffd2, 22},
        /*    (130)  |11111111|11111110|0111              */     {0xfffe7, 20},
        /*    (131)  |11111111|11111110|1000              */     {0xfffe8, 20},
        /*    (132)  |11111111|11111111|010011            */    {0x3fffd3, 22},
        /*    (133)  |11111111|11111111|010100            */    {0x3fffd4, 22},
        /*    (134)  |11111111|11111111|010101            */    {0x3fffd5, 22},
        /*    (135)  |11111111|11111111|1011001           */    {0x7fffd9, 23},
        /*    (136)  |11111111|11111111|010110            */    {0x3fffd6, 22},
        /*    (137)  |11111111|11111111|1011010           */    {0x7fffda, 23},
        /*    (138)  |11111111|11111111|1011011           */    {0x7fffdb, 23},
        /*    (139)  |11111111|11111111|1011100           */    {0x7fffdc, 23},
        /*    (140)  |11111111|11111111|1011101           */    {0x7fffdd, 23},
        /*    (141)  |11111111|11111111|1011110           */    {0x7fffde, 23},
        /*    (142)  |11111111|11111111|11101011          */    {0xffffeb, 24},
        /*    (143)  |11111111|11111111|1011111           */    {0x7fffdf, 23},
        /*    (144)  |11111111|11111111|11101100          */    {0xffffec, 24},
        /*    (145)  |11111111|11111111|11101101          */    {0xffffed, 24},
        /*    (146)  |11111111|11111111|010111            */    {0x3fffd7, 22},
        /*    (147)  |11111111|11111111|1100000           */    {0x7fffe0, 23},
        /*    (148)  |11111111|11111111|11101110          */    {0xffffee, 24},
        /*    (149)  |11111111|11111111|1100001           */    {0x7fffe1, 23},
        /*    (150)  |11111111|11111111|1100010           */    {0x7fffe2, 23},
        /*    (151)  |11111111|11111111|1100011           */    {0x7fffe3, 23},
        /*    (152)  |11111111|11111111|1100100           */    {0x7fffe4, 23},
        /*    (153)  |11111111|11111110|11100             */    {0x1fffdc, 21},
        /*    (154)  |11111111|11111111|011000            */    {0x3fffd8, 22},
        /*    (155)  |11111111|11111111|1100101           */    {0x7fffe5, 23},
        /*    (156)  |11111111|11111111|011001            */    {0x3fffd9, 22},
        /*    (157)  |11111111|11111111|1100110           */    {0x7fffe6, 23},
        /*    (158)  |11111111|11111111|1100111           */    {0x7fffe7, 23},
        /*    (159)  |11111111|11111111|11101111          */    {0xffffef, 24},
        /*    (160)  |11111111|11111111|011010            */    {0x3fffda, 22},
        /*    (161)  |11111111|11111110|11101             */    {0x1fffdd, 21},
        /*    (162)  |11111111|11111110|1001              */     {0xfffe9, 20},
        /*    (163)  |11111111|11111111|011011            */    {0x3fffdb, 22},
        /*    (164)  |11111111|11111111|011100            */    {0x3fffdc, 22},
        /*    (165)  |11111111|11111111|1101000           */    {0x7fffe8, 23},
        /*    (166)  |11111111|11111111|1101001           */    {0x7fffe9, 23},
        /*    (167)  |11111111|11111110|11110             */    {0x1fffde, 21},
        /*    (168)  |11111111|11111111|1101010           */    {0x7fffea, 23},
        /*    (169)  |11111111|11111111|011101            */    {0x3fffdd, 22},
        /*    (170)  |11111111|11111111|011110            */    {0x3fffde, 22},
        /*    (171)  |11111111|11111111|11110000          */    {0xfffff0, 24},
        /*    (172)  |11111111|11111110|11111             */    {0x1fffdf, 21},
        /*    (173)  |11111111|11111111|011111            */    {0x3fffdf, 22},
        /*    (174)  |11111111|11111111|1101011           */    {0x7fffeb, 23},
        /*    (175)  |11111111|11111111|1101100           */    {0x7fffec, 23},
        /*    (176)  |11111111|11111111|00000             */    {0x1fffe0, 21},
        /*    (177)  |11111111|11111111|00001             */    {0x1fffe1, 21},
        /*    (178)  |11111111|11111111|100000            */    {0x3fffe0, 22},
        /*    (179)  |11111111|11111111|00010             */    {0x1fffe2, 21},
        /*    (180)  |11111111|11111111|1101101           */    {0x7fffed, 23},
        /*    (181)  |11111111|11111111|100001            */    {0x3fffe1, 22},
        /*    (182)  |11111111|11111111|1101110           */    {0x7fffee, 23},
        /*    (183)  |11111111|11111111|1101111           */    {0x7fffef, 23},
        /*    (184)  |11111111|11111110|1010              */     {0xfffea, 20},
        /*    (185)  |11111111|11111111|100010            */    {0x3fffe2, 22},
        /*    (186)  |11111111|11111111|100011            */    {0x3fffe3, 22},
        /*    (187)  |11111111|11111111|100100            */    {0x3fffe4, 22},
        /*    (188)  |11111111|11111111|1110000           */    {0x7ffff0, 23},
        /*    (189)  |11111111|11111111|100101            */    {0x3fffe5, 22},
        /*    (190)  |11111111|11111111|100110            */    {0x3fffe6, 22},
        /*    (191)  |11111111|11111111|1110001           */    {0x7ffff1, 23},
        /*    (192)  |11111111|11111111|11111000|00       */   {0x3ffffe0, 26},
        /*    (193)  |11111111|11111111|11111000|01       */   {0x3ffffe1, 26},
        /*    (194)  |11111111|11111110|1011              */     {0xfffeb, 20},
        /*    (195)  |11111111|11111110|001               */     {0x7fff1, 19},
        /*    (196)  |11111111|11111111|100111            */    {0x3fffe7, 22},
        /*    (197)  |11111111|11111111|1110010           */    {0x7ffff2, 23},
        /*    (198)  |11111111|11111111|101000            */    {0x3fffe8, 22},
        /*    (199)  |11111111|11111111|11110110|0        */   {0x1ffffec, 25},
        /*    (200)  |11111111|11111111|11111000|10       */   {0x3ffffe2, 26},
        /*    (201)  |11111111|11111111|11111000|11       */   {0x3ffffe3, 26},
        /*    (202)  |11111111|11111111|11111001|00       */   {0x3ffffe4, 26},
        /*    (203)  |11111111|11111111|11111011|110      */   {0x7ffffde, 27},
        /*    (204)  |11111111|11111111|11111011|111      */   {0x7ffffdf, 27},
        /*    (205)  |11111111|11111111|11111001|01       */   {0x3ffffe5, 26},
        /*    (206)  |11111111|11111111|11110001          */    {0xfffff1, 24},
        /*    (207)  |11111111|11111111|11110110|1        */   {0x1ffffed, 25},
        /*    (208)  |11111111|11111110|010               */     {0x7fff2, 19},
        /*    (209)  |11111111|11111111|00011             */    {0x1fffe3, 21},
        /*    (210)  |11111111|11111111|11111001|10       */   {0x3ffffe6, 26},
        /*    (211)  |11111111|11111111|11111100|000      */   {0x7ffffe0, 27},
        /*    (212)  |11111111|11111111|11111100|001      */   {0x7ffffe1, 27},
        /*    (213)  |11111111|11111111|11111001|11       */   {0x3ffffe7, 26},
        /*    (214)  |11111111|11111111|11111100|010      */   {0x7ffffe2, 27},
        /*    (215)  |11111111|11111111|11110010          */    {0xfffff2, 24},
        /*    (216)  |11111111|11111111|00100             */    {0x1fffe4, 21},
        /*    (217)  |11111111|11111111|00101             */    {0x1fffe5, 21},
        /*    (218)  |11111111|11111111|11111010|00       */   {0x3ffffe8, 26},
        /*    (219)  |11111111|11111111|11111010|01       */   {0x3ffffe9, 26},
        /*    (220)  |11111111|11111111|11111111|1101     */   {0xffffffd, 28},
        /*    (221)  |11111111|11111111|11111100|011      */   {0x7ffffe3, 27},
        /*    (222)  |11111111|11111111|11111100|100      */   {0x7ffffe4, 27},
        /*    (223)  |11111111|11111111|11111100|101      */   {0x7ffffe5, 27},
        /*    (224)  |11111111|11111110|1100              */     {0xfffec, 20},
        /*    (225)  |11111111|11111111|11110011          */    {0xfffff3, 24},
        /*    (226)  |11111111|11111110|1101              */     {0xfffed, 20},
        /*    (227)  |11111111|11111111|00110             */    {0x1fffe6, 21},
        /*    (228)  |11111111|11111111|101001            */    {0x3fffe9, 22},
        /*    (229)  |11111111|11111111|00111             */    {0x1fffe7, 21},
        /*    (230)  |11111111|11111111|01000             */    {0x1fffe8, 21},
        /*    (231)  |11111111|11111111|1110011           */    {0x7ffff3, 23},
        /*    (232)  |11111111|11111111|101010            */    {0x3fffea, 22},
        /*    (233)  |11111111|11111111|101011            */    {0x3fffeb, 22},
        /*    (234)  |11111111|11111111|11110111|0        */   {0x1ffffee, 25},
        /*    (235)  |11111111|11111111|11110111|1        */   {0x1ffffef, 25},
        /*    (236)  |11111111|11111111|11110100          */    {0xfffff4, 24},
        /*    (237)  |11111111|11111111|11110101          */    {0xfffff5, 24},
        /*    (238)  |11111111|11111111|11111010|10       */   {0x3ffffea, 26},
        /*    (239)  |11111111|11111111|1110100           */    {0x7ffff4, 23},
        /*    (240)  |11111111|11111111|11111010|11       */   {0x3ffffeb, 26},
        /*    (241)  |11111111|11111111|11111100|110      */   {0x7ffffe6, 27},
        /*    (242)  |11111111|11111111|11111011|00       */   {0x3ffffec, 26},
        /*    (243)  |11111111|11111111|11111011|01       */   {0x3ffffed, 26},
        /*    (244)  |11111111|11111111|11111100|111      */   {0x7ffffe7, 27},
        /*    (245)  |11111111|11111111|11111101|000      */   {0x7ffffe8, 27},
        /*    (246)  |11111111|11111111|11111101|001      */   {0x7ffffe9, 27},
        /*    (247)  |11111111|11111111|11111101|010      */   {0x7ffffea, 27},
        /*    (248)  |11111111|11111111|11111101|011      */   {0x7ffffeb, 27},
        /*    (249)  |11111111|11111111|11111111|1110     */   {0xffffffe, 28},
        /*    (250)  |11111111|11111111|11111101|100      */   {0x7ffffec, 27},
        /*    (251)  |11111111|11111111|11111101|101      */   {0x7ffffed, 27},
        /*    (252)  |11111111|11111111|11111101|110      */   {0x7ffffee, 27},
        /*    (253)  |11111111|11111111|11111101|111      */   {0x7ffffef, 27},
        /*    (254)  |11111111|11111111|11111110|000      */   {0x7fffff0, 27},
        /*    (255)  |11111111|11111111|11111011|10       */   {0x3ffffee, 26},
        /*EOS (256)  |11111111|11111111|11111111|111111   */  {0x3fffffff, 30},
    };

    private static final Node ROOT;

    private static final class Node
    {
        int symbol;
        Node left;
        Node right;
        Node[] transitions;     // node x byte --> node (256 transitions for byte)
        String[] symbols;       // node x byte --> symbols (max 2 per transition)
        boolean accept;         // valid huffman node

        Node()
        {
            this.symbol = -1;
            this.transitions = new Node[256];
            this.symbols = new String[256];
        }

        @Override
        public String toString()
        {
            return "node[sym=" + (char) symbol + "]";
        }
    }

    static
    {
        ROOT = new Node();

        for (int sym = 0; sym < CODES.length; sym++)
        {
            Node current = ROOT;

            int code = CODES[sym][0];
            int len = CODES[sym][1];

            for (int i = len - 1; i >= 0; i--)
            {
                int bit = (code >>> i) & 0x01;        // Using MSB to traverse
                if (bit == 0)
                {
                    if (current.left == null)
                    {
                        current.left = new Node();
                    }
                    current = current.left;
                }
                else
                {
                    if (current.right == null)
                    {
                        current.right = new Node();
                    }
                    current = current.right;
                }
            }
            current.symbol = sym;
            current.accept = sym != CODES.length - 1;     // EOS is invalid in string literal
        }

        transition(ROOT);

        ROOT.accept = true;
        ROOT.right.accept = true;                                       // 1 padding EOS bit
        ROOT.right.right.accept = true;                                 // 11 padding EOS bits
        ROOT.right.right.right.accept = true;                           // 111 padding EOS bits
        ROOT.right.right.right.right.accept = true;                     // 1111 padding EOS bits
        ROOT.right.right.right.right.right.accept = true;               // 11111 padding EOS bits
        ROOT.right.right.right.right.right.right.accept = true;         // 111111 padding EOS bits
        ROOT.right.right.right.right.right.right.right.accept = true;   // 1111111 padding EOS bits
    }

    // Build all 256 Node x byte transitions
    private static void transition(Node node)
    {
        if (node == null)
        {
            return;
        }
        for (int i = 0; i < 256; i++)
        {
            transition(node, i);
        }
        transition(node.left);
        transition(node.right);
    }

    // Build one Node x byte transition
    private static void transition(Node node, int b)
    {
        Node cur = node;
        String str = node.symbols[b];

        for (int i = 7; i >= 0; i--)
        {
            int bit = (b >>> i) & 0x01;           // Using MSB to traverse
            cur = bit == 0 ? cur.left : cur.right;
            if (cur == null || cur.symbol == 256)      // EOS is invalid in sequence
            {
                return;
            }
            if (cur.symbol != -1)                      // Can have two symbols in a byte traversal
            {
                str = (str == null) ? "" + (char) cur.symbol : str + (char) cur.symbol;
                cur = ROOT;
            }
        }
        node.transitions[b] = cur;
        node.symbols[b] = str;
    }

    /*
    // Searches the huffman tree for a code
    private static int search(Node node, int code, int length) {

        for(int i=length-1; i >= 0; i--) {
            int bit = ((code >>> i) & 0x01);
            if (bit == 0) {
                node = node.left;
            } else {
                node = node.right;
            }
            if (node == null) {
                throw new RuntimeException("Not there");
            }
        }

        return node.sym;
    }


    // Decodes by traversing huffman tree by single bits
    public static void decode(Node root, DirectBuffer buf, int offset, int length) {
        StringBuilder sb = new StringBuilder();

        Node cur = root;
        for (int k = 0; k < length; k++) {

            byte b = buf.getByte(offset + k);

            for (int i = 7; i >= 0; i--) {
                int bit = ((b >>> i) & 0x01);
                System.out.print(bit+" ");
                if (bit == 0) {
                    cur = cur.left;
                } else {
                    cur = cur.right;
                }
                if (cur == null) {
                    throw new RuntimeException("Not there, but parsed until " + sb.toString());
                }
                if (cur.sym != -1) {
                    sb.append((char)cur.sym);
                    cur = root;
                }
            }
            System.out.println();
        }
        System.out.println(sb.toString());
    }
    */

    /*
     * Based on "Fast Prefix Code Processing (by Renato Pajarola)" paper. It
     * precomputes all 256 possible bit sequences or node transitions at
     * every node. This allows it to jump efficiently from any node to another
     * in the code tree by processing bytes simultaneously instead of single
     * bits.
     *
     * https://pdfs.semanticscholar.org/3697/8e4715a7bf21426877132f5b2e9c3d280287.pdf
     *
     * @return length of decoded string
     *         -1 if there is an error
     */
    public static int decode(DirectBuffer src, MutableDirectBuffer dst)
    {
        Node current = ROOT;
        int offset = 0;
        int limit = dst.capacity();

        for (int i = 0; i < src.capacity() && offset < limit; i++)
        {
            int b = src.getByte(i) & 0xff;
            Node next = current.transitions[b];
            if (next == null)
            {
                return -1;
            }
            if (current.symbols[b] != null)
            {
                dst.putByte(offset++, (byte) current.symbols[b].charAt(0));
                if (current.symbols[b].length() == 2 && offset < limit)
                {
                    dst.putByte(offset++, (byte) current.symbols[b].charAt(1));
                }
            }
            current = next;
        }
        return current.accept && offset < limit ? offset : -1;
    }

    // Returns the no of bytes needed to encode src
    public static int encodedSize(DirectBuffer src, int offset, int length)
    {
        int totalBits = 0;

        for (int i = 0; i < length; i++)
        {
            int index = src.getByte(offset + i) & 0xff;
            int bits = CODES[index][1];
            totalBits += bits;
        }

        return (totalBits + 7) / 8;
    }

    // Huffman encodes src buffer into dst buffer
    // Assumes enough space is in the dst buffer
    public static void encode(DirectBuffer src, MutableDirectBuffer dst)
    {
        //assert dst.capacity() >= encodedSize(src, 0, src.capacity());

        int remainingBits = 0;
        int dstIndex = 0;
        long currentSeq = 0;       // Aligned to LSB, for e.g 0000_0000_0XXX_XXXX

        for (int i = 0; i < src.capacity(); i++)
        {
            int index = src.getByte(i) & 0xFF;
            int code = CODES[index][0];
            int bits = CODES[index][1];

            if (remainingBits + bits > 64)                  // exceeds long (no more space for current bits)
            {
                dst.putLong(dstIndex, currentSeq << (64 - remainingBits), BIG_ENDIAN);
                dstIndex += remainingBits / 8;
                remainingBits = remainingBits % 8;
            }

            currentSeq <<= bits;
            currentSeq |= code;
            remainingBits += bits;
        }

        while (remainingBits > 0)
        {
            if (remainingBits >= 8)
            {
                remainingBits -= 8;
                dst.putByte(dstIndex++, (byte) (currentSeq >> remainingBits));
            }
            else
            {
                currentSeq <<= 8 - remainingBits;            // partial byte, so align to MSB
                currentSeq |= 0xFF >>> remainingBits;        // fill remaining bits with EOS bits
                remainingBits = 8;
            }
        }
    }

    private HpackHuffman()
    {
        // utility
    }
}
