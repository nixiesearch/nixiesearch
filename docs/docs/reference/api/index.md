# REST API

## Index operations

### Flush index

Trigger an explicit segment flush and sync. This usually happens periodically, see config reference on [index configuration](../config.md#index-configuration) for controlling the flush frequency.

Route: `POST /<index_name>/_flush`

Payload: none

Response: 

```json
{
  "status": "ok",
  "took": 10
}
```

* `status`: required, string. "ok" when OK.
* `took`: required, int. How many millis server spent processing the request.

Example:

```shell
$ curl -XPOST http://localhost:8080/msmarco/_flush|jq .
  % Total    % Received % Xferd  Average Speed   Time    Time     Time  Current
                                 Dload  Upload   Total   Spent    Left  Speed
100    24  100    24    0     0   3625      0 --:--:-- --:--:-- --:--:--  4000
{
  "status": "ok",
  "tool": 3
}
```

### Force merge index

Trigger explicit forced index merging. Might be useful when you want to reduce number of segments without waiting for the server to do it eventually.

Route: `POST /<index_name>/_merge`

Payload: 

```json
{
  "segments": 1
}
```

* `segments`: required, int. How many segments at max should there be after merging. 

Response:

```json
{
  "status": "ok",
  "took": 10
}
```

* `status`: required, string. "ok" when OK.
* `took`: required, int. How many millis server spent processing the request.

Example:

```shell
$ curl -XPOST -d '{"segments": 1}' http://localhost:8080/msmarco/_merge|jq .
  % Total    % Received % Xferd  Average Speed   Time    Time     Time  Current
                                 Dload  Upload   Total   Spent    Left  Speed
100    24  100    24    0     0   3625      0 --:--:-- --:--:-- --:--:--  4000
{
  "status": "ok",
  "tool": 3
}
```
