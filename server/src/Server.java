import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class server {
    private static final int LEASE_DURATION = 5 * 60 * 1000; // 5 minute
    private static final Map<Integer, Long> leasedMessageIDs = new HashMap<>();

    public static long combine(int reserved1, int bodyLength, int reserved2, int statusCodetoClient, int timestamp, int messageID, int queryType, int messageType) {
        // header package is created
        long paket = 0L;
        paket |= (reserved1 & 0xFFL);
        paket |= ((bodyLength & 0xFFL) << 8);
        paket |= ((reserved2 & 0x1FL) << 16);
        paket |= ((statusCodetoClient & 0x07L) << 21);
        paket |= ((timestamp & 0xFFFFFFFFL) << 24);
        paket |= ((messageID & 0x1FL) << 56);
        paket |= ((queryType & 0x03L) << 61);
        paket |= (messageType & 0x01L) << 63;
        return paket;
    }

    public static byte[] combineHeaderBody(long header, byte[] body) {
        // header and body are combined into one package
        byte[] paketBytes = new byte[8 + body.length];
        for (int i = 0; i < 8; i++) {
            paketBytes[i] = (byte) (header >> (56 - (i * 8)));
        }
        System.arraycopy(body, 0, paketBytes, 8, body.length);
        return paketBytes;
    }
    public static List<Path> searchFiles(Path dizin, String uzanti) throws IOException {
        // searches for a specific file extension in a specified file path
        List<Path> files = new ArrayList<>();
        Files.walkFileTree(dizin, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (file.toString().endsWith(uzanti)) {
                    files.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return files;
    }
    public static boolean checkDirectoryExistence(String directoryPath) {
        // check spesific directory path
        File directory = new File(directoryPath);
        return directory.exists() && directory.isDirectory();
    }
    public static boolean checkFileExistence(String filePath) {
        // check spesific file
        File file = new File(filePath);
        return file.exists() && file.isFile();
    }
    public static void main(String[] args) {
        int port = 31369; // port number
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Server is listening on port " + port);
            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("New client connected");

                Thread clientThread = new Thread(() -> { // allows multiple users to connect simultaneously
                    try {
                        handleClient(socket);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
                clientThread.start();
            }
        } catch (SocketException se) {
            System.out.println("Client connection closed.");
        } catch (IOException e) {
            System.out.println("Server exception: " + e.getMessage());
            e.printStackTrace();
        }
    }
    private static void handleClient(Socket socket) throws IOException { // the area where the transactions will be made for each client
        int statusCodetoClient;
        String text = " ";
        int reserved1 = 0;
        int reserved2 = 0;
        int totalsize;
        int bodysize;
        String file1 = "";
        String path = "";
        try (DataInputStream inputStream = new DataInputStream(socket.getInputStream());
             DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream())) {
            // the first packet arriving at the server
            byte[] receivedPacket = new byte[300];
            int bytesRead = inputStream.read(receivedPacket);
            if (bytesRead == -1) {
                return;
            }
            byte[] headerBytes = new byte[8];
            System.arraycopy(receivedPacket, 0, headerBytes, 0, headerBytes.length);
            long header = 0;
            for (int i = 0; i < headerBytes.length; i++) {
                header |= (headerBytes[i] & 0xFFL) << (headerBytes.length - 1 - i) * 8;
            }
            int messageType = (int) ((header >> 63) & 0x01);
            int queryType = (int) ((header >> 61) & 0x03);
            int messageId = (int) ((header >> 56) & 0x1F);
            int timeStamp = (int) ((header >> 24) & 0xFFFFFFFFL);
            int bodyLength = (int) ((header >> 8) & 0xFF);
            int bodyStartIndex = headerBytes.length;
            byte[] bodyBytes = new byte[bodyLength];
            System.arraycopy(receivedPacket, bodyStartIndex, bodyBytes, 0, bodyLength);
                // it checks whether the incoming package is a query or not, its body size and the accuracy of the query type number.
            if (!leasedMessageIDs.containsKey(messageId)) {  // it checks if there is another client connected with the same id number.
                    leasedMessageIDs.put(messageId, System.currentTimeMillis() + LEASE_DURATION);
                    statusCodetoClient = 0b111;
            } else {
                    statusCodetoClient = 0b101; // ID LEASED
            }
            // the first packet sent to the client
            long headerPacket = combine(reserved1, bodyLength, reserved2, statusCodetoClient, timeStamp, messageId, queryType, messageType);
            if (text.isEmpty()) {
                bodyBytes = null;
            } else {
                bodyBytes = text.getBytes(StandardCharsets.UTF_8);
            }
            byte[] paketBytes = combineHeaderBody(headerPacket, bodyBytes);
            outputStream.write(paketBytes);


            // the second packet arriving at the server
            bytesRead = inputStream.read(receivedPacket);
            if (bytesRead == -1) {
                return;
            }
            totalsize = bytesRead;
            bodysize = totalsize - 8;
            System.arraycopy(receivedPacket, 0, headerBytes, 0, headerBytes.length);
            header = 0;
            for (int i = 0; i < headerBytes.length; i++) {
                header |= (headerBytes[i] & 0xFFL) << (headerBytes.length - 1 - i) * 8;
            }
            queryType = (int) ((header >> 61) & 0x03);
            int bodyLengthfromClient = (int) ((header >> 8) & 0xFF);
            bodyBytes = new byte[bodyLengthfromClient];
            System.arraycopy(receivedPacket, bodyStartIndex, bodyBytes, 0, bodyLengthfromClient);

            String bodyString = new String(bodyBytes, StandardCharsets.UTF_8).replaceAll("\0", "");

            // actions taken according to the first package received
            if ((bodysize <= bodyLength)) { // it checks whether the incoming package is a query or not, its body size and the accuracy of the query type number.
                if ((queryType == 0)) {
                    boolean isDirectoryExists = checkDirectoryExistence(bodyString);
                    if (isDirectoryExists) {
                        statusCodetoClient = 0b000; // EXIST
                    } else {
                        statusCodetoClient = 0b001; // NOT EXIST
                    }
                } else {
                    boolean isDirectoryExists = checkDirectoryExistence(bodyString);
                    if (isDirectoryExists) {
                        statusCodetoClient = 0b000; // EXIST
                    } else {
                        statusCodetoClient = 0b110; // DIRECTORY NEEDED

                    }
                }
                path = bodyString;
            } else {
                statusCodetoClient = 0b100; // BAD REQUEST
            }

            // the second packet sent to the client
            headerPacket = combine(reserved1, bodyLength, reserved2, statusCodetoClient, timeStamp, messageId, queryType, messageType);
            if (text.isEmpty()) {
                bodyBytes = null;
            } else {
                bodyBytes = text.getBytes(StandardCharsets.UTF_8);
            }
            paketBytes = combineHeaderBody(headerPacket, bodyBytes);
            outputStream.write(paketBytes);


            if ((queryType == 1) || (queryType == 2) || (queryType == 3)) {
                // the third packet arriving at the server
                receivedPacket = new byte[300];
                bytesRead = inputStream.read(receivedPacket);
                if (bytesRead == -1) {
                    return;
                }
                totalsize = bytesRead;
                bodysize = totalsize - 8;
                System.arraycopy(receivedPacket, 0, headerBytes, 0, headerBytes.length);
                header = 0;
                for (int i = 0; i < headerBytes.length; i++) {
                    header |= (headerBytes[i] & 0xFFL) << (headerBytes.length - 1 - i) * 8;
                }
                timeStamp = (int) ((header >> 24) & 0xFFFFFFFFL);
                int hour = (timeStamp >> 27) & 0x1F;
                int minute = (timeStamp >> 21) & 0x3F;
                int second = (timeStamp >> 15) & 0x3F;
                int day = (timeStamp >> 10) & 0x1F;
                int month = (timeStamp >> 6) & 0x0F;
                int year = ((timeStamp) & 0x3F) + 2000;
                bodyLengthfromClient = (int) ((header >> 8) & 0xFF);
                bodyBytes = new byte[bodyLengthfromClient];
                System.arraycopy(receivedPacket, bodyStartIndex, bodyBytes, 0, bodyLengthfromClient);
                bodyString = new String(bodyBytes, StandardCharsets.UTF_8).replaceAll("\0", "");
                System.out.println(bodyString);
                // actions taken according to the second package received
                if ((bodysize <= bodyLength)) {
                    if ((queryType == 1) || (queryType == 2)) {
                        boolean isFileExists = checkFileExistence(bodyString);
                        if (isFileExists) {
                            statusCodetoClient = 0b000; // EXIST
                        } else {
                            statusCodetoClient = 0b001; // NOT EXIST
                        }
                    } else{
                        LocalDateTime targetDateTime = LocalDateTime.of(year, month, day, hour, minute, second);
                        String extension = bodyString;
                        try {
                            List<Path> fileList = searchFiles(Paths.get(path), extension);
                            if (fileList.isEmpty()) {
                                statusCodetoClient = 0b001; // NOT EXIST
                            } else {
                                for (Path filePath : fileList) {
                                    BasicFileAttributes attributes = Files.readAttributes(filePath, BasicFileAttributes.class);
                                    FileTime fileTime = attributes.lastModifiedTime();
                                    LocalDateTime fileModificationTime = fileTime.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
                                    int comparisonResult = fileModificationTime.compareTo(targetDateTime);
                                    if (comparisonResult > 0) {
                                        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                                        String formattedDateTime = fileModificationTime.format(formatter);
                                        text += filePath.getFileName() + " - " + formattedDateTime + ",";
                                        statusCodetoClient = 0b111; // SUCCESS
                                    }
                                }
                                if (statusCodetoClient != 0b111) {
                                    statusCodetoClient = 0b001; // NOT EXIST
                                }
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                } else {
                    statusCodetoClient = 0b100; // BAD REQUEST
                }

                file1 = bodyString;

                // the third packet sent to the client
                headerPacket = combine(reserved1, bodyLength, reserved2, statusCodetoClient, timeStamp, messageId, queryType, messageType);
                if (text.isEmpty()) {
                    bodyBytes = null;
                } else {
                    bodyBytes = text.getBytes(StandardCharsets.UTF_8);
                }
                paketBytes = combineHeaderBody(headerPacket, bodyBytes);
                outputStream.write(paketBytes);
            }
            if (queryType == 2) {
                // the fourth packet arriving at the server
                bytesRead = inputStream.read(receivedPacket);
                if (bytesRead == -1) {
                    return;
                }
                System.arraycopy(receivedPacket, 0, headerBytes, 0, headerBytes.length);
                header = 0;
                for (int i = 0; i < headerBytes.length; i++) {
                    header |= (headerBytes[i] & 0xFFL) << (headerBytes.length - 1 - i) * 8;
                }
                timeStamp = (int) ((header >> 24) & 0xFFFFFFFFL);
                int hour = (timeStamp >> 27) & 0x1F;
                int minute = (timeStamp >> 21) & 0x3F;
                int second = (timeStamp >> 15) & 0x3F;
                int day = (timeStamp >> 10) & 0x1F;
                int month = (timeStamp >> 6) & 0x0F;
                int year = ((timeStamp) & 0x3F) + 2000;
                bodyLengthfromClient = (int) ((header >> 8) & 0xFF);
                bodyBytes = new byte[bodyLengthfromClient];
                System.arraycopy(receivedPacket, bodyStartIndex, bodyBytes, 0, bodyLengthfromClient);
                File file = new File(file1);

                // actions taken according to the second package received
                BasicFileAttributes attributes = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
                FileTime fileTime = attributes.lastModifiedTime();
                LocalDateTime dosyaZamani = fileTime.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
                LocalDateTime karsilastirilacakZaman = LocalDateTime.of(year, month, day, hour, minute, second);
                if (dosyaZamani.isAfter(karsilastirilacakZaman)) {
                    statusCodetoClient = 0b010;
                } else if (dosyaZamani.isBefore(karsilastirilacakZaman)) {
                    statusCodetoClient = 0b011;
                }

                // the fourth packet sent to the client
                headerPacket = combine(reserved1, bodyLength, reserved2, statusCodetoClient, timeStamp, messageId, queryType, messageType);
                if (text.isEmpty()) {
                    bodyBytes = null;
                } else {
                    bodyBytes = text.getBytes(StandardCharsets.UTF_8);
                }
                paketBytes = combineHeaderBody(headerPacket, bodyBytes);
                outputStream.write(paketBytes);
            }
            try {
                socket.close();
                System.out.println("Socket closed successfully.");
            } catch (SocketException se) {
                System.out.println("Client connection closed.");
            } catch (IOException e) {
                System.err.println("Error closing socket: " + e.getMessage());
            }
        }
    }
}
