# Standalone mode

## Remote config file loading

Nixiesearch also supports loading config files from remote locations. Currently supported:

* S3-compatible block storage. Example: `nixiesearch standalone -c s3://bucket/prefix/conf.yml`
* HTTP/HTTPS hosted files. Example: `nixiesearch standalone -c https://example.com/config.yml`
* Local files. All `--config` option values are treated as local files if there is no URI schema prefix defined. Example: `nixiesearch standalone -c config.yml`. Optionally you can use a `file://` schema: `nixiesearch standalone -c file:///dir/config.yml`.