


import authorization.AuthService;
import authorization.AuthServiceImpl;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 *
  * @version 31.03.2019
 */


/*
Задание 3
1. Добавить на серверную сторону чата логирование, с выводом информации о действиях на сервере
(запущен, произошла ошибка, клиент подключился, клиент прислал сообщение/команду).
Использую SLF4J и logback (done)

 */


public class ChatServer {


    //Создаем экземпляр логгера
    private static Logger logger = LoggerFactory.getLogger(ChatServer.class);


    //Объявляем паттерны

    private static final Pattern AUTH_PATTERN = Pattern.compile("^/auth (\\w+) (\\w+)$");


    private AuthService authService;

    //Создаем экземпляр авторизации
    {
        try {
            authService = new AuthServiceImpl();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private Map<String, ClientHandler> clientHandlerMap = Collections.synchronizedMap(new HashMap<>());

    private ExecutorService execService; //Объявляем Executor Service

    public static void main(String[] args) {
        ChatServer chatServer = new ChatServer();
        chatServer.start(7777);
    }





    public void start(int port) {

        //Добавляем сервис асинхронного выполнения
        execService = Executors.newCachedThreadPool(); //не фиксированного размера так как клиентов
        //может быть несколько


        try (ServerSocket serverSocket = new ServerSocket(port)) {

            logger.debug("Server started!");

            while (true) {
                Socket socket = serverSocket.accept();
                DataInputStream inp = new DataInputStream(socket.getInputStream());
                DataOutputStream out = new DataOutputStream(socket.getOutputStream());

                logger.info("New client connected!");


                try {
                    String authMessage = inp.readUTF();
                    Matcher matcher = AUTH_PATTERN.matcher(authMessage);
                    if (matcher.matches()) {
                        String username = matcher.group(1);
                        String password = matcher.group(2);

                        if (authService.authUser(username, password)) {
                            clientHandlerMap.put(username, new ClientHandler(username, socket, this));
                            out.writeUTF("/auth successful");
                            out.flush();
                            logger.info("Authorization for user {} successful", username);
                            //System.out.printf("Authorization for user %s successful%n", username);

                            broadcastUserConnection();

                        } else {
                            logger.debug ("Authorization for user {} failed", username);

                            out.writeUTF("/auth fails");
                            out.flush();
                            socket.close();
                        }
                    } else {

                        logger.error("Incorrect authorization message {}", authMessage);
                       // System.out.printf("Incorrect authorization message %s%n", authMessage);
                        out.writeUTF("/auth fails");
                                      out.flush();
                        socket.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            execService.shutdown(); //Закрываем
        }
    }

    public void sendMessage(String userTo, String userFrom, String msg) throws IOException {
        ClientHandler userToClientHandler = clientHandlerMap.get(userTo);
        if (userToClientHandler != null) {
            userToClientHandler.sendMessage(userFrom, msg);
        } else {
            logger.info("User {} not found. Message from {} is lost.", userTo, userFrom);
            //System.out.printf("User %s not found. Message from %s is lost.%n", userTo, userFrom);
        }
    }

    public void sendUserConsistMessage(String userTo, String userFrom, String msg) throws IOException {
        ClientHandler userToClientHandler = clientHandlerMap.get(userTo);
        if (userToClientHandler != null) {
            userToClientHandler.sendUserConsistMessage(userFrom, msg);
        } else {
            logger.info("User {} not found. Message from {} is lost.", userTo, userFrom);
        }
    }



    public List<String> getUserList() {
        return new ArrayList<>(clientHandlerMap.keySet());
    }

    public void unsubscribeClient(ClientHandler clientHandler) throws IOException {

       //Удаляем из списка
        clientHandlerMap.remove(clientHandler.getUsername());
        broadcastUserConnection();

    }

    //Шлем информацию всем клиентам при подключении/отключении пользователя
    public void broadcastUserConnection() {

        //System.out.println(getUserList());
        logger.info("Users: "+ getUserList());
        List<String> namesList;
        namesList = getUserList();

        //Преобразуем ArrayList в строку для пересылки
        StringBuilder sb = new StringBuilder();
        for (String s :  namesList)
        {
            sb.append(s);
            sb.append("//");
        }


        //Шлем всем информацию о текущих пользователях
        for (String name : namesList) {
            try {
                 sendUserConsistMessage(name,"", sb.toString());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    //Геттер
    public ExecutorService getExecutorService() {
        return execService;
    }


}
