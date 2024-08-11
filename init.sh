mkdir data
mkdir data/model
mkdir output
mkdir output/documents
mkdir output/warc

mv ../OwlerUtil/SeedGathering/corpus_embedding_d_0.hdf5 data/model
mv ../OwlerUtil/SeedGathering/seeds_earth_observation.txt . 
mv seeds_earth_observation.txt seeds.txt

mv ../OwlerUtil/bert-base-uncased.onnx data/model

