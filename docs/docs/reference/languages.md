# Language support

Nixiesearch language support differs for lexical and semantic search methods:
* for lexical search, all Lucene analyzers are supported out of the box - see [full list below](#language-support-for-lexicalhybrid-search)
* for semantic search, language support depends on the underlying embedding model used. See some examples in a [Language support for semantic search](#language-support-for-semantic-search) below.

Language can be set in the index mapping for text-like fields:

```yaml
schema:
  your-index-name:
    fields:
      title:
        type: text
        search: 
          type: lexical
        language: en # use an ISO-639-1 language code
```

> If language is not defined, a special **default** language analyzer is used - no language specific transformations are done, only ICU tokenization.

## Language support for lexical/hybrid search

Nixiesearch supports all languages from [Apache Lucene](https://lucene.apache.org/core/8_5_1/analyzers-common/index.html) library:

| Language            | ISO 639-1 code |
|---------------------|----------------|
| Generic             | default        |
| English             | en             |
| Arabic              | ar             |
| Bulgarian           | br             |
| Bengali             | br             |
| Brazilian Portugese | br             |
| Catalan             | br             |
| Simplified Chinese  | zh             |
| Czech               | cz             |
| Danish              | da             |
| German              | de             |
| Greek               | el             |
| Spanish             | es             |
| Estonian            | et             |
| Basque              | eu             |
| Persian             | fa             |
| Finnish             | fi             |
| French              | fr             |
| Irish               | ga             |
| Hindi               | hi             |
| Hungarian           | hu             |
| Armenian            | hy             |
| Indonesian          | id             |
| Italian             | it             |
| Lithuanian          | lt             |
| Latvian             | lv             |
| Dutch               | nl             |
| Norwegian           | no             |
| Portuguese          | pt             |
| Romanian            | ro             |
| Russian             | ru             |
| Serbian             | sr             |
| Swedish             | sv             |
| Thai                | th             |
| Turkish             | tr             |
| Japanese            | ja             |
| Polish              | po             |
| Korean              | kr             |
| Tamil               | ta             |
| Ukrainian           | ua             |

> If your language is not included in the list, please file a GitHub issie: https://github.com/nixiesearch/nixiesearch/issues

## Language support for semantic search

Language support for semantic search fully depends on the embedding model used:

* [sentence-transformers/all-MiniLM-L6-v2](): English
* [BAAI/bge-large-en-v1.5](https://huggingface.co/BAAI/bge-large-en-v1.5): English, a special version for [Chinese](https://huggingface.co/BAAI/bge-large-zh-v1.5)
* [Alibaba-NLP/gte-base-en-v1.5](https://huggingface.co/Alibaba-NLP/gte-base-en-v1.5) English
* [intfloat/multilingual-e5-large](https://huggingface.co/intfloat/multilingual-e5-large): English, Chinese and all languages from [MIRACL dataset](https://huggingface.co/datasets/miracl/miracl)

So if your target language is English, you can choose almost any model from the [MTEB leaderboard](https://huggingface.co/spaces/mteb/leaderboard) you like. For multilingual model, the [intfloat/multilingual-e5-large](https://huggingface.co/intfloat/multilingual-e5-large) is recommended.