package tlc2.util;

import com.alibaba.fastjson2.JSONWriter;
import tlc2.TLCGlobals;
import tlc2.output.EC;
import tlc2.output.MP;
import tlc2.tool.Action;
import tlc2.tool.TLCState;

import java.io.Closeable;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class TCPTraceWriter implements ITraceWriter {
    private boolean closed;
    private ServerSocket mainServerSocket;
    private Socket mainClientSocket;
    private final List<ServerSocket> serverSockets = new ArrayList<>();
    private final List<Socket> clientSockets = new ArrayList<>();

    private static final int TCP_PORT = Integer.getInteger(TCPTraceWriter.class.getName() + ".port", 11235);

    static {
        System.out.println("hii");
    }

    @Override
    public void init() {
        try {
            mainServerSocket = new ServerSocket(TCP_PORT);

            MP.printMessage(EC.GENERAL, String.format("Waiting for TPC connection on :%s...", TCP_PORT));
            mainClientSocket = mainServerSocket.accept();
            MP.printMessage(EC.GENERAL, String.format("Client :%s connected", mainClientSocket.getPort()));

            for (int i = 0; i < TLCGlobals.getNumWorkers(); i++) {
                serverSockets.add(new ServerSocket(0));
                clientSockets.add(null);
            }

            try (JSONWriter jsonWriter = JSONWriter.ofUTF8()) {
                jsonWriter.startObject();

                ExportUtil.writeTlaModule(jsonWriter);

                ExportUtil.writeTlaConstants(jsonWriter);

                jsonWriter.writeName("worker_ports");
                jsonWriter.writeColon();
                jsonWriter.writeAny(serverSockets.stream().map(ServerSocket::getLocalPort).collect(Collectors.toList()));

                jsonWriter.endObject();
                jsonWriter.flushTo(mainClientSocket.getOutputStream());
            }
        } catch (IOException e) {
            close();
        }
    }

    public Socket getSocket() throws IOException {
        int id = IdThread.GetId();
        Socket sock = clientSockets.get(id);
        if (sock == null) {
            sock = serverSockets.get(id).accept();
            clientSockets.set(id, sock);
            MP.printMessage(EC.GENERAL, String.format("Client worker :%s connected", sock.getPort()));
        }
        return sock;
    }

    @Override
    public void writeTraceInitState(TLCState state) {
        if (closed) {
            return;
        }
        try (JSONWriter jsonWriter = JSONWriter.ofUTF8()) {
            Socket sock = getSocket();

            jsonWriter.startArray();
            ExportUtil.writeState(jsonWriter, state);
            jsonWriter.flushTo(sock.getOutputStream());
        } catch (IOException e) {
            close(false);
        }
    }

    @Override
    public void writeTraceAction(TLCState state, TLCState successor, Action action) {
        if (closed) {
            return;
        }
        try (JSONWriter jsonWriter = JSONWriter.ofUTF8()) {
            Socket sock = getSocket();

            jsonWriter.writeComma();
            ExportUtil.writeAction(jsonWriter, state, successor, action);
            jsonWriter.writeComma();
            ExportUtil.writeState(jsonWriter, state);
            jsonWriter.flushTo(sock.getOutputStream());
        } catch (IOException e) {
            close(false);
        }
    }

    @Override
    public void writeTraceEnd() {
        if (closed) {
            return;
        }
        try (JSONWriter jsonWriter = JSONWriter.ofUTF8()) {
            Socket sock = getSocket();

            jsonWriter.endArray();
            jsonWriter.flushTo(sock.getOutputStream());
        } catch (IOException e) {
            close(false);
        }
    }

    private static void close(Closeable closeable) {
        try {
            if (closeable != null) {
                closeable.close();
            }
        } catch (IOException ignored) {
        }
    }

    private void close(boolean graceful) {
        if (!graceful) {
            MP.printMessage(EC.GENERAL, "Client closed TCP connection. Continuing without trace exporting...");
        }
        closed = true;
        close(mainServerSocket);
        close(mainClientSocket);
        for (int i = 0; i < serverSockets.size(); i++) {
            close(serverSockets.get(i));
            close(clientSockets.get(i));
        }
    }

    @Override
    public void close() {
        close(true);
    }
}
