# Description

The route guide server and client demonstrate how to use grpc go libraries to
perform unary, client streaming, server streaming and full duplex RPCs.

Please refer to [gRPC Basics: Go](https://grpc.io/docs/tutorials/basic/go.html) for more information.

See the definition of the route guide service in `routeguide/route_guide.proto`.

## Run the sample code

To compile and run the server, assuming you are in the root of the `route_guide`
folder, i.e., `.../examples/route_guide/`, simply:

```sh
go run server/server.go
```
