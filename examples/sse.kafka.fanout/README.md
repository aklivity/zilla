# sse.kafka.fanout

Streams Kafka records to clients using **Server-Sent Events (SSE)** with **key and header-based filtering**.

Zilla listens on HTTP port `7114` and fans out records from the Kafka `events` topic to SSE clients.

## Use case: live stock ticker portfolio

In this example, Kafka is used to carry live stock price updates. Each record represents the latest price for a stock symbol.

SSE clients subscribe to **only the stocks they care about**, for example the symbols in a user’s portfolio, and receive live updates as prices change.

Filtering is used to:
- select a specific stock symbol
- optionally scope the stream using metadata (such as exchange)

## Requirements

- docker compose

## Setup

To start the Docker Compose stack defined in the [compose.yaml](compose.yaml) file:

```bash
docker compose up -d
```

## Streaming stock prices with SSE

### SSE endpoint structure

Clients subscribe using a URL of the form:

```
/events/{symbol}/{exchange}
```

Where:
- `symbol` is the stock ticker symbol (for example `AAPL`, `MSFT`)
- `exchange` is metadata carried as a Kafka header (for example `nasdaq`)

This allows multiple independent streams to exist at the same time, one per stock, per exchange.

## Verify behavior

### Subscribe to multiple stocks (portfolio)

```bash
curl -N --http2 -H "Accept:text/event-stream" \
  "http://localhost:7114/events/AAPL/nasdaq"
```

```bash
curl -N --http2 -H "Accept:text/event-stream" \
  "http://localhost:7114/events/MSFT/nasdaq"
```

Each stream receives updates independently, allowing a client application to represent a portfolio of stocks.


## Browser UI

Browse to [http://localhost:7114/index.html](http://localhost:7114/index.html) and make sure to visit the `localhost` site and trust the `localhost` certificate.

Click `Go` to attach the browser’s EventSource to `Kafka via Zilla` and display live price updates for `/events/{symbol}/{exchange}`, simulating a portfolio view.

## Reliability

Stop the `zilla` service to simulate a connection loss:

```bash
docker compose stop zilla
```

Restart it to simulate recovery:

```bash
docker compose start zilla
```

Any price updates published while clients were reconnecting are delivered immediately, followed by live updates.

## Teardown

```bash
docker compose down
```
