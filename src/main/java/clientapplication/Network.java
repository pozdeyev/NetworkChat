package clientapplication;

import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Network implements Closeable {

    private static final String AUTH_PATTERN = "/auth %s %s";
    private static final String MESSAGE_SEND_PATTERN = "/w %s %s";
    private static final String USER_CONSIST_PATTERN = "/userconn";

    private static final Pattern MESSAGE_PATTERN = Pattern.compile("^/w (\\w+) (.+)", Pattern.MULTILINE);

    private Socket socket;
    private DataOutputStream out;
    private DataInputStream in;
    private MessageWriterHistory historyforuser = new  MessageWriterHistory ("","", "", "");
    private History historyformachine;

    private final MessageSender messageSender;
    private final Thread receiver;

    private String username;
    private final String hostName;
    private final int port;

    public Network(String hostName, int port, MessageSender messageSender) {
        this.hostName = hostName;
        this.port = port;
        this.messageSender = messageSender;
        this.receiver = createReceiverThread();
    }

    private Thread createReceiverThread() {
        return new Thread(new Runnable() {
            @Override
            public void run() {
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        String text = in.readUTF();
                        System.out.println("New message: " + text);
                        Matcher matcher = MESSAGE_PATTERN.matcher(text);
                        if (matcher.matches()) {
                            Message msg = new Message(matcher.group(1), username,
                                    matcher.group(2));


                            //Пишем историю для пользователя
                            historyforuser.messageWriter(matcher.group(1), "I", username, matcher.group(2));

                            //Пишем историю для компьютера
                            historyformachine.saveRecord(msg);

                            messageSender.submitMessage(msg);

                        } else if (text.startsWith(USER_CONSIST_PATTERN)) {

                            //Если  видим паттерн на добавление пользователя - вычленяем имя.
                            String addUser;
                            addUser = text.replace (USER_CONSIST_PATTERN, "");
                            System.out.println("add user:" + addUser);

                            String[] userArr = addUser.split("//");
                            messageSender.submitUser(userArr);
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                System.out.printf("Network connection is closed for user %s%n", username);
            }
        });
    }


            public void sendMessageToUser(Message message) {

               sendMessage(String.format(MESSAGE_SEND_PATTERN, message.getUserTo(), message.getText()));


                historyformachine.saveRecord(message);


                try {
                    historyforuser.messageWriter(message.getUserTo(), username, "me", message.getText());
                } catch (IOException e) {
                    e.printStackTrace();
                }
               // messageSender.submitMessage(message);
            }

    private void sendMessage(String msg) {
        try {
            out.writeUTF(msg);
            out.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

//запрос истории
    public List<Message> getHistory() {
        return historyformachine.getLastMessages();
    }


    public void authorize(String username, String password) throws IOException {
        socket = new Socket(hostName, port);
        out = new DataOutputStream(socket.getOutputStream());
        in = new DataInputStream(socket.getInputStream());

        out.writeUTF(String.format(AUTH_PATTERN, username, password));
        String response = in.readUTF();
        if (response.equals("/auth successful")) {
            this.username = username;

            historyformachine = new History (username);
            receiver.start();


        } else {
            throw new AuthException();
        }
    }

    public String getUsername() {
        return username;
    }

    @Override
    public void close() throws IOException {
        socket.close();
        receiver.interrupt();
        try {
            receiver.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
