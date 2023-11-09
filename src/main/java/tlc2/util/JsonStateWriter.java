package tlc2.util;

import com.alibaba.fastjson2.JSONWriter;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.impl.list.primitive.IntInterval;
import tla2sany.st.Location;
import tlc2.TLCGlobals;
import tlc2.diploma.graph.ConcreteAction;
import tlc2.diploma.graph.StateGraphPathExtractor;
import tlc2.output.EC;
import tlc2.output.MP;
import tlc2.tool.Action;
import tlc2.tool.TLCState;
import tlc2.tool.Worker;
import tlc2.tool.impl.Tool;
import tlc2.value.IValue;
import tlc2.value.impl.Value;
import util.UniqueString;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@SuppressWarnings("unused")
public class JsonStateWriter implements IStateWriter {
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");
    private static final String FILE_NAME_FORMAT = "%03d.json";

    private final Path dir;
    private final StateGraphPathExtractor stateGraphPathExtractor;
    private final JSONSparseArrayWriter[] stateWriters;
    private final JSONSparseArrayWriter[] actionWriters;
    private final Map<Location, Integer> locToId;

    private static final String EXPORT_DIR = System.getProperty(JsonStateWriter.class.getName() + ".dir");

    @SuppressWarnings("resource")
    public JsonStateWriter() throws IOException {
        this.dir = Path.of(EXPORT_DIR);
        this.stateGraphPathExtractor = new StateGraphPathExtractor();

        int threads = TLCGlobals.getNumWorkers();

        Files.deleteIfExists(this.dir.resolve("meta.json"));

        Path stateDir = this.dir.resolve("states");
        Path actionDir = this.dir.resolve("actions");
        Files.createDirectories(stateDir);
        Files.createDirectories(actionDir);
        this.stateWriters = new JSONSparseArrayWriter[threads];
        this.actionWriters = new JSONSparseArrayWriter[threads];
        for (int i = 0; i < threads; i++) {
            String fileName = String.format(FILE_NAME_FORMAT, i + 1);
            this.stateWriters[i] = new JSONSparseArrayWriter(stateDir.resolve(fileName));
            this.actionWriters[i] = new JSONSparseArrayWriter(actionDir.resolve(fileName));
        }
        this.locToId = new HashMap<>();
    }

    private int getThreadId() {
        int id = 0;
        Thread thread = Thread.currentThread();
        if (thread instanceof Worker) {
            id = ((Worker) thread).myGetId();
        }
        return id;
    }

    public void writeJsonState(int index, TLCState state) {
        int threadId = getThreadId();
        this.stateWriters[threadId].write(jsonWriter -> {
            jsonWriter.writeName(Integer.toString(index));
            jsonWriter.writeColon();
            jsonWriter.startObject();
            Map<UniqueString, IValue> stateVals = state.getVals();
            for (Map.Entry<UniqueString, IValue> entry : stateVals.entrySet()) {
                jsonWriter.writeName(entry.getKey().toString());
                jsonWriter.writeColon();
                jsonWriter.writeAny(FastJsonSerializer.serialize(entry.getValue()));
            }
            jsonWriter.endObject();
        });
    }

    private void writeJsonAction(int index, TLCState from, TLCState to, Action action) {
        ConcreteAction concreteAction = ConcreteAction.from(from, to, action);
        int threadId = getThreadId();
        this.actionWriters[threadId].write(jsonWriter -> {
            jsonWriter.writeName(Integer.toString(index));
            jsonWriter.writeColon();
            jsonWriter.startArray();
            int actionId = this.locToId.get(concreteAction.getDeclaration());
            jsonWriter.writeInt32(actionId);
            for (IValue val : concreteAction.getArgs()) {
                jsonWriter.writeComma();
                jsonWriter.writeAny(FastJsonSerializer.serialize(val));
            }
            jsonWriter.endArray();
        });
    }

    private void tryInitLocToId() {
        if (!this.locToId.isEmpty()) {
            return;
        }

        Tool tool = (Tool) TLCGlobals.mainChecker.tool;
        List<Location> actionLocations = Arrays.stream(tool.getActions())
                .map(Action::getDeclaration)
                .distinct()
                .collect(Collectors.toList());
        this.locToId.putAll(IntStream.range(0, actionLocations.size())
                .boxed()
                .collect(Collectors.toMap(actionLocations::get, Function.identity())));

        Map<Location, UniqueString> actionNames = Arrays.stream(tool.getActions())
                .collect(Collectors.groupingBy(Action::getDeclaration,
                        Collectors.reducing(null, Action::getName, (x, y) -> x == null ? y : x)));
        Map<Location, Long> actionLocCount = Arrays.stream(tool.getActions())
                .collect(Collectors.groupingBy(Action::getDeclaration, Collectors.counting()));

        MP.printMessage(EC.GENERAL, "Found " + tool.getActions().length + " actions (" + actionLocations.size() + " distinct):");
        for (Location loc : actionLocations) {
            MP.printMessage(EC.GENERAL, "  " + actionNames.get(loc) + ": " + actionLocCount.get(loc));
        }
    }

