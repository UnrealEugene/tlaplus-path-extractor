An extension to [TLC model checker](https://github.com/tlaplus/tlaplus) of TLA+ to extract executions from state graph in JSON format. Was used to generate executions from TLA+ specification for model guided testing of replication algorithm [Viewstamped Replication](https://pmg.csail.mit.edu/papers/vr-revisited.pdf) used in VK.

## Usage

Compile the source code using Java 11 or higher with dependencies from `lib` folder and latest pre-release version of TLC (it can be found [here](https://github.com/tlaplus/tlaplus/releases/tag/v1.8.0)). After that the extension can be used by adding compiled files to the classpath and adding `-dump class,tlc2.util.JsonStateWriter <folder path>` command line argument to TLC, where `<folder path>` is the folder path in which all JSON files will be generated.

The generated files can be used in _model guided testing_ to test the concrete implementation written in your favorite programming language. 