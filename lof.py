from sklearn.neighbors import LocalOutlierFactor
import numpy as np
import h5py
from py4j.java_gateway import JavaGateway, CallbackServerParameters, launch_gateway, GatewayParameters
from py4j.java_collections import JavaArray

def get_bert_embeddings(embedding_file=None):
    with h5py.File(embedding_file, 'r') as hdf5_file:
        embeddings = []
        for key in hdf5_file.keys():
            embeddings.append(np.array(hdf5_file[key]))
        embeddings = np.array(embeddings).squeeze(1)
    return embeddings


class LOF:
    def __init__(self) -> None:
        self.lof_model = LocalOutlierFactor(n_neighbors=4, novelty=True)
        self.corpus_embedding = get_bert_embeddings("../OwlerUtil/Embedding/data/corpus_embedding_d_0_rp.hdf5")
        self.lof_model.fit(self.corpus_embedding)
        print("Finished init LOF")

    def predict(self, document_embedding):
        document_embedding = np.frombuffer(document_embedding, dtype=np.float64)
        #prediction = self.lof_model.predict([document_embedding])[0]
        prediction = "-1"
        return str(prediction)
    
    class Java:
        implements = ["eu.ows.owler.bolt"]


def main():
    print("Starting gateway")
    port = launch_gateway()
    gateway = JavaGateway(
        gateway_parameters=GatewayParameters(port=port),
        callback_server_parameters=CallbackServerParameters(),
        python_server_entry_point=LOF())

if __name__ == "__main__":
    main()