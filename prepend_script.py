with open('input/warc.paths','r') as f:
    with open('input/warc2.paths','w') as g:
        for line in f.readlines():
            g.write('https://data.commoncrawl.org/' + line)