    @Override
    public void writeState(TLCState state) {
        tryInitLocToId();
        int id = this.stateGraphPathExtractor.addState(state);
        this.writeJsonState(id, state);
    }

    @Override
    public void writeState(TLCState state, TLCState successor, short stateFlags) {
        writeState(state, successor, stateFlags, Visualization.DEFAULT);
    }

    @Override
    public void writeState(TLCState state, TLCState successor, short stateFlags, Action action) {
        writeState(state, successor, 0, 0, stateFlags, Visualization.DEFAULT, action);
    }

    @Override
    public void writeState(TLCState state, TLCState successor, short stateFlags, Visualization visualization) {
        writeState(state, successor, 0, 0, stateFlags, visualization, null);
    }

    @Override
    public void writeState(TLCState state, TLCState successor, BitVector actionChecks, int from, int length, short stateFlags) {
        writeState(state, successor, from, length, stateFlags, Visualization.DEFAULT, null);
    }

    @Override
    public void writeState(TLCState state, TLCState successor, BitVector actionChecks, int from, int length, short stateFlags, Visualization visualization) {
        writeState(state, successor, from, length, stateFlags, visualization, null);
    }

    private void writeState(TLCState state, TLCState successor, int ignoredFrom, int ignoredLength,
                            short stateFlags, Visualization visualization, Action action) {
        if (visualization == Visualization.STUTTERING) {
            return;
        }
        if (!isSet(stateFlags, IsSeen)) {
            int id = this.stateGraphPathExtractor.addState(successor);
            this.writeJsonState(id, successor);
        }
        if (state.fingerPrint() != successor.fingerPrint()) {
            int id = this.stateGraphPathExtractor.addAction(state, successor);
            this.writeJsonAction(id, state, successor, action);
        }
    }

    @Override
    public boolean isNoop() {
        return true;
    }

    @Override
    public boolean isDot() {
        return false;
    }

    @Override
    public boolean isConstrained() {
        return false;
    }

    private String now() {
        return DATE_FORMAT.format(new Date());
    }

    private String formatBytes(long bytes) {
        String[] units = new String[]{"bytes", "KiB", "MiB", "GiB", "TiB"};
        int unitIndex = (int) (Math.log10(bytes) / 3);
        double unitValue = 1L << (unitIndex * 10);
        return new DecimalFormat("#,##0.#")
                .format(bytes / unitValue) + " " + units[unitIndex];
    }

