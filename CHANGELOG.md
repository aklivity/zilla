# Changelog

## [1.2.0](https://github.com/aklivity/zilla/tree/1.2.0) (2026-04-28)

[Full Changelog](https://github.com/aklivity/zilla/compare/1.1.9...1.2.0)

**Implemented enhancements:**

- engine: implement sys: system namespace with built-in egress bindings [\#1677](https://github.com/aklivity/zilla/issues/1677)
- binding-http: derive ProxyBeginEx from :authority in HttpBeginEx for stream-driven destination routing [\#1676](https://github.com/aklivity/zilla/issues/1676)
- binding-mcp: implement mcp · client binding [\#1670](https://github.com/aklivity/zilla/issues/1670)

**Merged pull requests:**

- feat\(flyweight-maven-plugin\): add inject\(Consumer&lt;Builder&gt;\) on generated struct + union builders [\#1738](https://github.com/aklivity/zilla/pull/1738) ([jfallows](https://github.com/jfallows))
- fix\(binding-tcp\): prevent NPE when retrying accept across contexts [\#1735](https://github.com/aklivity/zilla/pull/1735) ([akrambek](https://github.com/akrambek))
- feat\(engine\): add sys:http\_client, sys:tls\_client, sys:tcp\_client built-in egress bindings \(\#1677\) [\#1717](https://github.com/aklivity/zilla/pull/1717) ([jfallows](https://github.com/jfallows))
- feat\(binding-http\): derive ProxyBeginEx from :authority for stream-driven routing [\#1713](https://github.com/aklivity/zilla/pull/1713) ([jfallows](https://github.com/jfallows))
- feat\(binding-mcp\): implement mcp client binding [\#1711](https://github.com/aklivity/zilla/pull/1711) ([jfallows](https://github.com/jfallows))

## [1.1.9](https://github.com/aklivity/zilla/tree/1.1.9) (2026-04-22)

[Full Changelog](https://github.com/aklivity/zilla/compare/1.1.8...1.1.9)

**Merged pull requests:**

- test\(engine\): add store operation drivers to TestBinding [\#1732](https://github.com/aklivity/zilla/pull/1732) ([akrambek](https://github.com/akrambek))

## [1.1.8](https://github.com/aklivity/zilla/tree/1.1.8) (2026-04-20)

[Full Changelog](https://github.com/aklivity/zilla/compare/1.1.7...1.1.8)

**Implemented enhancements:**

- binding-mcp: implement mcp · server binding [\#1668](https://github.com/aklivity/zilla/issues/1668)

**Merged pull requests:**

- Enhance metric resolution for raw protocol bindings [\#1730](https://github.com/aklivity/zilla/pull/1730) ([akrambek](https://github.com/akrambek))
- fix\(ci\): wrap teardown if-expression entirely in ${{ }} [\#1729](https://github.com/aklivity/zilla/pull/1729) ([jfallows](https://github.com/jfallows))
- feat\(binding-mcp\): implement mcp · server binding [\#1705](https://github.com/aklivity/zilla/pull/1705) ([jfallows](https://github.com/jfallows))

## [1.1.7](https://github.com/aklivity/zilla/tree/1.1.7) (2026-04-17)

[Full Changelog](https://github.com/aklivity/zilla/compare/1.1.6...1.1.7)

**Closed issues:**

- `SafeBuffer`: `AtomicBuffer` implementation backed by `MemorySegment` [\#1719](https://github.com/aklivity/zilla/issues/1719)
- Design configuration syntax for metrics with arbitrary attributes [\#1636](https://github.com/aklivity/zilla/issues/1636)
- Deliver support for metrics with arbitrary attributes [\#1577](https://github.com/aklivity/zilla/issues/1577)

**Merged pull requests:**

- Enhance metric resolution for raw protocol bindings [\#1725](https://github.com/aklivity/zilla/pull/1725) ([akrambek](https://github.com/akrambek))
- Support configuration syntax for metrics with arbitrary attributes [\#1696](https://github.com/aklivity/zilla/pull/1696) ([akrambek](https://github.com/akrambek))

## [1.1.6](https://github.com/aklivity/zilla/tree/1.1.6) (2026-04-08)

[Full Changelog](https://github.com/aklivity/zilla/compare/1.1.5...1.1.6)

**Implemented enhancements:**

- store-memory: implement in-process memory store [\#1667](https://github.com/aklivity/zilla/issues/1667)
- engine: add Store concept \(Store, StoreContext, StoreHandler, StoreFactorySpi\) [\#1666](https://github.com/aklivity/zilla/issues/1666)

**Merged pull requests:**

- feat\(store-memory\): implement in-process memory store [\#1710](https://github.com/aklivity/zilla/pull/1710) ([jfallows](https://github.com/jfallows))
- feat\(engine\): add Store concept \(Store, StoreContext, StoreHandler, StoreFactorySpi\) [\#1707](https://github.com/aklivity/zilla/pull/1707) ([jfallows](https://github.com/jfallows))

## [1.1.5](https://github.com/aklivity/zilla/tree/1.1.5) (2026-04-07)

[Full Changelog](https://github.com/aklivity/zilla/compare/1.1.4...1.1.5)

**Fixed bugs:**

- OTLP exporter crashes with JsonParsingException when event message contains double quotes [\#1703](https://github.com/aklivity/zilla/issues/1703)

**Merged pull requests:**

- OTLP exporter to handle event message with special character [\#1704](https://github.com/aklivity/zilla/pull/1704) ([ankitk-me](https://github.com/ankitk-me))

## [1.1.4](https://github.com/aklivity/zilla/tree/1.1.4) (2026-04-07)

[Full Changelog](https://github.com/aklivity/zilla/compare/1.0.11...1.1.4)

**Merged pull requests:**

- Upgrade to JDK 25 and Agrona 2.4.0 [\#1665](https://github.com/aklivity/zilla/pull/1665) ([jfallows](https://github.com/jfallows))
- Bump eclipse-temurin from 21-alpine to 25-alpine in /cloud/docker-image/src/main/docker [\#1574](https://github.com/aklivity/zilla/pull/1574) ([dependabot[bot]](https://github.com/apps/dependabot))

## [1.0.11](https://github.com/aklivity/zilla/tree/1.0.11) (2026-04-03)

[Full Changelog](https://github.com/aklivity/zilla/compare/1.0.10...1.0.11)

**Closed issues:**

- Support `zilla start ... --diagnostics-directory` to capture engine state [\#1663](https://github.com/aklivity/zilla/issues/1663)

**Merged pull requests:**

- omit examples without test script [\#1695](https://github.com/aklivity/zilla/pull/1695) ([ankitk-me](https://github.com/ankitk-me))
- Fix event log cleanup [\#1693](https://github.com/aklivity/zilla/pull/1693) ([jfallows](https://github.com/jfallows))
- Support capturing engine diagnostics [\#1692](https://github.com/aklivity/zilla/pull/1692) ([ankitk-me](https://github.com/ankitk-me))
- Fix tls handshake timeout description [\#1691](https://github.com/aklivity/zilla/pull/1691) ([jfallows](https://github.com/jfallows))

## [1.0.10](https://github.com/aklivity/zilla/tree/1.0.10) (2026-03-27)

[Full Changelog](https://github.com/aklivity/zilla/compare/1.0.9...1.0.10)

**Closed issues:**

- Logging strategy for auth errors in `kafka` client binding [\#1662](https://github.com/aklivity/zilla/issues/1662)

**Merged pull requests:**

- logging for auth errors in kafka client binding [\#1664](https://github.com/aklivity/zilla/pull/1664) ([ankitk-me](https://github.com/ankitk-me))

## [1.0.9](https://github.com/aklivity/zilla/tree/1.0.9) (2026-03-18)

[Full Changelog](https://github.com/aklivity/zilla/compare/1.0.7...1.0.9)

**Merged pull requests:**

- Fix connection leak in http1 client [\#1661](https://github.com/aklivity/zilla/pull/1661) ([akrambek](https://github.com/akrambek))

## [1.0.7](https://github.com/aklivity/zilla/tree/1.0.7) (2026-03-17)

[Full Changelog](https://github.com/aklivity/zilla/compare/1.0.6...1.0.7)

**Merged pull requests:**

- Fix Kafka produce idle timeout [\#1659](https://github.com/aklivity/zilla/pull/1659) ([jfallows](https://github.com/jfallows))
- Fix HTTP/1.1 keep-alive stall after 400 error response reset [\#1658](https://github.com/aklivity/zilla/pull/1658) ([akrambek](https://github.com/akrambek))

## [1.0.6](https://github.com/aklivity/zilla/tree/1.0.6) (2026-03-15)

[Full Changelog](https://github.com/aklivity/zilla/compare/1.0.4...1.0.6)

**Merged pull requests:**

- Reset http 1.1 response when request is reset [\#1656](https://github.com/aklivity/zilla/pull/1656) ([jfallows](https://github.com/jfallows))
- Ensure dump command can advance beyond padding to reach last record [\#1655](https://github.com/aklivity/zilla/pull/1655) ([jfallows](https://github.com/jfallows))
- Enhance dump command to support explicit initial offsets [\#1654](https://github.com/aklivity/zilla/pull/1654) ([jfallows](https://github.com/jfallows))

## [1.0.4](https://github.com/aklivity/zilla/tree/1.0.4) (2026-03-11)

[Full Changelog](https://github.com/aklivity/zilla/compare/1.0.3...1.0.4)

**Merged pull requests:**

- Fix HTTP 1.1 request flow control sequence [\#1653](https://github.com/aklivity/zilla/pull/1653) ([jfallows](https://github.com/jfallows))
- Fix NPE in KafkaClientDescribeFactory when broker returns partial configs [\#1652](https://github.com/aklivity/zilla/pull/1652) ([akrambek](https://github.com/akrambek))
- Update README.md [\#1650](https://github.com/aklivity/zilla/pull/1650) ([llukyanov](https://github.com/llukyanov))

## [1.0.3](https://github.com/aklivity/zilla/tree/1.0.3) (2026-03-10)

[Full Changelog](https://github.com/aklivity/zilla/compare/1.0.2...1.0.3)

**Merged pull requests:**

- Fix HTTP 1.1 server binding flow control for GET echo [\#1651](https://github.com/aklivity/zilla/pull/1651) ([jfallows](https://github.com/jfallows))

## [1.0.2](https://github.com/aklivity/zilla/tree/1.0.2) (2026-03-06)

[Full Changelog](https://github.com/aklivity/zilla/compare/1.0.1...1.0.2)

**Merged pull requests:**

- fix null binding during connection accept [\#1649](https://github.com/aklivity/zilla/pull/1649) ([ankitk-me](https://github.com/ankitk-me))

## [1.0.1](https://github.com/aklivity/zilla/tree/1.0.1) (2026-03-06)

[Full Changelog](https://github.com/aklivity/zilla/compare/1.0.0...1.0.1)

**Fixed bugs:**

- Engine can trigger an `IllegalStateException` during shutdown [\#1646](https://github.com/aklivity/zilla/issues/1646)

**Closed issues:**

- \[Maintenance\] Replace unmaintained Bitnami Legacy images with free SolDevelo alternative [\#1633](https://github.com/aklivity/zilla/issues/1633)

**Merged pull requests:**

- Prevent potential NPE for rejected HTTP 1.1 request with content [\#1648](https://github.com/aklivity/zilla/pull/1648) ([jfallows](https://github.com/jfallows))
- Avoid receiving new TCP connections during engine shutdown [\#1647](https://github.com/aklivity/zilla/pull/1647) ([jfallows](https://github.com/jfallows))
- add example `sse.kafka.fanout.filter` [\#1640](https://github.com/aklivity/zilla/pull/1640) ([ankitk-me](https://github.com/ankitk-me))

## [1.0.0](https://github.com/aklivity/zilla/tree/1.0.0) (2026-02-10)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.182...1.0.0)

**Merged pull requests:**

- fix link checker github action [\#1641](https://github.com/aklivity/zilla/pull/1641) ([ankitk-me](https://github.com/ankitk-me))

## [0.9.182](https://github.com/aklivity/zilla/tree/0.9.182) (2026-02-06)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.181...0.9.182)

**Closed issues:**

- Support `max-idle-time` for `sse` `server` binding [\#1638](https://github.com/aklivity/zilla/issues/1638)

**Merged pull requests:**

- Support sse server max idle time [\#1639](https://github.com/aklivity/zilla/pull/1639) ([jfallows](https://github.com/jfallows))

## [0.9.181](https://github.com/aklivity/zilla/tree/0.9.181) (2026-01-27)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.180...0.9.181)

**Merged pull requests:**

- Push CHANGELOG commit to develop [\#1634](https://github.com/aklivity/zilla/pull/1634) ([jfallows](https://github.com/jfallows))
- Bump ubuntu from jammy-20251013 to jammy-20260109 in /cloud/docker-image/src/main/docker [\#1631](https://github.com/aklivity/zilla/pull/1631) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump alpine from 3.23.0 to 3.23.2 in /cloud/docker-image/src/main/docker [\#1625](https://github.com/aklivity/zilla/pull/1625) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump actions/cache from 4 to 5 [\#1621](https://github.com/aklivity/zilla/pull/1621) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump actions/checkout from 5 to 6 [\#1619](https://github.com/aklivity/zilla/pull/1619) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump actions/setup-java from 4 to 5 [\#1541](https://github.com/aklivity/zilla/pull/1541) ([dependabot[bot]](https://github.com/apps/dependabot))

## [0.9.180](https://github.com/aklivity/zilla/tree/0.9.180) (2026-01-20)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.179...0.9.180)

**Fixed bugs:**

- Kafka DescribeConfigs uses API version 0 against brokers \(Kafka 4.1\) that require v1+ [\#1614](https://github.com/aklivity/zilla/issues/1614)

**Merged pull requests:**

- upgrade DescribeConfigs API `v1` [\#1629](https://github.com/aklivity/zilla/pull/1629) ([ankitk-me](https://github.com/ankitk-me))

## [0.9.179](https://github.com/aklivity/zilla/tree/0.9.179) (2026-01-18)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.178...0.9.179)

**Merged pull requests:**

- Dynamic context support for resolvers + change nonce generation [\#1632](https://github.com/aklivity/zilla/pull/1632) ([bmaidics](https://github.com/bmaidics))

## [0.9.178](https://github.com/aklivity/zilla/tree/0.9.178) (2026-01-14)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.177...0.9.178)

**Closed issues:**

- Support `tls` deferring to vault for list of keys or trusted certificates, support `filesystem` vault [\#1576](https://github.com/aklivity/zilla/issues/1576)

**Merged pull requests:**

- Fix config watcher to skip unregistered watch keys [\#1630](https://github.com/aklivity/zilla/pull/1630) ([akrambek](https://github.com/akrambek))

## [0.9.177](https://github.com/aklivity/zilla/tree/0.9.177) (2025-12-22)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.176...0.9.177)

**Merged pull requests:**

- Fix integer overflow in http client [\#1627](https://github.com/aklivity/zilla/pull/1627) ([akrambek](https://github.com/akrambek))

## [0.9.176](https://github.com/aklivity/zilla/tree/0.9.176) (2025-12-18)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.175...0.9.176)

**Implemented enhancements:**

- Support populating identity override without guarded route [\#1518](https://github.com/aklivity/zilla/issues/1518)

**Merged pull requests:**

- Correctly send http end when no upgrade header [\#1626](https://github.com/aklivity/zilla/pull/1626) ([bmaidics](https://github.com/bmaidics))
- Support populating identity override without guarded route [\#1600](https://github.com/aklivity/zilla/pull/1600) ([nageshwaravijay1117](https://github.com/nageshwaravijay1117))

## [0.9.175](https://github.com/aklivity/zilla/tree/0.9.175) (2025-12-11)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.174...0.9.175)

**Merged pull requests:**

- Include blocking select with timeout in doWork loops [\#1620](https://github.com/aklivity/zilla/pull/1620) ([jfallows](https://github.com/jfallows))

## [0.9.174](https://github.com/aklivity/zilla/tree/0.9.174) (2025-12-09)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.173...0.9.174)

**Closed issues:**

- Support `model-avro` validation where catalog expects prefix with schema id [\#1611](https://github.com/aklivity/zilla/issues/1611)

**Merged pull requests:**

- Bump ubuntu from jammy-20250404 to jammy-20251013 in /cloud/docker-image/src/main/docker [\#1607](https://github.com/aklivity/zilla/pull/1607) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump lycheeverse/lychee-action from 2.4.1 to 2.7.0 [\#1601](https://github.com/aklivity/zilla/pull/1601) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump actions/upload-artifact from 4 to 5 [\#1597](https://github.com/aklivity/zilla/pull/1597) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump actions/checkout from 4 to 5 [\#1535](https://github.com/aklivity/zilla/pull/1535) ([dependabot[bot]](https://github.com/apps/dependabot))

## [0.9.173](https://github.com/aklivity/zilla/tree/0.9.173) (2025-11-19)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.172...0.9.173)

**Closed issues:**

- Support `model-json` validation where catalog expects prefix with schema id [\#1605](https://github.com/aklivity/zilla/issues/1605)

**Merged pull requests:**

- Handle abort aborted race condition in IT scripts [\#1608](https://github.com/aklivity/zilla/pull/1608) ([jfallows](https://github.com/jfallows))

## [0.9.172](https://github.com/aklivity/zilla/tree/0.9.172) (2025-11-12)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.171...0.9.172)

**Merged pull requests:**

- Support catalog handler validate [\#1606](https://github.com/aklivity/zilla/pull/1606) ([jfallows](https://github.com/jfallows))

## [0.9.171](https://github.com/aklivity/zilla/tree/0.9.171) (2025-10-28)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.170...0.9.171)

**Fixed bugs:**

- Fix virtual cluster produce under load [\#1595](https://github.com/aklivity/zilla/issues/1595)

**Merged pull requests:**

- Enhance engine extensions API [\#1599](https://github.com/aklivity/zilla/pull/1599) ([jfallows](https://github.com/jfallows))

## [0.9.170](https://github.com/aklivity/zilla/tree/0.9.170) (2025-10-27)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.169...0.9.170)

**Merged pull requests:**

- Fix filesystem vault in local config [\#1598](https://github.com/aklivity/zilla/pull/1598) ([jfallows](https://github.com/jfallows))

## [0.9.169](https://github.com/aklivity/zilla/tree/0.9.169) (2025-10-27)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.168...0.9.169)

**Merged pull requests:**

- Add basic auth username/password accessor to HTTP server [\#1596](https://github.com/aklivity/zilla/pull/1596) ([bmaidics](https://github.com/bmaidics))
- update `sse.kafka.fanout` to support `jwt` [\#1593](https://github.com/aklivity/zilla/pull/1593) ([ankitk-me](https://github.com/ankitk-me))

## [0.9.168](https://github.com/aklivity/zilla/tree/0.9.168) (2025-10-23)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.167...0.9.168)

**Merged pull requests:**

- Fix TestGuardHandler when no attributes are specified [\#1592](https://github.com/aklivity/zilla/pull/1592) ([bmaidics](https://github.com/bmaidics))

## [0.9.167](https://github.com/aklivity/zilla/tree/0.9.167) (2025-10-21)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.166...0.9.167)

**Merged pull requests:**

- Fix http 1.1 client upgrade then close [\#1591](https://github.com/aklivity/zilla/pull/1591) ([jfallows](https://github.com/jfallows))

## [0.9.166](https://github.com/aklivity/zilla/tree/0.9.166) (2025-10-16)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.165...0.9.166)

**Merged pull requests:**

- Fix http 1.1 client decode of response empty reason phrase [\#1589](https://github.com/aklivity/zilla/pull/1589) ([jfallows](https://github.com/jfallows))

## [0.9.165](https://github.com/aklivity/zilla/tree/0.9.165) (2025-10-15)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.164...0.9.165)

**Merged pull requests:**

- Fix http filesystem module-info [\#1588](https://github.com/aklivity/zilla/pull/1588) ([jfallows](https://github.com/jfallows))
- Protobuf2 Model Support in Zilla [\#1548](https://github.com/aklivity/zilla/pull/1548) ([nageshwaravijay1117](https://github.com/nageshwaravijay1117))

## [0.9.164](https://github.com/aklivity/zilla/tree/0.9.164) (2025-10-13)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.162...0.9.164)

**Merged pull requests:**

- Append local config if configured [\#1583](https://github.com/aklivity/zilla/pull/1583) ([bmaidics](https://github.com/bmaidics))

## [0.9.162](https://github.com/aklivity/zilla/tree/0.9.162) (2025-10-10)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.161...0.9.162)

**Merged pull requests:**

- validate attributes in test binding [\#1587](https://github.com/aklivity/zilla/pull/1587) ([ankitk-me](https://github.com/ankitk-me))

## [0.9.161](https://github.com/aklivity/zilla/tree/0.9.161) (2025-10-09)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.159...0.9.161)

**Closed issues:**

- Support guard attributes [\#1578](https://github.com/aklivity/zilla/issues/1578)

**Merged pull requests:**

- Support guard attributes [\#1582](https://github.com/aklivity/zilla/pull/1582) ([ankitk-me](https://github.com/ankitk-me))

## [0.9.159](https://github.com/aklivity/zilla/tree/0.9.159) (2025-09-30)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.158...0.9.159)

**Closed issues:**

- Support specifying credentials for OTLP exporter [\#1556](https://github.com/aklivity/zilla/issues/1556)
- Migrate engine started and stopped to telemetry events [\#1491](https://github.com/aklivity/zilla/issues/1491)

**Merged pull requests:**

- fix schema for catalog modules [\#1557](https://github.com/aklivity/zilla/pull/1557) ([ankitk-me](https://github.com/ankitk-me))
- Migrate engine started and stopped to telemetry events [\#1555](https://github.com/aklivity/zilla/pull/1555) ([bmaidics](https://github.com/bmaidics))

## [0.9.158](https://github.com/aklivity/zilla/tree/0.9.158) (2025-09-12)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.157...0.9.158)

**Merged pull requests:**

- Add engine authorization config [\#1547](https://github.com/aklivity/zilla/pull/1547) ([bmaidics](https://github.com/bmaidics))

## [0.9.157](https://github.com/aklivity/zilla/tree/0.9.157) (2025-09-07)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.156...0.9.157)

**Merged pull requests:**

- Use ephemeral name as sender binding [\#1551](https://github.com/aklivity/zilla/pull/1551) ([jfallows](https://github.com/jfallows))
- Default worker capacity calculation fix [\#1550](https://github.com/aklivity/zilla/pull/1550) ([bmaidics](https://github.com/bmaidics))
- Handle slow TCP client handshake [\#1546](https://github.com/aklivity/zilla/pull/1546) ([jfallows](https://github.com/jfallows))

## [0.9.156](https://github.com/aklivity/zilla/tree/0.9.156) (2025-08-26)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.155...0.9.156)

**Merged pull requests:**

- Add convenience setter for string field in union or struct builder [\#1545](https://github.com/aklivity/zilla/pull/1545) ([jfallows](https://github.com/jfallows))

## [0.9.155](https://github.com/aklivity/zilla/tree/0.9.155) (2025-08-21)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.154...0.9.155)

**Fixed bugs:**

- Using Maps and Arrays with Avro, cause app to crash due to unsafe memory access operation [\#1525](https://github.com/aklivity/zilla/issues/1525)

**Closed issues:**

- Support JWT identity claims for dynamic `http-kafka` and `sse-kafka` topic routing [\#1511](https://github.com/aklivity/zilla/issues/1511)
- Auth0 Identity Value Not Fetching Inside Identity Field [\#1475](https://github.com/aklivity/zilla/issues/1475)
- Support PATCH as HTTP method [\#1419](https://github.com/aklivity/zilla/issues/1419)

**Merged pull requests:**

- Support remote\_client binding kind [\#1542](https://github.com/aklivity/zilla/pull/1542) ([jfallows](https://github.com/jfallows))
- Disable tcp.reflect example [\#1540](https://github.com/aklivity/zilla/pull/1540) ([jfallows](https://github.com/jfallows))
- Disable `ws.reflect` example [\#1536](https://github.com/aklivity/zilla/pull/1536) ([jfallows](https://github.com/jfallows))
- fix `mqtt` & `mqtt-kafka` topic pattern to allow `.` [\#1532](https://github.com/aklivity/zilla/pull/1532) ([ankitk-me](https://github.com/ankitk-me))
- Use piped input and output stream [\#1531](https://github.com/aklivity/zilla/pull/1531) ([jfallows](https://github.com/jfallows))
- Support http patch [\#1530](https://github.com/aklivity/zilla/pull/1530) ([Qianyu2021](https://github.com/Qianyu2021))
- config: support padding for arrays and maps in `JSON` view [\#1527](https://github.com/aklivity/zilla/pull/1527) ([ankitk-me](https://github.com/ankitk-me))

## [0.9.154](https://github.com/aklivity/zilla/tree/0.9.154) (2025-07-24)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.153...0.9.154)

**Implemented enhancements:**

- Automate `zilla` `develop-SNAPSHOT` acceptance tests for `zilla-examples` scenarios [\#1434](https://github.com/aklivity/zilla/issues/1434)

**Merged pull requests:**

- Support trustcacerts in test binding vault assertions [\#1522](https://github.com/aklivity/zilla/pull/1522) ([jfallows](https://github.com/jfallows))

## [0.9.153](https://github.com/aklivity/zilla/tree/0.9.153) (2025-07-14)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.152...0.9.153)

**Merged pull requests:**

- Log worker usage instead of utilization [\#1515](https://github.com/aklivity/zilla/pull/1515) ([akrambek](https://github.com/akrambek))

## [0.9.152](https://github.com/aklivity/zilla/tree/0.9.152) (2025-07-08)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.151...0.9.152)

**Fixed bugs:**

- Getting autentication error if JWT does not contain kid header [\#1502](https://github.com/aklivity/zilla/issues/1502)
- Zilla can appear unhealthy to tcp health check mechanisms at full engine worker utilization [\#1495](https://github.com/aklivity/zilla/issues/1495)
- zilla stops sending sse updates after a couple days [\#1340](https://github.com/aklivity/zilla/issues/1340)
- The http.filesystem example hangs when returning some files [\#1431](https://github.com/aklivity/zilla/issues/1431)

**Closed issues:**

- Validate `$ref` in OpenAPI Schema [\#1467](https://github.com/aklivity/zilla/issues/1467)
- Validate `$ref` in AsyncAPI Schema [\#1466](https://github.com/aklivity/zilla/issues/1466)

**Merged pull requests:**

- record unresolved $ref in OpenAPI [\#1510](https://github.com/aklivity/zilla/pull/1510) ([ankitk-me](https://github.com/ankitk-me))
- Support load balancer health check even at full capacity [\#1509](https://github.com/aklivity/zilla/pull/1509) ([akrambek](https://github.com/akrambek))
- fix: Schema declaration in message & record unresolved `$ref` [\#1488](https://github.com/aklivity/zilla/pull/1488) ([ankitk-me](https://github.com/ankitk-me))

## [0.9.151](https://github.com/aklivity/zilla/tree/0.9.151) (2025-06-18)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.150...0.9.151)

**Merged pull requests:**

- Add CRL checks [\#1474](https://github.com/aklivity/zilla/pull/1474) ([bmaidics](https://github.com/bmaidics))

## [0.9.150](https://github.com/aklivity/zilla/tree/0.9.150) (2025-06-16)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.149...0.9.150)

**Merged pull requests:**

- Add specific event kind for TLS handshake timeout [\#1505](https://github.com/aklivity/zilla/pull/1505) ([jfallows](https://github.com/jfallows))
- Enhance stdout exporter to include trace id [\#1504](https://github.com/aklivity/zilla/pull/1504) ([jfallows](https://github.com/jfallows))
- Ensure matchCN thread safety when engine task parallelism \> 1 [\#1503](https://github.com/aklivity/zilla/pull/1503) ([jfallows](https://github.com/jfallows))

## [0.9.149](https://github.com/aklivity/zilla/tree/0.9.149) (2025-06-15)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.148...0.9.149)

**Merged pull requests:**

- Report exceptions on zilla start, such as invalid power of 2 for config [\#1501](https://github.com/aklivity/zilla/pull/1501) ([jfallows](https://github.com/jfallows))
- Support `engine.worker.capacity.unbounded` configuration [\#1500](https://github.com/aklivity/zilla/pull/1500) ([jfallows](https://github.com/jfallows))
- Add `engine.workers.capacity` metric and rename other `engine.worker` metrics to `engine.workers` [\#1499](https://github.com/aklivity/zilla/pull/1499) ([jfallows](https://github.com/jfallows))
- Use GaugesLayout for engine worker capacity metric [\#1496](https://github.com/aklivity/zilla/pull/1496) ([jfallows](https://github.com/jfallows))

## [0.9.148](https://github.com/aklivity/zilla/tree/0.9.148) (2025-06-14)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.147...0.9.148)

**Merged pull requests:**

- Cancel pending TLS handshake task when streams closed in both directions [\#1497](https://github.com/aklivity/zilla/pull/1497) ([jfallows](https://github.com/jfallows))

## [0.9.147](https://github.com/aklivity/zilla/tree/0.9.147) (2025-06-12)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.146...0.9.147)

**Merged pull requests:**

- Fix typo increase counter on connection close instead of decreasing it [\#1494](https://github.com/aklivity/zilla/pull/1494) ([akrambek](https://github.com/akrambek))

## [0.9.146](https://github.com/aklivity/zilla/tree/0.9.146) (2025-06-11)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.145...0.9.146)

**Closed issues:**

- Custom Role Claim Support in Zilla JWT Validation [\#1476](https://github.com/aklivity/zilla/issues/1476)

**Merged pull requests:**

- Test missing namespace and binding name [\#1493](https://github.com/aklivity/zilla/pull/1493) ([akrambek](https://github.com/akrambek))
- Support custom role claim [\#1492](https://github.com/aklivity/zilla/pull/1492) ([akrambek](https://github.com/akrambek))
- Update README.md [\#1464](https://github.com/aklivity/zilla/pull/1464) ([anujkarn002](https://github.com/anujkarn002))

## [0.9.145](https://github.com/aklivity/zilla/tree/0.9.145) (2025-06-05)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.144...0.9.145)

**Merged pull requests:**

- Support conditional Thread.interrupt\(\) on exception in EngineRule [\#1489](https://github.com/aklivity/zilla/pull/1489) ([jfallows](https://github.com/jfallows))
- Bump lycheeverse/lychee-action from 2.3.0 to 2.4.1 [\#1473](https://github.com/aklivity/zilla/pull/1473) ([dependabot[bot]](https://github.com/apps/dependabot))

## [0.9.144](https://github.com/aklivity/zilla/tree/0.9.144) (2025-05-31)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.143...0.9.144)

**Closed issues:**

- Can't build Zilla offline due to JitPack-based K3PO dependencies [\#1481](https://github.com/aklivity/zilla/issues/1481)
- Support Overriding `correlation-id` via AsyncAPI Specification [\#1471](https://github.com/aklivity/zilla/issues/1471)

**Merged pull requests:**

- Use flyweights for TLS proxy decoder [\#1485](https://github.com/aklivity/zilla/pull/1485) ([jfallows](https://github.com/jfallows))
- Remove unused gitbook files [\#1484](https://github.com/aklivity/zilla/pull/1484) ([jfallows](https://github.com/jfallows))
- Enhance tcp unbinding spec test [\#1483](https://github.com/aklivity/zilla/pull/1483) ([akrambek](https://github.com/akrambek))
- Overriding `correlation-id` via AsyncAPI Specification [\#1482](https://github.com/aklivity/zilla/pull/1482) ([ankitk-me](https://github.com/ankitk-me))

## [0.9.143](https://github.com/aklivity/zilla/tree/0.9.143) (2025-05-23)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.142...0.9.143)

**Implemented enhancements:**

- Collect exportable engine metric representing worker capacity usage for auto scaling [\#1465](https://github.com/aklivity/zilla/issues/1465)

**Merged pull requests:**

- Engine worker metrics support [\#1470](https://github.com/aklivity/zilla/pull/1470) ([akrambek](https://github.com/akrambek))

## [0.9.142](https://github.com/aklivity/zilla/tree/0.9.142) (2025-05-22)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.141...0.9.142)

**Fixed bugs:**

- Zilla crashes with IndexOutOfBoundException at produce with reply capability if message \> 8kb [\#1479](https://github.com/aklivity/zilla/issues/1479)

**Merged pull requests:**

- Fix large message hashing in http-kafka [\#1480](https://github.com/aklivity/zilla/pull/1480) ([bmaidics](https://github.com/bmaidics))

## [0.9.141](https://github.com/aklivity/zilla/tree/0.9.141) (2025-05-22)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.140...0.9.141)

**Merged pull requests:**

- support dynamic guarded route [\#1478](https://github.com/aklivity/zilla/pull/1478) ([ankitk-me](https://github.com/ankitk-me))

## [0.9.140](https://github.com/aklivity/zilla/tree/0.9.140) (2025-05-19)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.139...0.9.140)

**Implemented enhancements:**

- Persist gRPC error messages [\#988](https://github.com/aklivity/zilla/issues/988)

**Fixed bugs:**

- `curl` fails to connect to `zilla` in `asyncapi.sse.proxy` and `asyncapi.sse.kafka.proxy` examples [\#1417](https://github.com/aklivity/zilla/issues/1417)

**Merged pull requests:**

- support `guarded` `routes` in TestBindingFactory [\#1477](https://github.com/aklivity/zilla/pull/1477) ([ankitk-me](https://github.com/ankitk-me))
- Remove `-e` option from `echo` & add `--exclude-internal` in `list` [\#1469](https://github.com/aklivity/zilla/pull/1469) ([ankitk-me](https://github.com/ankitk-me))
- Persist gRPC custom error messages [\#1468](https://github.com/aklivity/zilla/pull/1468) ([ankitk-me](https://github.com/ankitk-me))
- fix `asyncapi.sse.kafka.proxy` example & enable `test` [\#1463](https://github.com/aklivity/zilla/pull/1463) ([ankitk-me](https://github.com/ankitk-me))
- added support for validation in asyncapi.sse flow & example fix [\#1462](https://github.com/aklivity/zilla/pull/1462) ([ankitk-me](https://github.com/ankitk-me))

## [0.9.139](https://github.com/aklivity/zilla/tree/0.9.139) (2025-04-24)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.138...0.9.139)

**Fixed bugs:**

- MQTT client not receiving events in `asyncapi.mqtt.proxy` Example [\#1416](https://github.com/aklivity/zilla/issues/1416)

**Merged pull requests:**

- Ensure SslEngine delegated task completes with signal … [\#1461](https://github.com/aklivity/zilla/pull/1461) ([jfallows](https://github.com/jfallows))
- Fix `asyncapi.mqtt.proxy` example & enable test [\#1460](https://github.com/aklivity/zilla/pull/1460) ([ankitk-me](https://github.com/ankitk-me))

## [0.9.138](https://github.com/aklivity/zilla/tree/0.9.138) (2025-04-23)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.137...0.9.138)

**Merged pull requests:**

- Simplify TLS decode loop by inlining decodeHandshakeNeedTask [\#1459](https://github.com/aklivity/zilla/pull/1459) ([jfallows](https://github.com/jfallows))

## [0.9.137](https://github.com/aklivity/zilla/tree/0.9.137) (2025-04-21)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.136...0.9.137)

**Merged pull requests:**

- Use long not int when calculating ack max [\#1458](https://github.com/aklivity/zilla/pull/1458) ([jfallows](https://github.com/jfallows))

## [0.9.136](https://github.com/aklivity/zilla/tree/0.9.136) (2025-04-17)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.135...0.9.136)

**Implemented enhancements:**

- Support mqtt topics with path parameters required to match guarded identity [\#1382](https://github.com/aklivity/zilla/issues/1382)

**Merged pull requests:**

- Use distinct idle strategy instance per agent [\#1457](https://github.com/aklivity/zilla/pull/1457) ([jfallows](https://github.com/jfallows))
- Bump ubuntu from jammy-20250126 to jammy-20250404 in /cloud/docker-image/src/main/docker [\#1453](https://github.com/aklivity/zilla/pull/1453) ([dependabot[bot]](https://github.com/apps/dependabot))

## [0.9.135](https://github.com/aklivity/zilla/tree/0.9.135) (2025-04-11)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.134...0.9.135)

**Fixed bugs:**

- Miscalculating default worker capacity [\#1456](https://github.com/aklivity/zilla/issues/1456)
- Problem with oneOf ref in schema [\#1418](https://github.com/aklivity/zilla/issues/1418)

**Merged pull requests:**

- Fix default worker capacity [\#1455](https://github.com/aklivity/zilla/pull/1455) ([akrambek](https://github.com/akrambek))
- support `oneOf` `allOf` & `anyOf` schema in `binding-openapi` & `binding-asyncapi` [\#1454](https://github.com/aklivity/zilla/pull/1454) ([ankitk-me](https://github.com/ankitk-me))
- Add `test.sh` for `amqp.reflect` & skip fix required examples [\#1452](https://github.com/aklivity/zilla/pull/1452) ([ankitk-me](https://github.com/ankitk-me))
- enable Incubator features : zilla examples [\#1451](https://github.com/aklivity/zilla/pull/1451) ([ankitk-me](https://github.com/ankitk-me))

## [0.9.134](https://github.com/aklivity/zilla/tree/0.9.134) (2025-04-07)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.133...0.9.134)

**Merged pull requests:**

- Set default buffer slot capacity to 32K [\#1449](https://github.com/aklivity/zilla/pull/1449) ([akrambek](https://github.com/akrambek))
- Set TCP flags appropriately to avoid Wireshark TCP dissector errors [\#1442](https://github.com/aklivity/zilla/pull/1442) ([jfallows](https://github.com/jfallows))
- Bump alpine from 3.21.2 to 3.21.3 in /cloud/docker-image/src/main/docker [\#1404](https://github.com/aklivity/zilla/pull/1404) ([dependabot[bot]](https://github.com/apps/dependabot))

## [0.9.133](https://github.com/aklivity/zilla/tree/0.9.133) (2025-04-03)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.132...0.9.133)

**Implemented enhancements:**

- Add a GH action to build the head of `develop` and run example tests [\#1263](https://github.com/aklivity/zilla/issues/1263)

**Merged pull requests:**

- Enforce worker capacity limit across both client and server tcp connections [\#1448](https://github.com/aklivity/zilla/pull/1448) ([akrambek](https://github.com/akrambek))
- shade jose4j in guard-jwt [\#1447](https://github.com/aklivity/zilla/pull/1447) ([ankitk-me](https://github.com/ankitk-me))
- Update workflow to run test on PRs & use `develop-SNAPSHOT` image [\#1436](https://github.com/aklivity/zilla/pull/1436) ([ankitk-me](https://github.com/ankitk-me))

## [0.9.132](https://github.com/aklivity/zilla/tree/0.9.132) (2025-04-02)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.131...0.9.132)

**Merged pull requests:**

- Bind server socket per core [\#1446](https://github.com/aklivity/zilla/pull/1446) ([akrambek](https://github.com/akrambek))
- Increase task.parallelism by default to match workers [\#1445](https://github.com/aklivity/zilla/pull/1445) ([akrambek](https://github.com/akrambek))

## [0.9.131](https://github.com/aklivity/zilla/tree/0.9.131) (2025-03-25)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.130...0.9.131)

**Implemented enhancements:**

- Auto discover available host resources and dynamically set internal limits [\#984](https://github.com/aklivity/zilla/issues/984)

**Merged pull requests:**

- Auto discover available host resources and dynamically set internal limits [\#1438](https://github.com/aklivity/zilla/pull/1438) ([akrambek](https://github.com/akrambek))

## [0.9.130](https://github.com/aklivity/zilla/tree/0.9.130) (2025-03-22)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.129...0.9.130)

**Merged pull requests:**

- Verify varuint32\(n\) unpadded size [\#1440](https://github.com/aklivity/zilla/pull/1440) ([jfallows](https://github.com/jfallows))

## [0.9.129](https://github.com/aklivity/zilla/tree/0.9.129) (2025-03-20)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.128...0.9.129)

**Implemented enhancements:**

- Support inline definition of schemas in asyncapi generation. [\#1103](https://github.com/aklivity/zilla/issues/1103)

**Merged pull requests:**

- Enhance core test functions to support padded length varstring [\#1439](https://github.com/aklivity/zilla/pull/1439) ([jfallows](https://github.com/jfallows))
- rename: `sse.jwt` example to `sse.proxy.jwt` [\#1435](https://github.com/aklivity/zilla/pull/1435) ([ankitk-me](https://github.com/ankitk-me))
- Handle reserved budget in HTTP end frame [\#1430](https://github.com/aklivity/zilla/pull/1430) ([akrambek](https://github.com/akrambek))
- Fix failing test when JVM default locale is not US [\#1359](https://github.com/aklivity/zilla/pull/1359) ([epieffe](https://github.com/epieffe))

## [0.9.128](https://github.com/aklivity/zilla/tree/0.9.128) (2025-03-07)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.127...0.9.128)

**Fixed bugs:**

- Do not include ztables and zviews into show materialized view and tables command [\#1423](https://github.com/aklivity/zilla/issues/1423)
- Uploading more than 40k size file into filesystem doesn't get fully uploaded [\#1420](https://github.com/aklivity/zilla/issues/1420)

**Merged pull requests:**

- FLUSH after insert to make SHOW command predictable [\#1427](https://github.com/aklivity/zilla/pull/1427) ([akrambek](https://github.com/akrambek))

## [0.9.127](https://github.com/aklivity/zilla/tree/0.9.127) (2025-03-04)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.126...0.9.127)

**Fixed bugs:**

- Zilla crashes with NPE in asyncapi.mqtt.kafka.proxy example [\#1415](https://github.com/aklivity/zilla/issues/1415)
- zilla crashes with `java.lang.NoClassDefFoundError: com/google/gson/JsonElement` [\#1413](https://github.com/aklivity/zilla/issues/1413)

## [0.9.126](https://github.com/aklivity/zilla/tree/0.9.126) (2025-02-24)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.125...0.9.126)

**Implemented enhancements:**

- Type boolean possible in schema [\#1408](https://github.com/aklivity/zilla/issues/1408)

**Fixed bugs:**

- Java Agent Error while sending Data to Open Telemetry Endpoint \(OTEL Endpoint\) [\#1406](https://github.com/aklivity/zilla/issues/1406)
- Handle double quote when defining the table name in risingwave [\#1379](https://github.com/aklivity/zilla/issues/1379)

**Merged pull requests:**

- Handle TLS Alert.USER\_CANCELED then deferred Alert.CLOSE\_NOTIFY [\#1411](https://github.com/aklivity/zilla/pull/1411) ([jfallows](https://github.com/jfallows))
- fix: resolveKind flow for composite binding [\#1407](https://github.com/aklivity/zilla/pull/1407) ([ankitk-me](https://github.com/ankitk-me))
- fix: MQTT subscribe routing [\#1403](https://github.com/aklivity/zilla/pull/1403) ([ankitk-me](https://github.com/ankitk-me))
- Append missing system schema to avoid exposing view in show command [\#1398](https://github.com/aklivity/zilla/pull/1398) ([akrambek](https://github.com/akrambek))

## [0.9.125](https://github.com/aklivity/zilla/tree/0.9.125) (2025-02-05)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.124...0.9.125)

**Implemented enhancements:**

- Support gRPC client streaming to Kafka directly [\#642](https://github.com/aklivity/zilla/issues/642)

**Fixed bugs:**

- Zilla Crashes on invalid request payload [\#1394](https://github.com/aklivity/zilla/issues/1394)
- Schema Retrieval Failure Causes Validation Error in Zilla [\#1391](https://github.com/aklivity/zilla/issues/1391)
- Connection refused for MQTT Kafka broker after setting up TLS on the Kafka client [\#1389](https://github.com/aklivity/zilla/issues/1389)
- Zilla Validation not working correctly [\#1385](https://github.com/aklivity/zilla/issues/1385)

**Merged pull requests:**

- Use `OpenapiView` and `AsyncapiView` to generate composite namespaces [\#1396](https://github.com/aklivity/zilla/pull/1396) ([jfallows](https://github.com/jfallows))
- fix: Zilla Crashes on invalid request payload [\#1395](https://github.com/aklivity/zilla/pull/1395) ([ankitk-me](https://github.com/ankitk-me))

## [0.9.124](https://github.com/aklivity/zilla/tree/0.9.124) (2025-01-30)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.123...0.9.124)

## [0.9.123](https://github.com/aklivity/zilla/tree/0.9.123) (2025-01-29)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.122...0.9.123)

**Implemented enhancements:**

- Allow separate Auth config for Catalog definitions [\#1195](https://github.com/aklivity/zilla/issues/1195)

## [0.9.122](https://github.com/aklivity/zilla/tree/0.9.122) (2025-01-28)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.121...0.9.122)

**Fixed bugs:**

- Logging wrong accept url when http proxy is configured [\#1380](https://github.com/aklivity/zilla/issues/1380)
- Connection to MQTT server over WebSocket fails from web browser [\#1374](https://github.com/aklivity/zilla/issues/1374)
- Zilla unable to produce to Kafka when removing the north/south cache blocks [\#1353](https://github.com/aklivity/zilla/issues/1353)

**Closed issues:**

- Unify ZFUNCTION and ZSTREAM into a single concept [\#1376](https://github.com/aklivity/zilla/issues/1376)

## [0.9.121](https://github.com/aklivity/zilla/tree/0.9.121) (2025-01-20)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.120...0.9.121)

## [0.9.120](https://github.com/aklivity/zilla/tree/0.9.120) (2025-01-20)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.119...0.9.120)

**Merged pull requests:**

- Fix checkstyle in TLS Server [\#1373](https://github.com/aklivity/zilla/pull/1373) ([ankitk-me](https://github.com/ankitk-me))

## [0.9.119](https://github.com/aklivity/zilla/tree/0.9.119) (2025-01-20)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.118...0.9.119)

**Merged pull requests:**

- Handle CN with spaces [\#1372](https://github.com/aklivity/zilla/pull/1372) ([ankitk-me](https://github.com/ankitk-me))

## [0.9.118](https://github.com/aklivity/zilla/tree/0.9.118) (2025-01-12)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.117...0.9.118)

## [0.9.117](https://github.com/aklivity/zilla/tree/0.9.117) (2025-01-10)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.116...0.9.117)

**Merged pull requests:**

- Flush the insert to have immediate effect [\#1365](https://github.com/aklivity/zilla/pull/1365) ([akrambek](https://github.com/akrambek))

## [0.9.116](https://github.com/aklivity/zilla/tree/0.9.116) (2025-01-09)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.115...0.9.116)

**Fixed bugs:**

- Zilla is unresponsive sometimes & the app logs Stopped but accepts the CRUD requests [\#1312](https://github.com/aklivity/zilla/issues/1312)
- Asyncapi kafka header extraction expression don’t match zilla yaml expressions  [\#1138](https://github.com/aklivity/zilla/issues/1138)

## [0.9.115](https://github.com/aklivity/zilla/tree/0.9.115) (2024-12-26)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.114...0.9.115)

## [0.9.114](https://github.com/aklivity/zilla/tree/0.9.114) (2024-12-20)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.113...0.9.114)

## [0.9.113](https://github.com/aklivity/zilla/tree/0.9.113) (2024-12-20)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.112...0.9.113)

## [0.9.112](https://github.com/aklivity/zilla/tree/0.9.112) (2024-12-18)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.111...0.9.112)

**Fixed bugs:**

- Docs search bar doesn't work [\#1351](https://github.com/aklivity/zilla/issues/1351)
- InputMismatchException is thrown for `grpc` binding with googleapis/devtools/build proto [\#1230](https://github.com/aklivity/zilla/issues/1230)

## [0.9.111](https://github.com/aklivity/zilla/tree/0.9.111) (2024-12-13)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.110...0.9.111)

**Implemented enhancements:**

- Replace Ivy APIs for embedded Maven during `zpmw` install [\#1173](https://github.com/aklivity/zilla/issues/1173)

## [0.9.110](https://github.com/aklivity/zilla/tree/0.9.110) (2024-12-13)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.109...0.9.110)

## [0.9.109](https://github.com/aklivity/zilla/tree/0.9.109) (2024-12-13)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.108...0.9.109)

## [0.9.108](https://github.com/aklivity/zilla/tree/0.9.108) (2024-12-12)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.107...0.9.108)

## [0.9.107](https://github.com/aklivity/zilla/tree/0.9.107) (2024-12-10)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.106...0.9.107)

## [0.9.106](https://github.com/aklivity/zilla/tree/0.9.106) (2024-12-04)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.105...0.9.106)

## [0.9.105](https://github.com/aklivity/zilla/tree/0.9.105) (2024-12-03)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.104...0.9.105)

## [0.9.104](https://github.com/aklivity/zilla/tree/0.9.104) (2024-12-02)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.103...0.9.104)

**Fixed bugs:**

- `affinity mask must specify at least one bit` crash preventing Zilla from starting on Windows 11 + Docker Compose [\#1338](https://github.com/aklivity/zilla/issues/1338)

**Merged pull requests:**

- Json serialization of nullable fields [\#1341](https://github.com/aklivity/zilla/pull/1341) ([akrambek](https://github.com/akrambek))

## [0.9.103](https://github.com/aklivity/zilla/tree/0.9.103) (2024-11-27)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.102...0.9.103)

**Fixed bugs:**

- Mqtt selecting incorrect publish stream [\#1327](https://github.com/aklivity/zilla/issues/1327)

## [0.9.102](https://github.com/aklivity/zilla/tree/0.9.102) (2024-11-13)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.101...0.9.102)

**Implemented enhancements:**

- Support `risingwave` `pgsql` message transformations [\#1208](https://github.com/aklivity/zilla/issues/1208)

## [0.9.101](https://github.com/aklivity/zilla/tree/0.9.101) (2024-11-07)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.100...0.9.101)

**Fixed bugs:**

- Running emqtt\_bench triggers exception in KafkaCacheCursorFactory [\#752](https://github.com/aklivity/zilla/issues/752)

**Closed issues:**

- Support `ALTER STREAM` and `ALTER TABLE` transformation [\#1319](https://github.com/aklivity/zilla/issues/1319)

## [0.9.100](https://github.com/aklivity/zilla/tree/0.9.100) (2024-10-29)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.99...0.9.100)

**Implemented enhancements:**

- Replace jsqlparser with antlr grammar [\#1301](https://github.com/aklivity/zilla/issues/1301)
- Support `pgsql-kafka` binding [\#1058](https://github.com/aklivity/zilla/issues/1058)
- syntax check mqtt topic names and topic filters in zilla.yaml [\#521](https://github.com/aklivity/zilla/issues/521)

**Fixed bugs:**

- Zilla `asyncapi.mqtt.kafka.proxy` crash on startup with NPE: Cannot read field "values" because the return value of "...AsyncapiServerVariableResolver.resolve\(String\)" is null [\#1304](https://github.com/aklivity/zilla/issues/1304)
- Using the Zilla MQTT broker the producer periodically disconnect and only reconnects after pod restart [\#1302](https://github.com/aklivity/zilla/issues/1302)
- SSE notifications stop after about 5-6 times when using kafka-sse [\#1291](https://github.com/aklivity/zilla/issues/1291)
- POST application/protobuf binary data can get stuck [\#1283](https://github.com/aklivity/zilla/issues/1283)
- JSON to Protobuf breaks after processing an invalid message [\#1282](https://github.com/aklivity/zilla/issues/1282)
- Zilla intermittently crashes with IndexOutOfBoundsException at high mqtt load [\#1206](https://github.com/aklivity/zilla/issues/1206)
- Load testing MQTT QOS 0,1,2 with Empty messages in the `retained` topic and Zilla crashing [\#1164](https://github.com/aklivity/zilla/issues/1164)

**Closed issues:**

- `pgsql` `DROP TOPIC` command to `KafkaDeleteTopicsBeginEx` [\#1307](https://github.com/aklivity/zilla/issues/1307)
- Log missing enviroment variables.  [\#1188](https://github.com/aklivity/zilla/issues/1188)

## [0.9.99](https://github.com/aklivity/zilla/tree/0.9.99) (2024-10-12)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.98...0.9.99)

## [0.9.98](https://github.com/aklivity/zilla/tree/0.9.98) (2024-10-10)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.97...0.9.98)

**Fixed bugs:**

- Propagate error code in risingwave binding that's coming either from pgsql-kafka or risingwave [\#1286](https://github.com/aklivity/zilla/issues/1286)
- Zilla get blocked when sending messages to kafka [\#1268](https://github.com/aklivity/zilla/issues/1268)

## [0.9.97](https://github.com/aklivity/zilla/tree/0.9.97) (2024-10-07)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.96...0.9.97)

**Implemented enhancements:**

- Support `jwt` guarded identity via custom token claim [\#1276](https://github.com/aklivity/zilla/issues/1276)
- Support `insert into` to seed `kafka` messages via `risingwave` binding [\#1274](https://github.com/aklivity/zilla/issues/1274)

## [0.9.96](https://github.com/aklivity/zilla/tree/0.9.96) (2024-10-01)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.95...0.9.96)

**Implemented enhancements:**

- Support Kafka topics create, alter, delete [\#1059](https://github.com/aklivity/zilla/issues/1059)

**Fixed bugs:**

- `zilla` Fails to Load Configuration from Specified location if the initial attempts are unsuccessful [\#1226](https://github.com/aklivity/zilla/issues/1226)

## [0.9.95](https://github.com/aklivity/zilla/tree/0.9.95) (2024-09-23)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.94...0.9.95)

**Fixed bugs:**

- NPE durring high load: `Cannot invoke "io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaEvaluation.ordinal()" because "evaluation" is null` [\#1253](https://github.com/aklivity/zilla/issues/1253)

## [0.9.94](https://github.com/aklivity/zilla/tree/0.9.94) (2024-09-21)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.93...0.9.94)

**Implemented enhancements:**

- Add more events to Zilla's connection with Kafka or other tcp clients [\#1123](https://github.com/aklivity/zilla/issues/1123)
- Ignore case when evaluating enums [\#1109](https://github.com/aklivity/zilla/issues/1109)
- Support `catalog` register new schema version [\#1060](https://github.com/aklivity/zilla/issues/1060)

**Fixed bugs:**

- Zilla KafkaGroup stream stuck on decoding FindCoordinatorResponse [\#1201](https://github.com/aklivity/zilla/issues/1201)
- NPE when a schema isn't found in a schema registry [\#1170](https://github.com/aklivity/zilla/issues/1170)
- Fetching from a log compacted topic pulls all new messages with same key on first call [\#803](https://github.com/aklivity/zilla/issues/803)

## [0.9.93](https://github.com/aklivity/zilla/tree/0.9.93) (2024-09-10)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.92...0.9.93)

**Implemented enhancements:**

- Support `pgsql` binding [\#1057](https://github.com/aklivity/zilla/issues/1057)
- Handle large HTTP headers [\#1046](https://github.com/aklivity/zilla/issues/1046)
- Gracefully handle `zilla.yml` instead of `zilla.yaml` [\#580](https://github.com/aklivity/zilla/issues/580)

**Fixed bugs:**

- Zilla OpenAPI not supporting filesystem catalog [\#1225](https://github.com/aklivity/zilla/issues/1225)
- Zilla produces corrupt messages due to incorrect CRC when fragmented [\#1221](https://github.com/aklivity/zilla/issues/1221)
- Issue referencing `guard` in `asyncapi` binding [\#1215](https://github.com/aklivity/zilla/issues/1215)
- Using catalog::apicurio triggers unexpected behaviour [\#1202](https://github.com/aklivity/zilla/issues/1202)
- `400 Bad Request` response from Zilla when Using Java HttpClient [\#1192](https://github.com/aklivity/zilla/issues/1192)
- Investigate connection pool reconnect when Kafka not yet available [\#1153](https://github.com/aklivity/zilla/issues/1153)

**Merged pull requests:**

- Update asyncapi binding module-info to open parser package [\#1227](https://github.com/aklivity/zilla/pull/1227) ([jfallows](https://github.com/jfallows))

## [0.9.92](https://github.com/aklivity/zilla/tree/0.9.92) (2024-08-13)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.91...0.9.92)

**Implemented enhancements:**

- Support `extract-key` kafka message transform [\#1176](https://github.com/aklivity/zilla/issues/1176)

## [0.9.91](https://github.com/aklivity/zilla/tree/0.9.91) (2024-08-10)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.90...0.9.91)

**Fixed bugs:**

- `asyncapi` binding triggers zilla crash when used with `catalog::apicurio` [\#1185](https://github.com/aklivity/zilla/issues/1185)
- Avro to JSON conversion problem with REST Proxy [\#1169](https://github.com/aklivity/zilla/issues/1169)

## [0.9.90](https://github.com/aklivity/zilla/tree/0.9.90) (2024-08-05)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.89...0.9.90)

**Implemented enhancements:**

- Support `extract-headers` kafka message transform [\#1175](https://github.com/aklivity/zilla/issues/1175)
- Simplify `sse` support in AsyncAPI specs [\#1151](https://github.com/aklivity/zilla/issues/1151)

**Fixed bugs:**

- Support topic pattern wildcards in `mqtt-kafka` clients [\#1178](https://github.com/aklivity/zilla/issues/1178)
- Connecting to Aiven Kafka over TLS Throws an `java.security.InvalidAlgorithmParameterException: the trustAnchors parameter must be non-empty` Error [\#1115](https://github.com/aklivity/zilla/issues/1115)

## [0.9.89](https://github.com/aklivity/zilla/tree/0.9.89) (2024-07-22)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.88...0.9.89)

**Implemented enhancements:**

- Support `asyncapi` mapping `sse` protocol to `kafka` protocol [\#1064](https://github.com/aklivity/zilla/issues/1064)
- Helm chart option for handling HTTP/2 behind an Ingress controller [\#896](https://github.com/aklivity/zilla/issues/896)
- Support `grpc` custom metadata pass through [\#730](https://github.com/aklivity/zilla/issues/730)
- Export telemetry logs to local filesystem [\#298](https://github.com/aklivity/zilla/issues/298)
- Integrate OpenTelemetry collectors by exporting local log events over OTLP [\#297](https://github.com/aklivity/zilla/issues/297)
- Design observable logs configuration syntax [\#296](https://github.com/aklivity/zilla/issues/296)
- `telemetry logs` feature [\#295](https://github.com/aklivity/zilla/issues/295)

**Fixed bugs:**

- custom metadata populated by grpc-server missing  [\#1155](https://github.com/aklivity/zilla/issues/1155)
- Avro validation returns 204 and produces blank message [\#1143](https://github.com/aklivity/zilla/issues/1143)

## [0.9.88](https://github.com/aklivity/zilla/tree/0.9.88) (2024-07-15)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.87...0.9.88)

**Implemented enhancements:**

- AsyncAPI `http-kafka` header overrides support [\#1141](https://github.com/aklivity/zilla/issues/1141)

**Fixed bugs:**

- AsyncAPI sse kafka filtering support [\#1137](https://github.com/aklivity/zilla/issues/1137)

## [0.9.87](https://github.com/aklivity/zilla/tree/0.9.87) (2024-07-12)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.86...0.9.87)

## [0.9.86](https://github.com/aklivity/zilla/tree/0.9.86) (2024-07-10)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.85...0.9.86)

## [0.9.85](https://github.com/aklivity/zilla/tree/0.9.85) (2024-07-10)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.84...0.9.85)

**Implemented enhancements:**

- add option to print version information to the cli tools [\#1066](https://github.com/aklivity/zilla/issues/1066)

**Fixed bugs:**

- Support key validation in kafka asyncapi generation [\#1105](https://github.com/aklivity/zilla/issues/1105)
- Asyncapi doesn't generate schema for catalog with avro format [\#1104](https://github.com/aklivity/zilla/issues/1104)

**Closed issues:**

- Add more context to the Kafka API event code log formatter. [\#1126](https://github.com/aklivity/zilla/issues/1126)

## [0.9.84](https://github.com/aklivity/zilla/tree/0.9.84) (2024-06-29)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.83...0.9.84)

**Implemented enhancements:**

- Verify public-private key pair obtained from vault used for TLS handshake [\#1073](https://github.com/aklivity/zilla/issues/1073)

**Closed issues:**

- feat: improve troubleshooting capabilities [\#903](https://github.com/aklivity/zilla/issues/903)

## [0.9.83](https://github.com/aklivity/zilla/tree/0.9.83) (2024-06-28)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.82...0.9.83)

**Implemented enhancements:**

- Add asyncapi http-kafka proxy example [\#1077](https://github.com/aklivity/zilla/issues/1077)
- Use miliseconds in metrics [\#1069](https://github.com/aklivity/zilla/issues/1069)
- Promote `filesystem` catalog out of incubator [\#1068](https://github.com/aklivity/zilla/issues/1068)
- Support `asyncapi` mapping `http` protocol to `kafka` protocol [\#1063](https://github.com/aklivity/zilla/issues/1063)
- Support filtering by kafka structured value field\(s\) [\#1062](https://github.com/aklivity/zilla/issues/1062)
- Support remote zilla configuration with change detection [\#1061](https://github.com/aklivity/zilla/issues/1061)
- Use full Event ID and the event name [\#1013](https://github.com/aklivity/zilla/issues/1013)
- Support configuration of MQTT Publish QoS maximum [\#970](https://github.com/aklivity/zilla/issues/970)
- Support `sse` server and client via `asyncapi` [\#952](https://github.com/aklivity/zilla/issues/952)
- Review kafka binding partition offset vs progress offset [\#285](https://github.com/aklivity/zilla/issues/285)

**Fixed bugs:**

- iNotify error when multiple Zilla instances are started in K8s Pods on a Portainer.io host [\#1081](https://github.com/aklivity/zilla/issues/1081)
- Running `emqtt_bench` `sub`  triggers an exception [\#1037](https://github.com/aklivity/zilla/issues/1037)
- MqttSessionBeginEx missing packetIds in zilla dump [\#1028](https://github.com/aklivity/zilla/issues/1028)
- Running `emqtt_bench`  triggers an exception in mqtt during the decoding [\#999](https://github.com/aklivity/zilla/issues/999)
- Intermittent NPE when trying to resolve guards [\#994](https://github.com/aklivity/zilla/issues/994)

**Closed issues:**

- Add SSE payload validation to sse-server binding [\#1076](https://github.com/aklivity/zilla/issues/1076)

## [0.9.82](https://github.com/aklivity/zilla/tree/0.9.82) (2024-05-28)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.81...0.9.82)

**Fixed bugs:**

- Zilla crashes with `IllegalArgumentException: cannot accept missingValue` when using `defaultOffset: live` [\#1051](https://github.com/aklivity/zilla/issues/1051)
- Zilla crashes on mqtt cli -T option [\#1039](https://github.com/aklivity/zilla/issues/1039)
- Running `emqtt_bench` both `sub` and `pub` triggers an exception [\#1000](https://github.com/aklivity/zilla/issues/1000)
- `http-kafka` will `fetch` messages that have been deleted by a retention policy [\#897](https://github.com/aklivity/zilla/issues/897)

## [0.9.81](https://github.com/aklivity/zilla/tree/0.9.81) (2024-05-24)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.80...0.9.81)

**Implemented enhancements:**

- Improve Starting Zilla with the CLI [\#1016](https://github.com/aklivity/zilla/issues/1016)
- Generate `zilla dump` packet captures in timestamp order including across workers [\#959](https://github.com/aklivity/zilla/issues/959)
- Split protocol testing into separate ITs for `zilla dump` command [\#958](https://github.com/aklivity/zilla/issues/958)
- Add zilla context to MQTT consumer groups [\#886](https://github.com/aklivity/zilla/issues/886)

**Fixed bugs:**

- Telemetry attribute service.name doesn't get sent correctly [\#1007](https://github.com/aklivity/zilla/issues/1007)
- Streampay `zilla` instance crashes while trying to access  `https://localhost:9090` [\#975](https://github.com/aklivity/zilla/issues/975)

## [0.9.80](https://github.com/aklivity/zilla/tree/0.9.80) (2024-05-20)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.79...0.9.80)

**Breaking changes:**

- Remove `zilla generate` command [\#960](https://github.com/aklivity/zilla/issues/960)
- Report unused properties based on binding definition [\#808](https://github.com/aklivity/zilla/issues/808)

**Implemented enhancements:**

- Update the Zilla issue Bug Report template with debugging info collection instructions [\#991](https://github.com/aklivity/zilla/issues/991)
- Support multiple specs in `openapi-asyncapi` binding [\#964](https://github.com/aklivity/zilla/issues/964)
- Integrate JMH into `tls` binding [\#961](https://github.com/aklivity/zilla/issues/961)
- Enhance validation for `openapi` and `asyncapi` bindings [\#950](https://github.com/aklivity/zilla/issues/950)
- Support multiple specs in `openapi` binding [\#949](https://github.com/aklivity/zilla/issues/949)
- Support multiple specs in `asyncapi` binding [\#948](https://github.com/aklivity/zilla/issues/948)
- Support `asyncapi` `mqtt` streetlights mapping to `kafka` streetlights [\#947](https://github.com/aklivity/zilla/issues/947)
- Support `mqtt` access log [\#945](https://github.com/aklivity/zilla/issues/945)
- Support `mqtt` client binding authorization [\#940](https://github.com/aklivity/zilla/issues/940)
- Resiliently handle `apicurio` catalog unreachable [\#938](https://github.com/aklivity/zilla/issues/938)
- Resiliently handle `karapace` catalog unreachable [\#937](https://github.com/aklivity/zilla/issues/937)
- Support local `zilla` installation on MacOS via `homebrew` [\#680](https://github.com/aklivity/zilla/issues/680)
- Update bug report template [\#820](https://github.com/aklivity/zilla/pull/820) ([vordimous](https://github.com/vordimous))

**Fixed bugs:**

- Zilla crashes with `IllegalArgumentException` when an Avro payload is fetched as `json` [\#1025](https://github.com/aklivity/zilla/issues/1025)
- MQTT-Kafka qos2: increasing tracked producer sequence number without publishing to Kafka [\#1014](https://github.com/aklivity/zilla/issues/1014)
- OTLP `logs` signal doesn't show up in OpenTelemetry Demo [\#1006](https://github.com/aklivity/zilla/issues/1006)
- Flow control issue in openapi binding [\#1004](https://github.com/aklivity/zilla/issues/1004)
- `mqtt` connecting with longer client id fails [\#1003](https://github.com/aklivity/zilla/issues/1003)
- Running zilla with the `kafka-grpc` binding in a cluster with multiple instances results in each instance delivering a message to the configured `remote_server` [\#882](https://github.com/aklivity/zilla/issues/882)
- Using the `grpc.kafka.proxy` example setup, Zilla will periodically not deliver the message to the gRPC service defined in the `kafka-grpc` binding [\#881](https://github.com/aklivity/zilla/issues/881)
- Flaky kafka-grpc test [\#768](https://github.com/aklivity/zilla/issues/768)

**Merged pull requests:**

- Generate correct crc32c value for the messages with different produceId [\#1011](https://github.com/aklivity/zilla/pull/1011) ([akrambek](https://github.com/akrambek))
- Remove generate command [\#1010](https://github.com/aklivity/zilla/pull/1010) ([attilakreiner](https://github.com/attilakreiner))

## [0.9.79](https://github.com/aklivity/zilla/tree/0.9.79) (2024-04-22)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.78...0.9.79)

**Implemented enhancements:**

- Support `filesystem` catalog for local schemas [\#908](https://github.com/aklivity/zilla/issues/908)
- Check for files on startup when the zilla.yaml specifies paths to files or directories [\#292](https://github.com/aklivity/zilla/issues/292)

## [0.9.78](https://github.com/aklivity/zilla/tree/0.9.78) (2024-04-16)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.77...0.9.78)

## [0.9.77](https://github.com/aklivity/zilla/tree/0.9.77) (2024-04-15)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.76...0.9.77)

## [0.9.76](https://github.com/aklivity/zilla/tree/0.9.76) (2024-04-15)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.75...0.9.76)

## [0.9.75](https://github.com/aklivity/zilla/tree/0.9.75) (2024-04-12)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.74...0.9.75)

**Implemented enhancements:**

- Support logging of events caused by Model [\#887](https://github.com/aklivity/zilla/issues/887)
- Helm chart QoL improvements [\#884](https://github.com/aklivity/zilla/issues/884)
- Support `mqtt` streetlights AsyncAPI validating proxy [\#880](https://github.com/aklivity/zilla/issues/880)
- Promote components out of incubator [\#879](https://github.com/aklivity/zilla/issues/879)
- Support specific server in AsyncAPI spec in `asyncapi` binding [\#878](https://github.com/aklivity/zilla/issues/878)
- Support specific server in OpenAPI spec in `openapi` binding [\#877](https://github.com/aklivity/zilla/issues/877)
- Support HTTP prefer async with OpenAPI [\#876](https://github.com/aklivity/zilla/issues/876)
- Support OpenAPI and AsyncAPI validation cases [\#814](https://github.com/aklivity/zilla/issues/814)
- Integrate `openapi` and `asyncapi` with `catalog` [\#813](https://github.com/aklivity/zilla/issues/813)

**Fixed bugs:**

- Error in JsonValidatorHandler when the remote registry can't be accessed [\#817](https://github.com/aklivity/zilla/issues/817)
- Zilla doesn't communicate with bitnami/kafka in Taxi demo [\#690](https://github.com/aklivity/zilla/issues/690)
- Bootstrap options on cache\_client vs cache\_server for the kafka binding [\#388](https://github.com/aklivity/zilla/issues/388)

**Merged pull requests:**

- Support latest version in Apicurio [\#907](https://github.com/aklivity/zilla/pull/907) ([bmaidics](https://github.com/bmaidics))
- Remove name from asyncapi.specs.servers [\#906](https://github.com/aklivity/zilla/pull/906) ([bmaidics](https://github.com/bmaidics))
- Support specific server in AsyncAPI spec in asyncapi binding [\#883](https://github.com/aklivity/zilla/pull/883) ([bmaidics](https://github.com/bmaidics))
- Number Validator improvement to support OpenAPI & AsyncAPI specs [\#875](https://github.com/aklivity/zilla/pull/875) ([ankitk-me](https://github.com/ankitk-me))
- Event logs for Model [\#874](https://github.com/aklivity/zilla/pull/874) ([ankitk-me](https://github.com/ankitk-me))
- String Validator improvement to support OpenAPI & AsyncAPI specs [\#873](https://github.com/aklivity/zilla/pull/873) ([ankitk-me](https://github.com/ankitk-me))
- Schema fixes + avoiding duplicate reply begin on mqtt-kafka subscribe stream [\#872](https://github.com/aklivity/zilla/pull/872) ([bmaidics](https://github.com/bmaidics))
- Support logging of schema without expressions [\#871](https://github.com/aklivity/zilla/pull/871) ([jfallows](https://github.com/jfallows))
- Fix metrics [\#869](https://github.com/aklivity/zilla/pull/869) ([attilakreiner](https://github.com/attilakreiner))
- Support metrics in openapi and asyncapi [\#868](https://github.com/aklivity/zilla/pull/868) ([akrambek](https://github.com/akrambek))
- Update README.md [\#867](https://github.com/aklivity/zilla/pull/867) ([llukyanov](https://github.com/llukyanov))
- Integer Validator improvement to support OpenAPI & AsyncAPI specs [\#830](https://github.com/aklivity/zilla/pull/830) ([ankitk-me](https://github.com/ankitk-me))

## [0.9.74](https://github.com/aklivity/zilla/tree/0.9.74) (2024-03-19)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.73...0.9.74)

**Merged pull requests:**

- Fix http header value offset [\#865](https://github.com/aklivity/zilla/pull/865) ([akrambek](https://github.com/akrambek))
- Support non-404 status codes on authorization failure [\#864](https://github.com/aklivity/zilla/pull/864) ([jfallows](https://github.com/jfallows))

## [0.9.73](https://github.com/aklivity/zilla/tree/0.9.73) (2024-03-18)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.72...0.9.73)

**Merged pull requests:**

- Read buffer pool size from file when readonly [\#863](https://github.com/aklivity/zilla/pull/863) ([jfallows](https://github.com/jfallows))
- Resolve top level namespace guards in composite namespaces [\#862](https://github.com/aklivity/zilla/pull/862) ([jfallows](https://github.com/jfallows))
- Openapi bug fixes [\#861](https://github.com/aklivity/zilla/pull/861) ([akrambek](https://github.com/akrambek))
- Populate guarded qname for composite namespaces [\#860](https://github.com/aklivity/zilla/pull/860) ([jfallows](https://github.com/jfallows))
- Fix qvault on asyncapi composite binding [\#858](https://github.com/aklivity/zilla/pull/858) ([bmaidics](https://github.com/bmaidics))
- Support guarded qname for composite namespaces [\#857](https://github.com/aklivity/zilla/pull/857) ([jfallows](https://github.com/jfallows))

## [0.9.72](https://github.com/aklivity/zilla/tree/0.9.72) (2024-03-17)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.71...0.9.72)

**Merged pull requests:**

- Log http access event before validation in both http/1.1 and h2 [\#856](https://github.com/aklivity/zilla/pull/856) ([jfallows](https://github.com/jfallows))
- Fail when failed to acquire budget index [\#855](https://github.com/aklivity/zilla/pull/855) ([bmaidics](https://github.com/bmaidics))
- Conditionally release buffer slot on clean up [\#854](https://github.com/aklivity/zilla/pull/854) ([akrambek](https://github.com/akrambek))
- Fix exporter-otlp schema [\#852](https://github.com/aklivity/zilla/pull/852) ([attilakreiner](https://github.com/attilakreiner))

## [0.9.71](https://github.com/aklivity/zilla/tree/0.9.71) (2024-03-15)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.70...0.9.71)

**Implemented enhancements:**

- Support `apicurio` as `catalog` for specifications [\#812](https://github.com/aklivity/zilla/issues/812)
- Support remote logging of events via `otlp` [\#785](https://github.com/aklivity/zilla/issues/785)

**Fixed bugs:**

- Openapi and asyncapi parsers throw a null pointer when a none 0 patch version is used. [\#841](https://github.com/aklivity/zilla/issues/841)
- mosquitto\_pub qos 0 fails validation with a valid message [\#838](https://github.com/aklivity/zilla/issues/838)
- Zilla Quickstart gRPC RouteGuide service hangs after lots of messages [\#719](https://github.com/aklivity/zilla/issues/719)

**Merged pull requests:**

- Add key filter support in openapi asyncapi mapping [\#851](https://github.com/aklivity/zilla/pull/851) ([akrambek](https://github.com/akrambek))
- Support verbose output of internally generated composite namespaces [\#850](https://github.com/aklivity/zilla/pull/850) ([jfallows](https://github.com/jfallows))
- Use correct offset when response has no record set [\#849](https://github.com/aklivity/zilla/pull/849) ([akrambek](https://github.com/akrambek))
- Fix binding metadata for composite bindings [\#847](https://github.com/aklivity/zilla/pull/847) ([attilakreiner](https://github.com/attilakreiner))
- CacheProduceIT.shouldRejectMessageValues nondeterministic failure fix [\#845](https://github.com/aklivity/zilla/pull/845) ([ankitk-me](https://github.com/ankitk-me))
- Implement JoinGroup request as first class stream [\#844](https://github.com/aklivity/zilla/pull/844) ([akrambek](https://github.com/akrambek))
- Fix patch detection in openapi and asyncapi [\#843](https://github.com/aklivity/zilla/pull/843) ([akrambek](https://github.com/akrambek))
- Fix mosquitto\_pub fails validation with a valid message [\#840](https://github.com/aklivity/zilla/pull/840) ([bmaidics](https://github.com/bmaidics))
- Add event logs to open telemetry exporter [\#839](https://github.com/aklivity/zilla/pull/839) ([attilakreiner](https://github.com/attilakreiner))
- Add Apicurio catalog [\#827](https://github.com/aklivity/zilla/pull/827) ([bmaidics](https://github.com/bmaidics))

## [0.9.70](https://github.com/aklivity/zilla/tree/0.9.70) (2024-03-07)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.69...0.9.70)

**Fixed bugs:**

- Using parameter expansion in bash doesn't work in the docker containers.  [\#829](https://github.com/aklivity/zilla/issues/829)
- Zilla crashes when a lot of MQTT clients are connected [\#762](https://github.com/aklivity/zilla/issues/762)

**Merged pull requests:**

- Fix options name, port resolving [\#833](https://github.com/aklivity/zilla/pull/833) ([bmaidics](https://github.com/bmaidics))
- Stabilize Asyncapi test with race condition [\#832](https://github.com/aklivity/zilla/pull/832) ([bmaidics](https://github.com/bmaidics))
- Simplify zilla shell script logic for sh on container images [\#831](https://github.com/aklivity/zilla/pull/831) ([jfallows](https://github.com/jfallows))
- Fix NPE in connection pool due to race condition [\#828](https://github.com/aklivity/zilla/pull/828) ([akrambek](https://github.com/akrambek))
- Refactoring event logs [\#821](https://github.com/aklivity/zilla/pull/821) ([attilakreiner](https://github.com/attilakreiner))

## [0.9.69](https://github.com/aklivity/zilla/tree/0.9.69) (2024-03-04)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.68...0.9.69)

**Implemented enhancements:**

- Use dedicated env var to enable Incubator features [\#800](https://github.com/aklivity/zilla/issues/800)
- Support `http` to `kafka` proxy using `openapi.yaml` and `asyncapi.yaml` [\#742](https://github.com/aklivity/zilla/issues/742)
- Support `mqtt` to `kafka` proxy using `asyncapi.yaml` [\#741](https://github.com/aklivity/zilla/issues/741)
- Support `openapi` `http` proxy using `openapi.yaml` [\#740](https://github.com/aklivity/zilla/issues/740)
- Support `asyncapi` `http` proxy using `asyncapi.yaml` [\#739](https://github.com/aklivity/zilla/issues/739)
- Support `asyncapi` `mqtt` proxy using `asyncapi.yaml` [\#738](https://github.com/aklivity/zilla/issues/738)
- Support local logging of events caused by external actors [\#679](https://github.com/aklivity/zilla/issues/679)
- Support parameters in KafkaTopicsConfig [\#809](https://github.com/aklivity/zilla/pull/809) ([bmaidics](https://github.com/bmaidics))

**Fixed bugs:**

- SEVERE: Problem adapting object of type class NamespaceConfig to interface jakarta.json.JsonObject in class class NamespaceAdapter [\#796](https://github.com/aklivity/zilla/issues/796)
- Zilla is validating `env` vars before replacing them.  [\#795](https://github.com/aklivity/zilla/issues/795)
- Basic Docker Compose Setup Clogs CPU With Error Messages [\#722](https://github.com/aklivity/zilla/issues/722)

**Merged pull requests:**

- Asyncapi and Openapi bug fixes [\#826](https://github.com/aklivity/zilla/pull/826) ([akrambek](https://github.com/akrambek))
- Asyncapi catalog implementation [\#825](https://github.com/aklivity/zilla/pull/825) ([bmaidics](https://github.com/bmaidics))
- Fix NPE in KafkaSignalStream [\#823](https://github.com/aklivity/zilla/pull/823) ([bmaidics](https://github.com/bmaidics))
- Fix early flush sending for retained stream [\#822](https://github.com/aklivity/zilla/pull/822) ([bmaidics](https://github.com/bmaidics))
- Add incubating annotation for stdout exporter [\#819](https://github.com/aklivity/zilla/pull/819) ([jfallows](https://github.com/jfallows))
- MQTT-Kafka asyncapi proxy [\#818](https://github.com/aklivity/zilla/pull/818) ([bmaidics](https://github.com/bmaidics))
- Fix kafka client composite resolvedId [\#816](https://github.com/aklivity/zilla/pull/816) ([bmaidics](https://github.com/bmaidics))
- Use env var to add incubator java option [\#811](https://github.com/aklivity/zilla/pull/811) ([vordimous](https://github.com/vordimous))
- Support http to kafka proxy using openapi.yaml and asyncapi.yaml [\#810](https://github.com/aklivity/zilla/pull/810) ([akrambek](https://github.com/akrambek))
- Structured models require `catalog` config [\#807](https://github.com/aklivity/zilla/pull/807) ([ankitk-me](https://github.com/ankitk-me))
- Include qualified vault name on binding [\#806](https://github.com/aklivity/zilla/pull/806) ([jfallows](https://github.com/jfallows))
- Include config exception cause [\#805](https://github.com/aklivity/zilla/pull/805) ([jfallows](https://github.com/jfallows))
- Kafka asyncapi client [\#804](https://github.com/aklivity/zilla/pull/804) ([bmaidics](https://github.com/bmaidics))
- Support k3po ephemeral option [\#801](https://github.com/aklivity/zilla/pull/801) ([akrambek](https://github.com/akrambek))
- Support asyncapi http proxy using asyncapi.yaml [\#799](https://github.com/aklivity/zilla/pull/799) ([bmaidics](https://github.com/bmaidics))
- Fix kafka sasl schema validation to support expressions [\#798](https://github.com/aklivity/zilla/pull/798) ([akrambek](https://github.com/akrambek))
- Zilla is validating env vars before replacing them [\#797](https://github.com/aklivity/zilla/pull/797) ([akrambek](https://github.com/akrambek))
- Support openapi http proxy using openapi.yaml [\#778](https://github.com/aklivity/zilla/pull/778) ([akrambek](https://github.com/akrambek))

## [0.9.68](https://github.com/aklivity/zilla/tree/0.9.68) (2024-02-13)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.67...0.9.68)

**Fixed bugs:**

- Zilla crashes when a large number of MQTT clients connect [\#793](https://github.com/aklivity/zilla/issues/793)

## [0.9.67](https://github.com/aklivity/zilla/tree/0.9.67) (2024-02-11)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.66...0.9.67)

**Implemented enhancements:**

- Use `model` and `view` when describing the message type [\#750](https://github.com/aklivity/zilla/issues/750)
- Support obtaining `protobuf` schemas from `schema registry` for `grpc` services [\#697](https://github.com/aklivity/zilla/issues/697)
- Support idempotent `mqtt` `qos 2` publish to `kafka` [\#677](https://github.com/aklivity/zilla/issues/677)
- Detect and inspect invalid messages received [\#676](https://github.com/aklivity/zilla/issues/676)
- Support incremental validation of fragmented messages sent by client [\#671](https://github.com/aklivity/zilla/issues/671)
- Catalog cache TTL implementation [\#658](https://github.com/aklivity/zilla/pull/658) ([ankitk-me](https://github.com/ankitk-me))

**Fixed bugs:**

- TLSv1.3 client handshake stall [\#791](https://github.com/aklivity/zilla/issues/791)
- Zilla crashes when it tries to send flush on retain stream [\#770](https://github.com/aklivity/zilla/issues/770)
- Running emqtt\_bench triggers exception in connection pool [\#716](https://github.com/aklivity/zilla/issues/716)
- `mqtt-kafka` does not limit client sharding to `mqtt v5` [\#708](https://github.com/aklivity/zilla/issues/708)
- `tls binding` should handle `null` key returned from `vault` [\#395](https://github.com/aklivity/zilla/issues/395)

**Merged pull requests:**

- Simplify TLSv1.3 handshake check [\#792](https://github.com/aklivity/zilla/pull/792) ([jfallows](https://github.com/jfallows))
-  Log validation failure of HTTP messages \(stdout\) [\#781](https://github.com/aklivity/zilla/pull/781) ([ankitk-me](https://github.com/ankitk-me))
- Supply client id by host only, and move defaulting to caller [\#780](https://github.com/aklivity/zilla/pull/780) ([jfallows](https://github.com/jfallows))
- Handle unknown vault keys in tls binding [\#779](https://github.com/aklivity/zilla/pull/779) ([jfallows](https://github.com/jfallows))
- Refactor to use kafka server config per client network stream… [\#777](https://github.com/aklivity/zilla/pull/777) ([jfallows](https://github.com/jfallows))
- update docker-image pom.xml to refer model modules [\#775](https://github.com/aklivity/zilla/pull/775) ([ankitk-me](https://github.com/ankitk-me))
-  Skip invalid Kafka messages during Fetch [\#774](https://github.com/aklivity/zilla/pull/774) ([ankitk-me](https://github.com/ankitk-me))
- Refactoring supplyValidator to MqttServerFactory [\#773](https://github.com/aklivity/zilla/pull/773) ([ankitk-me](https://github.com/ankitk-me))
- TTL based cache update cleanup [\#772](https://github.com/aklivity/zilla/pull/772) ([ankitk-me](https://github.com/ankitk-me))
- Model specific cache detect schema change update [\#767](https://github.com/aklivity/zilla/pull/767) ([ankitk-me](https://github.com/ankitk-me))
- feature/schema-registry catchup with develop [\#765](https://github.com/aklivity/zilla/pull/765) ([ankitk-me](https://github.com/ankitk-me))
- Fragment validator interface & implementation [\#735](https://github.com/aklivity/zilla/pull/735) ([ankitk-me](https://github.com/ankitk-me))
- Qos2 idempotent producer [\#733](https://github.com/aklivity/zilla/pull/733) ([bmaidics](https://github.com/bmaidics))
- Protobuf Validation & Conversion [\#691](https://github.com/aklivity/zilla/pull/691) ([ankitk-me](https://github.com/ankitk-me))

## [0.9.66](https://github.com/aklivity/zilla/tree/0.9.66) (2024-01-24)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.65...0.9.66)

**Implemented enhancements:**

- Support `openapi` `http` response validation [\#684](https://github.com/aklivity/zilla/issues/684)
- Support `protobuf` conversion to and from `json` for `kafka` messages [\#682](https://github.com/aklivity/zilla/issues/682)
- Support incubator features preview in zilla release docker image [\#670](https://github.com/aklivity/zilla/issues/670)

**Fixed bugs:**

- Schema validation fails before the `${{env.*}}` parameters have been removed [\#583](https://github.com/aklivity/zilla/issues/583)

**Merged pull requests:**

- Refactor resolvers to support configuration [\#758](https://github.com/aklivity/zilla/pull/758) ([jfallows](https://github.com/jfallows))
- Fix docker file path [\#756](https://github.com/aklivity/zilla/pull/756) ([akrambek](https://github.com/akrambek))
-  Implement response validation in http client binding [\#732](https://github.com/aklivity/zilla/pull/732) ([attilakreiner](https://github.com/attilakreiner))

## [0.9.65](https://github.com/aklivity/zilla/tree/0.9.65) (2024-01-18)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.64...0.9.65)

**Implemented enhancements:**

- Support `avro` conversion to and from `json` for `kafka` messages [\#681](https://github.com/aklivity/zilla/issues/681)
- Support observability of zilla engine internal streams [\#678](https://github.com/aklivity/zilla/issues/678)
- Simplify configuration of multiple protocols on different tcp ports [\#669](https://github.com/aklivity/zilla/issues/669)
- Simplify kafka client bootstrap server names and ports config [\#619](https://github.com/aklivity/zilla/issues/619)
- MQTT publish QoS 2 as Kafka produce with acks in\_sync\_replicas and idempotent `producerId` [\#605](https://github.com/aklivity/zilla/issues/605)
- Add the option to route by `port` in the `tls` binding [\#564](https://github.com/aklivity/zilla/issues/564)
- Support outbound message transformation from `protobuf` to `json` [\#458](https://github.com/aklivity/zilla/issues/458)
- Support inbound message transformation from `json` to `protobuf` [\#457](https://github.com/aklivity/zilla/issues/457)
- Support outbound message transformation from `avro` to `json` [\#315](https://github.com/aklivity/zilla/issues/315)
- Support inbound message transformation from `json` to `avro` [\#313](https://github.com/aklivity/zilla/issues/313)
- Handle data fragmentation for MQTT binding [\#282](https://github.com/aklivity/zilla/issues/282)
- Add `sse`, `ws`, `fs` extension parsing to `dump` command [\#660](https://github.com/aklivity/zilla/pull/660) ([attilakreiner](https://github.com/attilakreiner))
- Support MQTT fragmented messages [\#651](https://github.com/aklivity/zilla/pull/651) ([bmaidics](https://github.com/bmaidics))

**Fixed bugs:**

- Unable to Run MQTT Example Successfully [\#724](https://github.com/aklivity/zilla/issues/724)
- Http1 server not progressing after reaching full buffer slot size [\#715](https://github.com/aklivity/zilla/issues/715)
- `mqtt-kafka` binding uses 2 different consumer groups per `mqtt` client [\#698](https://github.com/aklivity/zilla/issues/698)
- Optimize memory allocation for `mqtt-kafka` offset tracking [\#675](https://github.com/aklivity/zilla/issues/675)
- connection pool stops handling signals after while causing mqtt client to hang [\#667](https://github.com/aklivity/zilla/issues/667)
- Kafka Merge is getting stall because of intermediate partition offset state [\#666](https://github.com/aklivity/zilla/issues/666)
- Handle large message in grpc binding [\#648](https://github.com/aklivity/zilla/issues/648)
- update zilla jsonschemas [\#637](https://github.com/aklivity/zilla/issues/637)
- Mqtt session takeover is not working when the second client connects to the same Zilla instance [\#620](https://github.com/aklivity/zilla/issues/620)
- http2.network.ConnectionManagementIT.serverSent100kMessage test fails sporadically due to race [\#134](https://github.com/aklivity/zilla/issues/134)
- Fix tcp flow control issue [\#704](https://github.com/aklivity/zilla/pull/704) ([bmaidics](https://github.com/bmaidics))
- Optimize memory allocation for mqtt-kafka offset tracking [\#694](https://github.com/aklivity/zilla/pull/694) ([bmaidics](https://github.com/bmaidics))
- Send disconnect even without mqtt reset extension [\#689](https://github.com/aklivity/zilla/pull/689) ([bmaidics](https://github.com/bmaidics))

**Closed issues:**

- Prototype composite binding support with nested namespaces [\#685](https://github.com/aklivity/zilla/issues/685)
- Build has been failed in local [\#229](https://github.com/aklivity/zilla/issues/229)

**Merged pull requests:**

- Support composite binding config [\#737](https://github.com/aklivity/zilla/pull/737) ([jfallows](https://github.com/jfallows))
- Http1 server not progressing after reaching full buffer slot size [\#714](https://github.com/aklivity/zilla/pull/714) ([akrambek](https://github.com/akrambek))
- Bump org.apache.maven:maven from 3.9.4 to 3.9.6 [\#712](https://github.com/aklivity/zilla/pull/712) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump org.apache.maven.plugins:maven-compiler-plugin from 3.11.0 to 3.12.1 [\#711](https://github.com/aklivity/zilla/pull/711) ([dependabot[bot]](https://github.com/apps/dependabot))
- Simplify kafka client bootstrap server names and ports config [\#710](https://github.com/aklivity/zilla/pull/710) ([akrambek](https://github.com/akrambek))
- Align tcp net read window [\#709](https://github.com/aklivity/zilla/pull/709) ([jfallows](https://github.com/jfallows))
- Add kafka extension parsing to dump command [\#706](https://github.com/aklivity/zilla/pull/706) ([attilakreiner](https://github.com/attilakreiner))
- Bump org.codehaus.mojo:exec-maven-plugin from 3.1.0 to 3.1.1 [\#703](https://github.com/aklivity/zilla/pull/703) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump org.jacoco:jacoco-maven-plugin from 0.8.10 to 0.8.11 [\#701](https://github.com/aklivity/zilla/pull/701) ([dependabot[bot]](https://github.com/apps/dependabot))
- Unnecessary deferred value causes the connection to stall [\#700](https://github.com/aklivity/zilla/pull/700) ([akrambek](https://github.com/akrambek))
- Reject stream if deferred is not set for the fragmented message [\#693](https://github.com/aklivity/zilla/pull/693) ([akrambek](https://github.com/akrambek))
- Remove wrong state assignment in the group cache [\#692](https://github.com/aklivity/zilla/pull/692) ([akrambek](https://github.com/akrambek))
- Bump org.moditect:moditect-maven-plugin from 1.0.0.Final to 1.1.0 [\#688](https://github.com/aklivity/zilla/pull/688) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump commons-cli:commons-cli from 1.3.1 to 1.6.0 [\#687](https://github.com/aklivity/zilla/pull/687) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump junit.version from 5.8.2 to 5.10.1 [\#686](https://github.com/aklivity/zilla/pull/686) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump jakarta.json:jakarta.json-api from 2.0.1 to 2.1.3 [\#674](https://github.com/aklivity/zilla/pull/674) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump jmh.version from 1.12 to 1.37 [\#673](https://github.com/aklivity/zilla/pull/673) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump com.guicedee.services:commons-collections4 from 1.1.0.7 to 1.2.2.1 [\#672](https://github.com/aklivity/zilla/pull/672) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump org.mockito:mockito-core from 5.3.1 to 5.8.0 [\#665](https://github.com/aklivity/zilla/pull/665) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump org.slf4j:slf4j-api from 1.7.36 to 2.0.10 [\#664](https://github.com/aklivity/zilla/pull/664) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump org.hamcrest:hamcrest-library from 1.3 to 2.2 [\#663](https://github.com/aklivity/zilla/pull/663) ([dependabot[bot]](https://github.com/apps/dependabot))
- Update latest and stable offset if it was in stabilizing state [\#661](https://github.com/aklivity/zilla/pull/661) ([akrambek](https://github.com/akrambek))
- Release kafka connection pool budget [\#659](https://github.com/aklivity/zilla/pull/659) ([akrambek](https://github.com/akrambek))

## [0.9.64](https://github.com/aklivity/zilla/tree/0.9.64) (2023-12-25)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.63...0.9.64)

**Merged pull requests:**

- MQTT topic sharding [\#657](https://github.com/aklivity/zilla/pull/657) ([jfallows](https://github.com/jfallows))
- Move everything except fetch and produce to use connection pool [\#656](https://github.com/aklivity/zilla/pull/656) ([akrambek](https://github.com/akrambek))

## [0.9.63](https://github.com/aklivity/zilla/tree/0.9.63) (2023-12-25)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.62...0.9.63)

**Implemented enhancements:**

- Support MQTT message expiry in `mqtt-kafka` mapping [\#631](https://github.com/aklivity/zilla/issues/631)
- Add grpc extension parsing to the dump command [\#652](https://github.com/aklivity/zilla/pull/652) ([attilakreiner](https://github.com/attilakreiner))

**Fixed bugs:**

- OffsetFetch Request should connect to the coordinator instead of a random member of cluster [\#653](https://github.com/aklivity/zilla/issues/653)

**Closed issues:**

- gRPC remote\_server gets duplicate messages [\#480](https://github.com/aklivity/zilla/issues/480)
- Log compaction behavior with or without bootstrap is not consistent [\#389](https://github.com/aklivity/zilla/issues/389)

**Merged pull requests:**

- Fix static field [\#655](https://github.com/aklivity/zilla/pull/655) ([akrambek](https://github.com/akrambek))
- OffsetFetch Request should connect to the coordinator instead of a random member of cluster  [\#654](https://github.com/aklivity/zilla/pull/654) ([akrambek](https://github.com/akrambek))
- Bump actions/upload-artifact from 3 to 4 [\#645](https://github.com/aklivity/zilla/pull/645) ([dependabot[bot]](https://github.com/apps/dependabot))

## [0.9.62](https://github.com/aklivity/zilla/tree/0.9.62) (2023-12-13)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.61...0.9.62)

**Closed issues:**

- MQTT sessions don't show up in Redpanda [\#585](https://github.com/aklivity/zilla/issues/585)

## [0.9.61](https://github.com/aklivity/zilla/tree/0.9.61) (2023-12-10)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.60...0.9.61)

**Implemented enhancements:**

- Kafka GRPC consumer Group Support [\#597](https://github.com/aklivity/zilla/issues/597)
- Integrate Schema Registry / Karapace [\#404](https://github.com/aklivity/zilla/issues/404)
- Apply typed schema to cached messages; `kafka cache fetch server` [\#314](https://github.com/aklivity/zilla/issues/314)
- `schema-registry` feature \(baseline\) [\#265](https://github.com/aklivity/zilla/issues/265)
- Enhance inspection of internal streams [\#154](https://github.com/aklivity/zilla/issues/154)

**Fixed bugs:**

- Group Coordinator sasl scram doesn't have complete full handshake [\#624](https://github.com/aklivity/zilla/issues/624)
- Follow kafka consumer protocol data structure for userdata parsing [\#617](https://github.com/aklivity/zilla/issues/617)
- WebSocket inbound `ping` frames are rejected [\#606](https://github.com/aklivity/zilla/issues/606)

**Closed issues:**

- MQTT client is disconnected and cannot reconnect after sending message [\#623](https://github.com/aklivity/zilla/issues/623)
- Use affinity and Long2ObjectHashmap instead of clientId  [\#432](https://github.com/aklivity/zilla/issues/432)

## [0.9.60](https://github.com/aklivity/zilla/tree/0.9.60) (2023-12-05)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.59...0.9.60)

**Implemented enhancements:**

- MQTT publish QoS 1 as Kafka produce with acks in\_sync\_replicas [\#604](https://github.com/aklivity/zilla/issues/604)
- MQTT subscribe QoS 2 as stateful Kafka fetch with `consumerId` for message delivery retry [\#603](https://github.com/aklivity/zilla/issues/603)
- MQTT subscribe QoS 1 as stateful Kafka fetch with `consumerId` for message delivery retry [\#602](https://github.com/aklivity/zilla/issues/602)
- Include metadata in merge reply begin ex [\#601](https://github.com/aklivity/zilla/issues/601)
- Consumer group message acknowledgement support [\#588](https://github.com/aklivity/zilla/issues/588)
- Support mqtt protocol v3.1.1 [\#541](https://github.com/aklivity/zilla/issues/541)
- Generate `http` server request `validators` from `OpenAPI` specification [\#459](https://github.com/aklivity/zilla/issues/459)

**Fixed bugs:**

- the `tls` binding throws NPE if there are no `options` defined [\#612](https://github.com/aklivity/zilla/issues/612)
- Offset commit request should have next offset instead of consumer message offset [\#592](https://github.com/aklivity/zilla/issues/592)
- `group.min.session.timeout.ms` is null using zilla in front of redpanda [\#581](https://github.com/aklivity/zilla/issues/581)
- java.lang.IllegalStateException: missing file for budgets : /var/run/zilla/budgets127 [\#578](https://github.com/aklivity/zilla/issues/578)

**Closed issues:**

- `prometheus` schema Port and `tcp` schema Port have different validation [\#569](https://github.com/aklivity/zilla/issues/569)
- zilla:correlation-id header sort [\#508](https://github.com/aklivity/zilla/issues/508)

## [0.9.59](https://github.com/aklivity/zilla/tree/0.9.59) (2023-11-21)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.58...0.9.59)

**Implemented enhancements:**

- Generate `http` server request `validators` from `AsyncAPI` specification [\#460](https://github.com/aklivity/zilla/issues/460)

**Fixed bugs:**

- MQTT topic routing doesn't correctly reject pub/sub requests [\#572](https://github.com/aklivity/zilla/issues/572)

**Closed issues:**

- Empty messages on `retained` topic for all MQTT messages [\#575](https://github.com/aklivity/zilla/issues/575)
- \[DOCS\] Fix typos in README file [\#540](https://github.com/aklivity/zilla/issues/540)

## [0.9.58](https://github.com/aklivity/zilla/tree/0.9.58) (2023-11-13)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.57...0.9.58)

**Implemented enhancements:**

- Integrate `http` binding with `validators` [\#455](https://github.com/aklivity/zilla/issues/455)

**Fixed bugs:**

- \[MQTT-Kafka\] Exception runtime.binding.mqtt.kafka.internal.types.MqttExpirySignalFW.wrap\(MqttExpirySignalFW.java:45\) [\#563](https://github.com/aklivity/zilla/issues/563)
- Running mqtt benchmark triggers mqtt exception [\#488](https://github.com/aklivity/zilla/issues/488)

## [0.9.57](https://github.com/aklivity/zilla/tree/0.9.57) (2023-11-04)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.56...0.9.57)

**Fixed bugs:**

- \[Connection Pool\] binding.kafka.internal.stream.KafkaClientConnectionPool$KafkaClientConnection.doConnectionWindow\(KafkaClientConnectionPool.java:1318\) [\#565](https://github.com/aklivity/zilla/issues/565)
- \[MQTT-Kafka\] Randomly closing the connection in the middle of produce triggers the exception [\#559](https://github.com/aklivity/zilla/issues/559)
- Gracefully handle out of slot exception in kafka cache client produce [\#558](https://github.com/aklivity/zilla/issues/558)
- \[Connection Pool\] Signaling can trigger exception [\#557](https://github.com/aklivity/zilla/issues/557)
- `http-kafka` fetch binding returns malformed JSON when the payload is large  [\#528](https://github.com/aklivity/zilla/issues/528)

## [0.9.56](https://github.com/aklivity/zilla/tree/0.9.56) (2023-10-31)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.55...0.9.56)

**Implemented enhancements:**

- Support `extraEnv` in helm chart [\#520](https://github.com/aklivity/zilla/issues/520)
- `kubernetes autoscaling` feature \(enhanced\) [\#518](https://github.com/aklivity/zilla/issues/518)
- Shard MQTT topic space for client-id specific subset [\#427](https://github.com/aklivity/zilla/issues/427)
- Distribute MQTT topic space across different Kafka topics [\#426](https://github.com/aklivity/zilla/issues/426)
- `AsyncAPI` integration \(baseline\) [\#257](https://github.com/aklivity/zilla/issues/257)
- `OpenAPI` integration \(baseline\) [\#255](https://github.com/aklivity/zilla/issues/255)
- `mqtt-kafka` feature \(baseline\) [\#190](https://github.com/aklivity/zilla/issues/190)
- `telemetry metrics` feature \(baseline\) [\#188](https://github.com/aklivity/zilla/issues/188)
- `grpc-kafka` feature \(baseline\) [\#183](https://github.com/aklivity/zilla/issues/183)

**Fixed bugs:**

- Etag header field name MUST be converted to lowercase prior to their encoding in HTTP/2 [\#551](https://github.com/aklivity/zilla/issues/551)
- BudgetDebitor fails to claim budget after sometime [\#548](https://github.com/aklivity/zilla/issues/548)
- Unexpected flush causes NPE in connection pool [\#546](https://github.com/aklivity/zilla/issues/546)
- \[Consumer Group\] Race  condition while joining simultaneously to the same group id [\#542](https://github.com/aklivity/zilla/issues/542)
- MQTT client connections cause errors/crashes [\#527](https://github.com/aklivity/zilla/issues/527)
- Sporadic github action build failures [\#526](https://github.com/aklivity/zilla/issues/526)
- Unable to write to streams buffer under bidi-stream [\#368](https://github.com/aklivity/zilla/issues/368)

**Closed issues:**

- Feature: Adding contributors section to the README.md file. [\#545](https://github.com/aklivity/zilla/issues/545)
- gRPC method call doesn't respond when status code is not OK [\#504](https://github.com/aklivity/zilla/issues/504)

## [0.9.55](https://github.com/aklivity/zilla/tree/0.9.55) (2023-10-11)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.54...0.9.55)

**Implemented enhancements:**

- Use 1-1 helm chart versioning strategy [\#487](https://github.com/aklivity/zilla/issues/487)
- Generate `mqtt` server publish `validators` from `AsyncAPI` specification [\#461](https://github.com/aklivity/zilla/issues/461)
- Integrate `mqtt` binding with `validators` [\#456](https://github.com/aklivity/zilla/issues/456)
- Implement `json` validator [\#454](https://github.com/aklivity/zilla/issues/454)
- Support `inline` catalog for validators [\#453](https://github.com/aklivity/zilla/issues/453)
- Enforce inbound type checking; `kafka cache produce client` [\#312](https://github.com/aklivity/zilla/issues/312)

**Fixed bugs:**

- 0 for no mqtt session expiry should be mapped to max value for the group stream [\#501](https://github.com/aklivity/zilla/issues/501)
- Group stream with same group id may get hang [\#500](https://github.com/aklivity/zilla/issues/500)
- Not cleaning up group stream on group leave response. [\#491](https://github.com/aklivity/zilla/issues/491)
- Connection pool flowcontrol can trigger exception [\#482](https://github.com/aklivity/zilla/issues/482)
- `grpc` server binding sends incorrect `DATA` `flags` for fragmented messages [\#397](https://github.com/aklivity/zilla/issues/397)

## [0.9.54](https://github.com/aklivity/zilla/tree/0.9.54) (2023-09-26)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.53...0.9.54)

**Fixed bugs:**

- Additional scenarios in connection pool cleanup can trigger exception [\#475](https://github.com/aklivity/zilla/issues/475)

## [0.9.53](https://github.com/aklivity/zilla/tree/0.9.53) (2023-09-24)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.52...0.9.53)

**Fixed bugs:**

- Connection cleanup can trigger exception  [\#468](https://github.com/aklivity/zilla/issues/468)

## [0.9.52](https://github.com/aklivity/zilla/tree/0.9.52) (2023-09-23)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.51...0.9.52)

**Implemented enhancements:**

- Connection pool for `kafka` binding `heartbeat` requests [\#462](https://github.com/aklivity/zilla/issues/462)
- Enhance `mqtt` binding configuration syntax [\#420](https://github.com/aklivity/zilla/issues/420)
- Mqtt-Kafka session implementation [\#319](https://github.com/aklivity/zilla/issues/319)
- Design `zilla.yaml` configuration syntax for schema types [\#310](https://github.com/aklivity/zilla/issues/310)
- Generate `zilla.yaml` from `AsyncAPI` specification [\#256](https://github.com/aklivity/zilla/issues/256)
- Generate `zilla.yaml` from `OpenAPI` specification\(s\) [\#254](https://github.com/aklivity/zilla/issues/254)
- Support `kafka` consumer groups [\#215](https://github.com/aklivity/zilla/issues/215)

**Fixed bugs:**

- Zilla crash during attempted WebSocket connection [\#391](https://github.com/aklivity/zilla/issues/391)
- Index out of bounds exception with HTTP-Kafka proxy [\#293](https://github.com/aklivity/zilla/issues/293)

**Closed issues:**

- Send will message as data frame + reject large packets [\#364](https://github.com/aklivity/zilla/issues/364)
- Support Kafka client request-response with MQTT clients [\#326](https://github.com/aklivity/zilla/issues/326)
- Add guard support for MQTT binding [\#308](https://github.com/aklivity/zilla/issues/308)
- Implement retained feature for mqtt-kafka [\#289](https://github.com/aklivity/zilla/issues/289)

## [0.9.51](https://github.com/aklivity/zilla/tree/0.9.51) (2023-07-27)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.50...0.9.51)

**Implemented enhancements:**

- Enhance `tcp` binding to route by `port` [\#294](https://github.com/aklivity/zilla/issues/294)
- Integrate OpenTelemetry collectors by exporting local metrics over OTLP [\#112](https://github.com/aklivity/zilla/issues/112)

**Closed issues:**

- Add redirect, server reference support to mqtt binding [\#302](https://github.com/aklivity/zilla/issues/302)
- Add options to mqtt-kafa binding so we can change kafka topics [\#300](https://github.com/aklivity/zilla/issues/300)

## [0.9.50](https://github.com/aklivity/zilla/tree/0.9.50) (2023-07-14)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.49...0.9.50)

**Implemented enhancements:**

- `kubernetes autoscaling` feature \(baseline\) [\#189](https://github.com/aklivity/zilla/issues/189)

**Closed issues:**

- Update image base [\#291](https://github.com/aklivity/zilla/issues/291)

## [0.9.49](https://github.com/aklivity/zilla/tree/0.9.49) (2023-06-28)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.48...0.9.49)

**Implemented enhancements:**

- Kafka merged flush should support filter changes [\#259](https://github.com/aklivity/zilla/issues/259)

**Closed issues:**

- Null pointer when Headers are null [\#281](https://github.com/aklivity/zilla/issues/281)

## [0.9.48](https://github.com/aklivity/zilla/tree/0.9.48) (2023-06-23)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.47...0.9.48)

**Implemented enhancements:**

- Support additional grpc specific metrics [\#233](https://github.com/aklivity/zilla/issues/233)

**Fixed bugs:**

- CacheMergedIT.shouldFetchMergedMessagesWithIsolationReadCommitted fails only on GitHub Actions [\#239](https://github.com/aklivity/zilla/issues/239)
- CacheFetchIT.shouldReceiveMessagesWithIsolationReadUncommittedWhenAborting fails only on GitHub Actions [\#236](https://github.com/aklivity/zilla/issues/236)

## [0.9.47](https://github.com/aklivity/zilla/tree/0.9.47) (2023-05-14)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.46...0.9.47)

## [0.9.46](https://github.com/aklivity/zilla/tree/0.9.46) (2023-05-14)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.45...0.9.46)

## [0.9.45](https://github.com/aklivity/zilla/tree/0.9.45) (2023-05-14)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.44...0.9.45)

**Implemented enhancements:**

- Generic helm chart [\#242](https://github.com/aklivity/zilla/issues/242)

## [0.9.44](https://github.com/aklivity/zilla/tree/0.9.44) (2023-05-13)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.43...0.9.44)

**Implemented enhancements:**

- Simplify `zilla.yaml` errors on invalid input [\#222](https://github.com/aklivity/zilla/issues/222)
- MQTT and MQTT-Kafka bindings [\#196](https://github.com/aklivity/zilla/issues/196)
- Support additional http specific metrics [\#111](https://github.com/aklivity/zilla/issues/111)

**Closed issues:**

- Refactor existing MQTT specs [\#179](https://github.com/aklivity/zilla/issues/179)

## [0.9.43](https://github.com/aklivity/zilla/tree/0.9.43) (2023-05-10)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.42...0.9.43)

**Implemented enhancements:**

- Enhance `grpc` related binding configuration [\#226](https://github.com/aklivity/zilla/issues/226)
- Move `kafka-grpc` options for `grpc` to `with` section of config [\#218](https://github.com/aklivity/zilla/issues/218)
- Remove `zilla load` now that we have `zilla metrics` instead [\#214](https://github.com/aklivity/zilla/issues/214)
- Minimize on-stack performance overhead of metrics recording [\#213](https://github.com/aklivity/zilla/issues/213)
- Support `eager` evaluation of all `kafka` filters and indicate which filters matched [\#209](https://github.com/aklivity/zilla/issues/209)
- Integrate Prometheus by exporting local metrics over HTTP [\#193](https://github.com/aklivity/zilla/issues/193)
- Support gRPC Kafka server streaming [\#186](https://github.com/aklivity/zilla/issues/186)
- Support Kafka gRPC mapping [\#185](https://github.com/aklivity/zilla/issues/185)
- Support gRPC Kafka mapping [\#184](https://github.com/aklivity/zilla/issues/184)
- Enhance kafka binding to notify transition from historical to live messages [\#172](https://github.com/aklivity/zilla/issues/172)
- Support gRPC proxying [\#171](https://github.com/aklivity/zilla/issues/171)
- Convert zilla spec config .json files to .yaml extension and syntax [\#164](https://github.com/aklivity/zilla/issues/164)
- When starting up in verbose mode make sure there is a newline after printing the config [\#157](https://github.com/aklivity/zilla/issues/157)
- Support dynamic `zilla` configuration via `https` [\#139](https://github.com/aklivity/zilla/issues/139)
- Watch `zilla` configuration for changes and apply automatically [\#138](https://github.com/aklivity/zilla/issues/138)
- Enhance idempotency support for HTTP-Kafka binding [\#113](https://github.com/aklivity/zilla/issues/113)
- Provide zilla metrics command to report current values locally [\#110](https://github.com/aklivity/zilla/issues/110)
- Migrate implicit stream open, close, error, bytes metrics to explicit configuration syntax [\#109](https://github.com/aklivity/zilla/issues/109)
- Design observable metrics configuration syntax [\#100](https://github.com/aklivity/zilla/issues/100)
- Enhance http client binding to support h2 protocol [\#99](https://github.com/aklivity/zilla/issues/99)
- Simplify configuration for JWT identity provider signing keys [\#68](https://github.com/aklivity/zilla/issues/68)

**Fixed bugs:**

- During reconfigure, we don't log errors [\#177](https://github.com/aklivity/zilla/issues/177)
- Flyweight wrapping error race condition [\#146](https://github.com/aklivity/zilla/issues/146)
- CacheMergedIT.shouldFetchMergedMessageValues fails only on GitHub Actions [\#131](https://github.com/aklivity/zilla/issues/131)

**Closed issues:**

- Migrate `zilla` README from `zilla.json` to `zilla.yaml` [\#159](https://github.com/aklivity/zilla/issues/159)

**Merged pull requests:**

- Dynamic config [\#141](https://github.com/aklivity/zilla/pull/141) ([bmaidics](https://github.com/bmaidics))
- Add schema for specifying an OpenID provider discovery endpoint [\#106](https://github.com/aklivity/zilla/pull/106) ([Alfusainey](https://github.com/Alfusainey))

## [0.9.42](https://github.com/aklivity/zilla/tree/0.9.42) (2023-01-29)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.41...0.9.42)

**Implemented enhancements:**

- Support YAML syntax for Zilla configuration [\#144](https://github.com/aklivity/zilla/issues/144)

## [0.9.41](https://github.com/aklivity/zilla/tree/0.9.41) (2023-01-27)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.40...0.9.41)

## [0.9.40](https://github.com/aklivity/zilla/tree/0.9.40) (2023-01-25)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.39...0.9.40)

**Implemented enhancements:**

- Support `{{ mustache }}` syntax in zilla.json [\#91](https://github.com/aklivity/zilla/issues/91)

## [0.9.39](https://github.com/aklivity/zilla/tree/0.9.39) (2023-01-23)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.38...0.9.39)

## [0.9.38](https://github.com/aklivity/zilla/tree/0.9.38) (2023-01-23)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.37...0.9.38)

## [0.9.37](https://github.com/aklivity/zilla/tree/0.9.37) (2023-01-23)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.35...0.9.37)

**Implemented enhancements:**

- Follow standard layout for zilla files in docker image [\#140](https://github.com/aklivity/zilla/issues/140)

**Fixed bugs:**

- NPE when reloading browser page, mid produce [\#151](https://github.com/aklivity/zilla/issues/151)

## [0.9.35](https://github.com/aklivity/zilla/tree/0.9.35) (2023-01-19)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.34...0.9.35)

**Fixed bugs:**

- kafka cache treats non-compacted topics as compacted [\#147](https://github.com/aklivity/zilla/issues/147)

## [0.9.34](https://github.com/aklivity/zilla/tree/0.9.34) (2023-01-16)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.33...0.9.34)

## [0.9.33](https://github.com/aklivity/zilla/tree/0.9.33) (2022-12-14)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.32...0.9.33)

## [0.9.32](https://github.com/aklivity/zilla/tree/0.9.32) (2022-11-28)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.31...0.9.32)

**Implemented enhancements:**

- Implement `zilla dump` command similar to `tcpdump` [\#114](https://github.com/aklivity/zilla/issues/114)

## [0.9.31](https://github.com/aklivity/zilla/tree/0.9.31) (2022-11-17)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.30...0.9.31)

**Implemented enhancements:**

- Remove merged from kafka binding configuration [\#108](https://github.com/aklivity/zilla/issues/108)
- Simplify duplicate request detection at event-driven microservices [\#71](https://github.com/aklivity/zilla/issues/71)

**Fixed bugs:**

- Error running http.kafka.oneway from zilla-examples [\#117](https://github.com/aklivity/zilla/issues/117)
- Zilla build fails on timeout [\#102](https://github.com/aklivity/zilla/issues/102)

**Merged pull requests:**

- Remove merged from kafka binding configuration [\#122](https://github.com/aklivity/zilla/pull/122) ([ankitk-me](https://github.com/ankitk-me))
- Replace HttpRequest with buffer slot [\#105](https://github.com/aklivity/zilla/pull/105) ([akrambek](https://github.com/akrambek))
- Increase frame count for byteman to find matching call stack method [\#104](https://github.com/aklivity/zilla/pull/104) ([jfallows](https://github.com/jfallows))
- Document how to run the benchmark with Java 16+ [\#103](https://github.com/aklivity/zilla/pull/103) ([antonmry](https://github.com/antonmry))
- BugFix: process configURL if the protocol is http [\#101](https://github.com/aklivity/zilla/pull/101) ([Alfusainey](https://github.com/Alfusainey))

## [0.9.30](https://github.com/aklivity/zilla/tree/0.9.30) (2022-09-19)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.29...0.9.30)

**Fixed bugs:**

- Handle produce error [\#97](https://github.com/aklivity/zilla/pull/97) ([akrambek](https://github.com/akrambek))

**Merged pull requests:**

- Support reset extension in test engine [\#96](https://github.com/aklivity/zilla/pull/96) ([akrambek](https://github.com/akrambek))

## [0.9.29](https://github.com/aklivity/zilla/tree/0.9.29) (2022-08-29)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.28...0.9.29)

**Implemented enhancements:**

- Configurable isolation level for kafka binding [\#85](https://github.com/aklivity/zilla/issues/85)
- Configurable acknowledgement mode for kafka binding [\#84](https://github.com/aklivity/zilla/issues/84)

**Merged pull requests:**

- Support SSE proxy [\#98](https://github.com/aklivity/zilla/pull/98) ([jfallows](https://github.com/jfallows))
- Enhance kafka transaction scenarios… [\#95](https://github.com/aklivity/zilla/pull/95) ([jfallows](https://github.com/jfallows))
- Support read committed and read uncommitted kafka isolation levels [\#94](https://github.com/aklivity/zilla/pull/94) ([jfallows](https://github.com/jfallows))
- Support configuration of produce acks [\#93](https://github.com/aklivity/zilla/pull/93) ([jfallows](https://github.com/jfallows))
- Add tests for distinct partition leader [\#92](https://github.com/aklivity/zilla/pull/92) ([akrambek](https://github.com/akrambek))

## [0.9.28](https://github.com/aklivity/zilla/tree/0.9.28) (2022-07-12)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.27...0.9.28)

## [0.9.27](https://github.com/aklivity/zilla/tree/0.9.27) (2022-07-09)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.26...0.9.27)

**Fixed bugs:**

- Investigate GitHub Actions build inconsistencies [\#23](https://github.com/aklivity/zilla/issues/23)

## [0.9.26](https://github.com/aklivity/zilla/tree/0.9.26) (2022-06-11)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.25...0.9.26)

## [0.9.25](https://github.com/aklivity/zilla/tree/0.9.25) (2022-06-10)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.24...0.9.25)

## [0.9.24](https://github.com/aklivity/zilla/tree/0.9.24) (2022-06-08)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.23...0.9.24)

**Fixed bugs:**

- Extract credentials from HTTP path query string even when non-terminal parameter [\#73](https://github.com/aklivity/zilla/issues/73)
- Malformed if-match value triggers exception [\#38](https://github.com/aklivity/zilla/issues/38)

**Merged pull requests:**

- Skip adding if-match header to kafka message if etag not present [\#67](https://github.com/aklivity/zilla/pull/67) ([jfallows](https://github.com/jfallows))

## [0.9.23](https://github.com/aklivity/zilla/tree/0.9.23) (2022-05-27)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.22...0.9.23)

**Merged pull requests:**

- Support trustcacerts without requiring a vault [\#66](https://github.com/aklivity/zilla/pull/66) ([jfallows](https://github.com/jfallows))
- Check extension type id is proxy metadata in tls client and tcp client [\#64](https://github.com/aklivity/zilla/pull/64) ([jfallows](https://github.com/jfallows))

## [0.9.22](https://github.com/aklivity/zilla/tree/0.9.22) (2022-05-27)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.21...0.9.22)

**Fixed bugs:**

- Allow tls trustcacerts option to work without vault [\#65](https://github.com/aklivity/zilla/issues/65)
- Ws to tls proxy misinterprets begin extension  [\#63](https://github.com/aklivity/zilla/issues/63)

## [0.9.21](https://github.com/aklivity/zilla/tree/0.9.21) (2022-05-25)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.20...0.9.21)

**Implemented enhancements:**

- Refer to sse-kafka event id progress as etag instead [\#43](https://github.com/aklivity/zilla/issues/43)
- Require exit be omitted from tcp client configuration [\#40](https://github.com/aklivity/zilla/issues/40)

## [0.9.20](https://github.com/aklivity/zilla/tree/0.9.20) (2022-05-24)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.19...0.9.20)

## [0.9.19](https://github.com/aklivity/zilla/tree/0.9.19) (2022-05-23)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.18...0.9.19)

## [0.9.18](https://github.com/aklivity/zilla/tree/0.9.18) (2022-05-23)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.17...0.9.18)

**Implemented enhancements:**

- Optimize transfer-encoding for http-kafka correlated response [\#45](https://github.com/aklivity/zilla/issues/45)
- Allow list of merged topics in kafka binding options to be optional [\#41](https://github.com/aklivity/zilla/issues/41)

## [0.9.17](https://github.com/aklivity/zilla/tree/0.9.17) (2022-05-13)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.16...0.9.17)

## [0.9.16](https://github.com/aklivity/zilla/tree/0.9.16) (2022-05-12)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.15...0.9.16)

## [0.9.15](https://github.com/aklivity/zilla/tree/0.9.15) (2022-05-12)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.14...0.9.15)

**Implemented enhancements:**

- Enhance http-kafka idempotency key [\#28](https://github.com/aklivity/zilla/issues/28)
- Support key in event id field for sse-kafka binding [\#27](https://github.com/aklivity/zilla/issues/27)
- Support etag in event id field for sse-kafka binding [\#26](https://github.com/aklivity/zilla/issues/26)
- Support tombstone messages via sse-kafka binding [\#25](https://github.com/aklivity/zilla/issues/25)

## [0.9.14](https://github.com/aklivity/zilla/tree/0.9.14) (2022-03-26)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.13...0.9.14)

## [0.9.13](https://github.com/aklivity/zilla/tree/0.9.13) (2022-03-25)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.12...0.9.13)

## [0.9.12](https://github.com/aklivity/zilla/tree/0.9.12) (2022-03-25)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.11...0.9.12)

## [0.9.11](https://github.com/aklivity/zilla/tree/0.9.11) (2022-03-24)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.10...0.9.11)

## [0.9.10](https://github.com/aklivity/zilla/tree/0.9.10) (2022-03-24)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.9...0.9.10)

## [0.9.9](https://github.com/aklivity/zilla/tree/0.9.9) (2022-03-24)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.8...0.9.9)

## [0.9.8](https://github.com/aklivity/zilla/tree/0.9.8) (2022-01-31)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.7...0.9.8)

## [0.9.7](https://github.com/aklivity/zilla/tree/0.9.7) (2022-01-20)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.6...0.9.7)

## [0.9.6](https://github.com/aklivity/zilla/tree/0.9.6) (2022-01-15)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.5...0.9.6)

## [0.9.5](https://github.com/aklivity/zilla/tree/0.9.5) (2022-01-14)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.4...0.9.5)

## [0.9.4](https://github.com/aklivity/zilla/tree/0.9.4) (2022-01-13)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.3...0.9.4)

## [0.9.3](https://github.com/aklivity/zilla/tree/0.9.3) (2022-01-13)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.2...0.9.3)

## [0.9.2](https://github.com/aklivity/zilla/tree/0.9.2) (2022-01-13)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.1...0.9.2)

## [0.9.1](https://github.com/aklivity/zilla/tree/0.9.1) (2022-01-12)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.0...0.9.1)

## [0.9.0](https://github.com/aklivity/zilla/tree/0.9.0) (2022-01-12)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.8.2...0.9.0)

## [0.8.2](https://github.com/aklivity/zilla/tree/0.8.2) (2022-01-10)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.8.1...0.8.2)

## [0.8.1](https://github.com/aklivity/zilla/tree/0.8.1) (2022-01-10)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.8.0...0.8.1)

## [0.8.0](https://github.com/aklivity/zilla/tree/0.8.0) (2022-01-07)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.7.0...0.8.0)

## [0.7.0](https://github.com/aklivity/zilla/tree/0.7.0) (2022-01-02)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.6.0...0.7.0)

## [0.6.0](https://github.com/aklivity/zilla/tree/0.6.0) (2022-01-01)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.5.2...0.6.0)

## [0.5.2](https://github.com/aklivity/zilla/tree/0.5.2) (2021-12-30)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.5.1...0.5.2)

## [0.5.1](https://github.com/aklivity/zilla/tree/0.5.1) (2021-12-29)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.5.0...0.5.1)

## [0.5.0](https://github.com/aklivity/zilla/tree/0.5.0) (2021-12-29)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.4.0...0.5.0)

**Fixed bugs:**

- Mockito test failure only on GitHub Actions [\#3](https://github.com/aklivity/zilla/issues/3)

## [0.4.0](https://github.com/aklivity/zilla/tree/0.4.0) (2021-12-17)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.3.0...0.4.0)

## [0.3.0](https://github.com/aklivity/zilla/tree/0.3.0) (2021-12-17)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.2.0...0.3.0)

## [0.2.0](https://github.com/aklivity/zilla/tree/0.2.0) (2021-12-17)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.1.0...0.2.0)

## [0.1.0](https://github.com/aklivity/zilla/tree/0.1.0) (2021-12-16)

[Full Changelog](https://github.com/aklivity/zilla/compare/21d40009e35a4d777ac8e198febc843cb049320c...0.1.0)



\* *This Changelog was automatically generated by [github_changelog_generator](https://github.com/github-changelog-generator/github-changelog-generator)*
