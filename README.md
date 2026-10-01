# TunnelVision

TunnelVision tells you whether an IP address is a VPN, Tor exit, proxy, datacenter or known-abusive network. It uses
only free, public data and tries hard **not** too flag regular users.

It can be used mainly in 2 ways:

- **Embedded**: add `tunnelvision-core` to any JVM application (a proxy, a game server, a web app) and call
  `check()` in-process. Its only runtime dependency is the SLF4J API, so it logs through whatever your application uses.
- **As a service**: build the image and run `tunnelvision-server`, then call its HTTP API
  from any language.

```http
GET /v1/check/23.27.54.9
```

```json
{
  "ip": "23.27.54.9",
  "risk": 0.75,
  "blocked": true,
  "status": "CHECKED",
  "categories": ["VPN", "HOSTING"],
  "provider": null,
  "detections": [
    { "source": "x4b-vpn", "name": "X4BNet VPN list", "category": "VPN", "confidence": "LIKELY", "match": "23.27.54.0/24" },
    { "source": "x4b-datacenter", "name": "X4BNet datacenter list", "category": "HOSTING", "confidence": "LIKELY", "match": "23.27.54.0/23" }
  ]
}
```

## Modules

| Module                         | What it is                                                                                  |
|--------------------------------|---------------------------------------------------------------------------------------------|
| `tunnelvision-core`            | The detection engine, sources, and the `VerdictCache` interface. Only depends on SLF4J      |
| `tunnelvision-cache-caffeine`  | An in-process cache, for a single instance or embedded use                                  |
| `tunnelvision-cache-redis`     | A shared cache over Redis, Valkey, Dragonfly or anything else that speaks the RESP protocol |
| `tunnelvision-cache-hazelcast` | A shared cache over Hazelcast, either an embedded member or a client                        |
| `tunnelvision-server`          | A Spring Boot HTTP API around all of the above                                              |

## Embedding

```java
TunnelVision tunnelVision = TunnelVision.builder()
    .snapshotStore(new FileSnapshotStore(Path.of("data/sources")))
    .cache(new CaffeineVerdictCache(Duration.ofMinutes(10), 100_000))
    .addSource(new StaticSource("denylist", "My denylist", Category.ABUSE, Confidence.CONFIRMED, List.of("198.51.100.0/24")))
    .allowlist(List.of("203.0.113.7"))
    .build();
tunnelVision.start(); // Loads snapshots, then refreshes sources in the background

// e.g. in a proxy's login handler
if (tunnelVision.check(player.getAddress().getAddress()).blocked()) {
    player.disconnect("VPNs aren't allowed");
}
```

Lookups run in memory. They are a binary search over merged rangse, so they take well under a microsecond and are safe to
call from any thread, including an event loop. Private and reserved addresses come back clean with `status: RESERVED`
and never throw, so LAN connections are fine.

To add a source, implement `DetectionSource`, or use `PlainListSource` for one-entry-per-line lists. To add a cache, implement
`VerdictCache`. `TieredVerdictCache` puts a local cache in front of a shared one.

## How detection works

