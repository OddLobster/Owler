# Getting Started

ou need to have at least Java 11 and have properly configured `JAVA_HOME` to point to your Java installation directory. 
For example on MacOS if you are using sdkman you can define in your `~/.bash_profile` file:

```{code} bash 
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-11.0.17.jdk/Contents/Home 
```

## Start OWler

Start OWler with:

```{code} bash 
storm jar warc-crawler.jar org.apache.storm.flux.Flux topology/warc-elastic-crawler/owler.flux
```

