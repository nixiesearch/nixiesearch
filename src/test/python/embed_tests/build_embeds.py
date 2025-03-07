import argparse
import csv
from sentence_transformers import SentenceTransformer

parser = argparse.ArgumentParser()
parser.add_argument("--input", required=True, type=str)
parser.add_argument("--output", required=True, type=str)

args = parser.parse_args()


with open(args.input, "r") as source:
    with open(args.output, "w") as dest:
        reader = csv.DictReader(source)
        writer = csv.writer(dest)
        for row in reader:
            print(f"Loading model {row['model']}")
            model = SentenceTransformer(row["model"], trust_remote_code=True)
            out = [row["model"], row["text"]]
            embedding = model.encode([row["text"]], normalize_embeddings=True)
            print(embedding.shape)
            out.append(" ".join([str(x) for x in embedding[0].tolist()]))
            writer.writerow(out)

print("done")
