from sentence_transformers import SentenceTransformer
import numpy as np

model = SentenceTransformer(
    "Snowflake/snowflake-arctic-embed-s", trust_remote_code=True
)

# print(
#     model.encode("hello world", output_value="token_embeddings", convert_to_numpy=False)
# )

print(
    model.encode(
        ["query: hello world"],
        # output_value="sentence_embedding",
        # convert_to_numpy=False,
        normalize_embeddings=True,
    )
)
