from flask import Flask, request, jsonify
import numpy as np
import h5py
from sklearn.neighbors import LocalOutlierFactor

app = Flask(__name__)

def get_bert_embeddings(embedding_file=None):
    with h5py.File(embedding_file, 'r') as hdf5_file:
        embeddings = []
        for key in hdf5_file.keys():
            embeddings.append(np.array(hdf5_file[key]))
        embeddings = np.array(embeddings).squeeze(1)
    return embeddings

class LOF:
    def __init__(self):
        self.lof_model = LocalOutlierFactor(n_neighbors=8, novelty=True)
        self.corpus_embedding = get_bert_embeddings("data/model/corpus_embedding.hdf5")
        self.lof_model.fit(self.corpus_embedding)
        print("Finished init LOF")

    def predict(self, document_embedding):
        document_embedding = np.array(document_embedding)
        prediction = self.lof_model.predict([document_embedding])[0]
        lof_score = self.lof_model.decision_function([document_embedding])[0]
        return str(prediction), str(lof_score)

lof = LOF()

@app.route('/predict', methods=['POST'])
def predict():
    data = request.get_json()
    print(type(data['embedding']))
    document_embedding = data['embedding']
    prediction, lof_score = lof.predict(document_embedding)
    print(type(prediction), type(lof_score))
    return jsonify({"prediction": prediction, "lof_score": lof_score})

if __name__ == "__main__":
    print("Starting LOF api")
    app.run(host='0.0.0.0', port=43044)