    @Override
    @SuppressWarnings("unchecked")
    public void close() {
        int threads = TLCGlobals.getNumWorkers();
        try {
            for (int i = 0; i < threads; i++) {
                this.stateWriters[i].close();
                this.actionWriters[i].close();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        if (TLCGlobals.mainChecker.getStateQueueSize() != 0) {
            return;
        }

        Tool tool = (Tool) TLCGlobals.mainChecker.tool;
        Iterable<List<StateGraphPathExtractor.Edge>> paths = this.stateGraphPathExtractor.extractPaths();

        MP.printMessage(EC.GENERAL, "Path cover JSON exporting started.");

        Path metaFile = this.dir.resolve("meta.json");
        try {
            Files.deleteIfExists(metaFile);
        } catch (IOException ignored) {
        }

        int pathCount = this.stateGraphPathExtractor.getPathCount();

        // write executions
        Path execDir = this.dir.resolve("executions");
        try {
            Files.createDirectories(execDir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        Iterator<List<StateGraphPathExtractor.Edge>> pathIterator = paths.iterator();
        for (int i = 0, l = 0; i < threads; i++) {
            int div = pathCount / threads, mod = pathCount % threads;
            int r = l + div + (i < mod ? 1 : 0);

            String execFileName = String.format(FILE_NAME_FORMAT, i + 1);
            Path execFile = execDir.resolve(execFileName);

            try (BufferedWriter writer = Files.newBufferedWriter(execFile);
                 JSONWriter jsonWriter = JSONWriter.ofUTF8()) {
                jsonWriter.startArray();
                for (int j = l; j < r; j++) {
                    if (j > l) {
                        jsonWriter.writeComma();
                    }
                    List<StateGraphPathExtractor.Edge> path = pathIterator.next();
                    jsonWriter.startArray();
                    if (!path.isEmpty()) {
                        jsonWriter.writeInt32(path.get(0).getFrom());
                    }
                    for (StateGraphPathExtractor.Edge edge : path) {
                        jsonWriter.writeComma();
                        jsonWriter.writeInt32(edge.getId());
                        jsonWriter.writeComma();
                        jsonWriter.writeInt32(edge.getTo());
                    }
                    jsonWriter.endArray();
                    if ((j - l) % 1024 == 1023) {
                        jsonWriter.flushTo(writer);
                    }
                }
                jsonWriter.endArray();
                jsonWriter.flushTo(writer);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }

            long fileSize = execFile.toFile().length();
            MP.printMessage(EC.GENERAL, "  " + this.dir.relativize(execFile) + " (" + formatBytes(fileSize) + ")");

            l = r;
        }

        Vect<Object> constVect = tool.getModelConfig().getConstants();
        Object[] constArray = new Vect[constVect.size()];
        constVect.copyInto(constArray);
        Map<String, Value> constMap = new HashMap<>();
        for (Object obj : constArray) {
            Vect<Object> vect = (Vect<Object>) obj;
            constMap.put((String) vect.elementAt(0), (Value) vect.elementAt(1));
        }

        // write meta file
        try (BufferedWriter writer = Files.newBufferedWriter(metaFile);
             JSONWriter jsonWriter = JSONWriter.ofUTF8()) {
            jsonWriter.startObject();

            jsonWriter.writeName("start_time");
            jsonWriter.writeColon();
            jsonWriter.writeString(now());

            jsonWriter.writeName("tla_module");
            jsonWriter.writeColon();
            String rootFile = TLCGlobals.mainChecker.tool.getRootFile();
            jsonWriter.writeString(Path.of(rootFile).getFileName().toString().replaceAll(".tla$", ""));

            jsonWriter.writeName("tla_constants");
            jsonWriter.writeColon();
            jsonWriter.startObject();
            for (Map.Entry<String, Value> entry : constMap.entrySet()) {
                jsonWriter.writeName(entry.getKey());
                jsonWriter.writeColon();
                jsonWriter.writeAny(FastJsonSerializer.serialize(entry.getValue()));
            }
            jsonWriter.endObject();

            ImmutableList<String> fileNames = IntInterval.oneTo(threads)
                    .collect(i -> String.format(FILE_NAME_FORMAT, i));

            jsonWriter.writeName("state_count");
            jsonWriter.writeColon();
            jsonWriter.writeInt32(this.stateGraphPathExtractor.getStateCount());

            jsonWriter.writeName("state_files");
            jsonWriter.writeColon();
            jsonWriter.writeAny(fileNames.collect(f -> this.dir.relativize(this.dir.resolve("states").resolve(f)).toString()));

            jsonWriter.writeName("action_count");
            jsonWriter.writeColon();
            jsonWriter.writeInt32(this.stateGraphPathExtractor.getActionCount());

            jsonWriter.writeName("action_files");
            jsonWriter.writeColon();
            jsonWriter.writeAny(fileNames.collect(f -> this.dir.relativize(this.dir.resolve("actions").resolve(f)).toString()));

            jsonWriter.writeName("execution_count");
            jsonWriter.writeColon();
            jsonWriter.writeInt32(pathCount);

            jsonWriter.writeName("execution_files");
            jsonWriter.writeColon();
            jsonWriter.writeAny(fileNames.collect(f -> this.dir.relativize(this.dir.resolve("executions").resolve(f)).toString()));

            jsonWriter.endObject();
            jsonWriter.flushTo(writer);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        long totalSize = -1;
        try (Stream<Path> walkStream = Files.walk(this.dir, 2)) {
            totalSize = walkStream
                    .filter(p -> p.toFile().isFile())
                    .mapToLong(p -> p.toFile().length())
                    .sum();
        } catch (IOException ignored) {
        }

        MP.printMessage(EC.GENERAL, "Path cover successfully exported ("
                + (totalSize != -1 ? formatBytes(totalSize) : "unknown size") + ", " + now() + ").");
    }

    @Override
    public String getDumpFileName() {
        return null;
    }

    @Override
    public void snapshot() {
        // No operations
    }

    private static class JSONSparseArrayWriter implements Closeable {
        private final BufferedWriter writer;
        private final JSONWriter jsonWriter;
        private int flushCounter;
        private final int flushPeriod;
        private static final int DEFAULT_FLUSH_PERIOD = 128;

        public JSONSparseArrayWriter(Path path, int flushPeriod) throws IOException {
            this.writer = Files.newBufferedWriter(path);
            this.jsonWriter = JSONWriter.ofUTF8();
            this.flushCounter = 0;
            this.flushPeriod = flushPeriod;

            this.jsonWriter.startObject();
            this.jsonWriter.flushTo(this.writer);
        }

        public JSONSparseArrayWriter(Path path) throws IOException {
            this(path, DEFAULT_FLUSH_PERIOD);
        }

        public void write(Consumer<JSONWriter> consumer) {
            consumer.accept(jsonWriter);

            flushCounter++;
            if (flushCounter >= flushPeriod) {
                flushCounter = 0;
                jsonWriter.flushTo(writer);
            }
        }

        @Override
        public void close() throws IOException {
            this.jsonWriter.endObject();
            this.jsonWriter.flushTo(this.writer);
            this.jsonWriter.close();
            this.writer.close();
        }
    }
}
