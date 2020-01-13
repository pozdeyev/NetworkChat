import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class ClientHandler {

    //Создаем экземпляр логгера
    private static Logger logger = LoggerFactory.getLogger(ClientHandler.class);

    private static final Pattern MESSAGE_PATTERN = Pattern.compile("^/w (\\w+) (.+)", Pattern.MULTILINE);
    private static final String MESSAGE_SEND_PATTERN = "/w %s %s";
    private static final String USER_CONSIST_PATTERN = "/userconn%s%s";


   // private final Thread handleThread;
    private final DataInputStream inp;
    private final DataOutputStream out;
    private final ChatServer server;
    private final String username;
    private final Socket socket;

    public ClientHandler(String username, Socket socket, ChatServer server) throws IOException {
        this.username = username;
        this.socket = socket;
        this.server = server;
        this.inp = new DataInputStream(socket.getInputStream());
        this.out = new DataOutputStream(socket.getOutputStream());

        server.getExecutorService().submit(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    String msg = inp.readUTF();

                    logger.info("Message from user {}:  {}", username, msg);
                   // System.out.printf("Message from user %s: %s%n", username, msg);

                    Matcher matcher = MESSAGE_PATTERN.matcher(msg);
                    if (matcher.matches()) {
                        String userTo = matcher.group(1);
                        String message = matcher.group(2);
                        server.sendMessage(userTo, username, message);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                logger.debug("Client {} disconnected", username);
                //System.out.printf("Client %s disconnected%n", username);
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }

                try {
                    server.unsubscribeClient(ClientHandler.this);
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }

        });
        // handleThread.start();
    }

    public void sendMessage(String userTo, String msg) throws IOException {
        out.writeUTF(String.format(MESSAGE_SEND_PATTERN, userTo, msg));
    }

    //Сообщение на клиенты о добавлении/удаелнии пользователя
    public void sendUserConsistMessage(String userTo, String msg) throws IOException {
        out.writeUTF(String.format(USER_CONSIST_PATTERN, userTo, msg));
    }



    public String getUsername() {
        return username;
    }
}
