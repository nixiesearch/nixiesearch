## Supported embedding models

Nixiesearch supports any [sentence-transformers](https://sbert.net)-compatible model in the [ONNX](https://onnxruntime.ai/) format.

The following list of models is tested to work well with Nixiesearch: 

* there is an ONNX model provided in the repo (e.g. a `model.onnx` file),
* input tensor shapes are supported, 
* Nixiesearch can correctly guess query and document prompt format (like E5-family of models requiring `query: ` and `passage: ` prefixes),
* embedding pooling method is supported - `CLS` or `mean`.

!!! note

    Nixiesearch can automatically guess the proper prompt format and pooling method for all the models in the supported list table below. You can override this behavior in the model [configuration section](../../../reference/config.md#embedding-models) with `pooling` and `prompt` parameters.

### List of supported models

{{ read_csv('models.csv') }}

If the model is not listed in this table, but has an ONNX file available, then most probably it should work well. But you might set `prompt` and `pooling` parameters based on model documentation. See embedding model [configuration section](../../../reference/config.md#embedding-models) for more details.

## Model handles

Nixiesearch supports loading models directly from Huggingface by its handle (e.g. `sentence-transformers/all-MiniLM-L6-v2`) and from local file directory.

You can reference any HF model handle in the inference block, for example:

```yaml
inference:
  embedding:
    e5-small:
      model: sentence-transformers/all-MiniLM-L6-v2
```
It also works with local paths:

```yaml
inference:
  embedding:
    your-model:
      model: /path/to/model/dir
```

Optionally you can define which particular ONNX file to load, for example the QInt8 quantized one:

```yaml
inference:
  embedding:
    # Used for semantic retrieval
    e5-small:
      model: nixiesearch/e5-small-v2-onnx
      file: model_opt2_QInt8.onnx
```

To enable caching for frequent strings, use the `cache` option. See [Embedding caching](cache.md) for more details.

```yaml
inference:
  embedding:
    # Used for semantic retrieval
    e5-small:
      model: nixiesearch/e5-small-v2-onnx
      cache:
        memory:
          max_size: 1024 # cache top-N most popular embeddings
```


## Converting your own model

You can use the [nixiesearch/onnx-convert](https://github.com/nixiesearch/onnx-convert) to convert your own model:

```bash
python convert.py --model_id intfloat/multilingual-e5-large --optimize 2 --quantize QInt8
```

```
Conversion config: ConversionArguments(model_id='intfloat/multilingual-e5-base', quantize='QInt8', output_parent_dir='./models/', task='sentence-similarity', opset=None, device='cpu', skip_validation=False, per_channel=True, reduce_range=True, optimize=2)
Exporting model to ONNX
Framework not specified. Using pt to export to ONNX.
Using the export variant default. Available variants are:
    - default: The default ONNX variant.
Using framework PyTorch: 2.1.0+cu121
Overriding 1 configuration item(s)
        - use_cache -> False
Post-processing the exported models...
Deduplicating shared (tied) weights...
Validating ONNX model models/intfloat/multilingual-e5-base/model.onnx...
        -[✓] ONNX model output names match reference model (last_hidden_state)
        - Validating ONNX Model output "last_hidden_state":
                -[✓] (2, 16, 768) matches (2, 16, 768)
                -[✓] all values close (atol: 0.0001)
The ONNX export succeeded and the exported model was saved at: models/intfloat/multilingual-e5-base
Export done
Processing model file ./models/intfloat/multilingual-e5-base/model.onnx
ONNX model loaded
Optimizing model with level=2
Optimization done, quantizing to QInt8
```
See the [nixiesearch/onnx-convert](https://github.com/nixiesearch/onnx-convert) repo for more details and options.
