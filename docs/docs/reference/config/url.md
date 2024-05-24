# Supported URL formats 

Nixiesearch [configuration](overview.md), [index mapping](mapping.md) and [command-line options](../cli/index.md) support passing URLs as locations.

For example, [offline pull-based indexing](../cli/index.md#offline-indexing) from a local file has an `--url` parameter:

```shell
docker run -i -t -v <your-local-dir>:/data nixiesearch/nixiesearch:latest \
   index file --config /data/conf.yml --index <index name> \
   --url file:///data/docs.json
```

Nixiesearch supports following URL schemas:

* [Local files](#local-files): `file:///path/to/file`
* [HTTP locations](#http-locations): `http://server.com/file.json`
* [S3-compatible locations](#s3-compatible-locations) like [AWS S3](todo), [Google Cloud Store](todo), [MinIO](https://min.io/) and others: `s3://bucket/prefix/file.json`

## Local files

An URL is treated as a local file, if:
* it starts with a `file://` schema prefix
* it is a relative or absolute path like `/home/user/file.json` of just `file.json`
* According to [RFC 3986, Section 3.2.2](https://datatracker.ietf.org/doc/html/rfc3986) there should be either one (e.g. `file:/path/some.json`) or three slashes (e.g `file:///path/some.json`) in the prefix, but two slashes are also frequently used. Nixiesearch will handle 1, 2, and 3 slashes in the file URL.

## HTTP locations

Both HTTP and HTTPS URL schemes are supported.

## S3-compatible locations

S3-compatible URLs have the following format:

`s3://bucket/prefix/file.json`

To pass non-URL S3 parameters like authentication and region, use ENV variables:

```shell
$ export AWS_ACCESS_KEY_ID=AKIAIOSFODNN7EXAMPLE
$ export AWS_SECRET_ACCESS_KEY=wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY
$ export AWS_DEFAULT_REGION=us-west-2
```

## URLs in a config file

For a [configuration file](overview.md) URL usage, you can also unfold the S3 URL into a YAML object, which has all the internal settings exposed:

```yaml
schema:
  helloworld:
    store:
      distributed:
        remote:
          # path: s3://index-bucket/foo/bar
          s3:
            bucket: index-bucket
            prefix: foo/bar
            region: us-east-1
            endpoint: http://localhost:8443/
```

## URLs in command-line options

Also the [nixiesearch CLI](../cli/index.md) has an `--endpoint` parameter, so you can pass custom endpoint for all S3 URLs passed as cmdline parameters.

## Decompression support

Nixiesearch also detects gz/bz2/zst compressed files (by their extension) and decompresses them on the fly. So you can also compress your source files to save space and bandwidth:

```shell
docker run -i -t -v <your-local-dir>:/data nixiesearch/nixiesearch:latest \
   index file --config /data/conf.yml --index <index name> \
   --url file:///data/docs.json.gz
```