| Source           | Category | Confidence | Refresh | Data                                                                    |
|------------------|----------|------------|---------|-------------------------------------------------------------------------|
| `tor`            | Tor      | Confirmed  | 1h      | [Tor bulk exit list](https://check.torproject.org/torbulkexitlist)      |
| `x4b-vpn`        | VPN      | Likely     | 24h     | [X4BNet lists_vpn](https://github.com/X4BNet/lists_vpn) VPN list        |
| `x4b-datacenter` | Hosting  | Likely     | 24h     | [X4BNet lists_vpn](https://github.com/X4BNet/lists_vpn) datacenter list |

You can add custom sources from config (URLs or inline entries) or from code.

### Keeping the data trustworthy

Bad upstream data is the main cause of false positives, so every refresh is checked before it's used:

- Entries that don't parse are skipped.
- Entries that are too broad (wider than `/8` for IPv4 or `/24` for IPv6) or that overlap private/reserved space are
  dropped. If more than 1% of a refresh is like that, the whole refresh is rejected.
- A refresh with fewer than half the entries of the previous one is rejected, because that is almost always an error page or a
  truncated download.
- A rejected or failed refresh keeps the last good data, and is retried after 5 minutes. The last good copy of each
  source is saved to disk, so a restart doesn't depend on upstream being reachable.
- A source whose data is older than 3 refresh intervals (and at least 24 hours) is **stale**, and lookups ignore it.
- Whenever a source's data changes, the verdict cache is cleared.

### The verdict

- `categories` lists every kind of match: `VPN`, `TOR`, `PROXY`, `RELAY`, `HOSTING` or `ABUSE`. Only VPN sources add
  `VPN`. A hosting match alone is **not** a VPN: plenty of legitimate traffic (corporate proxies, CI runners, crawlers)
  comes from datacenters.
- `status` is `CHECKED`, or `ALLOWLISTED` or `RESERVED` for addresses that are skipped and always clean.
- A **relay** match (consumer privacy relays such as iCloud Private Relay) suppresses hosting and likely-VPN matches,
  since relays exit from CDN networks that are also on hosting lists.
- `risk` (0 to 1) is the weight of the strongest signal. Signals don't stack.
- `blocked` is true when a matched category is in `policy.block-categories` (VPN, Tor and proxy by default) or the risk
  reaches `policy.risk-threshold`.

| Signal           | Default weight |
|------------------|----------------|
| VPN (confirmed)  | 1.0            |
| VPN (likely)     | 0.75           |
| Tor              | 1.0            |
| Proxy            | 0.9            |
| Abuse            | 0.9            |
| Hosting          | 0.5            |
| Relay            | 0.1            |

## HTTP API

| Method | Route            | Description                                  |
|--------|------------------|----------------------------------------------|
| `GET`  | `/v1/check/{ip}` | Check an address                             |
| `POST` | `/v1/check`      | Check up to 1000 addresses, `{"ips": [...]}` |
| `GET`  | `/v1/sources`    | The state of every source                    |
| `GET`  | `/openapi.yaml`  | The OpenAPI spec                             |

The API is defined in [`openapi.yaml`](tunnelvision-server/src/main/resources/static/openapi.yaml), and the server's
interfaces and models are generated from it at build time. To change the API, edit the spec first. You can generate a
client for any language from the same file, e.g. with [OpenAPI Generator](https://openapi-generator.tech).

Errors use [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) problem details.

Health checks and metrics are served on a separate port (`8081`) so they stay off the public port:
`/actuator/health/liveness`, `/actuator/health/readiness` (ready once every source has loaded or tried to), and
`/actuator/prometheus`.

## Running it

### Docker

Build the image from a clone of this repo:

```bash
docker build -t tunnelvision .
docker run -p 127.0.0.1:8080:8080 -p 127.0.0.1:8081:8081 -v tunnelvision-data:/app/data tunnelvision
curl localhost:8080/v1/check/1.1.1.1
```

Or run it with a Valkey cache using `docker compose up -d --build`.

Set the cache with `TUNNELVISION_CACHE_TYPE`:

| Type        | Use when                                                                                                                                                                                                                                                                                                      |
|-------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `caffeine`  | The default. Each instance keeps its own cache, no extra infrastructure                                                                                                                                                                                                                                       |
| `redis`     | You already run Redis or Valkey. Set `TUNNELVISION_CACHE_REDIS_URI`                                                                                                                                                                                                                                           |
| `hazelcast` | Instances share a cache by clustering together, no extra infrastructure. How they find each other (Kubernetes, a fixed list, ...) is set in Hazelcast's own [`hazelcast.yaml`](tunnelvision-server/src/main/resources/hazelcast.yaml): copy it to the working directory (`/app` in the container) and edit it |

## Configuration

All settings live in [`application.yaml`](tunnelvision-server/src/main/resources/application.yaml), and each one has a
comment there. You can override any of them with an environment variable (`TUNNELVISION_CACHE_TYPE=redis`) or with an
`application.yaml` in `./config/` (`/app/config/` in the container).

## Development

Requires Java 25 or newer and Maven 3.9 or newer. Docker is optional.

```bash
mvn verify                                      # Build and run every test
java -jar tunnelvision-server/target/tunnelvision-server.jar
```

## Attribution

Detection data comes from [The Tor Project](https://www.torproject.org/) and
[X4BNet lists_vpn](https://github.com/X4BNet/lists_vpn).
