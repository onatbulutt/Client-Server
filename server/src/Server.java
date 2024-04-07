
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

public class Server {
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
        List<Path> bulunanDosyalar = new ArrayList<>();
        Files.walkFileTree(dizin, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (file.toString().endsWith(uzanti)) {
                    bulunanDosyalar.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return bulunanDosyalar;
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
        String text = " ";
        int reserved1 = 0;
        int reserved2 = 0;

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
        int totalsize = 0;
        int bodysize = 0;
        String file1 = "";
        String path = "";

        try (DataInputStream inputStream = new DataInputStream(socket.getInputStream());
             DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream())) {

            // the first packet arriving at the server
            byte[] receivedPacket4 = new byte[300];
            int bytesRead4 = inputStream.read(receivedPacket4);
            if (bytesRead4 == -1) {
                return;
            }
            byte[] headerBytes4 = new byte[8];
            System.arraycopy(receivedPacket4, 0, headerBytes4, 0, headerBytes4.length);
            long header4 = 0;
            for (int i = 0; i < headerBytes4.length; i++) {
                header4 |= (headerBytes4[i] & 0xFFL) << (headerBytes4.length - 1 - i) * 8;
            }
            int messageType4 = (int) ((header4 >> 63) & 0x01);
            int queryType4 = (int) ((header4 >> 61) & 0x03);
            int messageId4 = (int) ((header4 >> 56) & 0x1F);
            int timeStamp4 = (int) ((header4 >> 24) & 0xFFFFFFFFL);
            int bodyLength4 = (int) ((header4 >> 8) & 0xFF);
            int bodyStartIndex4 = headerBytes4.length;
            int bodyLengthfromClient4 = (int) ((header4 >> 8) & 0xFF);
            byte[] bodyBytes4 = new byte[bodyLengthfromClient4];
            System.arraycopy(receivedPacket4, bodyStartIndex4, bodyBytes4, 0, bodyLengthfromClient4);
            if (!leasedMessageIDs.containsKey(messageId4)) {  // it checks if there is another client connected with the same id number.
                leasedMessageIDs.put(messageId4, System.currentTimeMillis() + LEASE_DURATION);
                statusCodetoClient = 0b101; // ID LEASED
            } else {
                statusCodetoClient = 0b100; // BAD REQUEST
            }

            // the first packet sent to the client
            long headerPacket4 = combine(reserved1, bodyLength4, reserved2, statusCodetoClient, timeStamp4, messageId4, queryType4, messageType4);
            if (text.isEmpty()) {
                bodyBytes4 = null;
            } else {
                bodyBytes4 = text.getBytes(StandardCharsets.UTF_8);
            }
            byte[] paketBytes4 = combineHeaderBody(headerPacket4, bodyBytes4);
            outputStream.write(paketBytes4);


            // the second packet arriving at the server
            byte[] receivedPacket = new byte[300];
            int bytesRead = inputStream.read(receivedPacket);
            if (bytesRead == -1) {
                return;
            }
            totalsize = bytesRead;
            bodysize = totalsize - 8;
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
            int bodyLengthfromClient = (int) ((header >> 8) & 0xFF);
            byte[] bodyBytes = new byte[bodyLengthfromClient];
            System.arraycopy(receivedPacket, bodyStartIndex, bodyBytes, 0, bodyLengthfromClient);
            String bodyString = new String(bodyBytes, StandardCharsets.UTF_8).replaceAll("\0", "");

            // actions taken according to the first package received
            if ((bodysize <= bodyLength) && (queryType <= 3 && queryType >= 0) && (messageType == 0)) { // it checks whether the incoming package is a query or not, its body size and the accuracy of the query type number.
                if ((queryType == 0)) {
                    boolean isDirectoryExists = checkDirectoryExistence(bodyString);
                    if (isDirectoryExists) {
                        statusCodetoClient = 0b000; // EXIST
                    } else {
                        statusCodetoClient = 0b001; // NOT EXIST
                    }
                } else if ((queryType == 1) || (queryType == 2) || (queryType == 3)) {
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
            long headerPacket = combine(reserved1, bodyLength, reserved2, statusCodetoClient, timeStamp, messageId, queryType, messageType);
            if (text.isEmpty()) {
                bodyBytes = null;
            } else {
                bodyBytes = text.getBytes(StandardCharsets.UTF_8);
            }
            byte[] paketBytes = combineHeaderBody(headerPacket, bodyBytes);
            outputStream.write(paketBytes);


            if ((queryType == 1) || (queryType == 2) || (queryType == 3)) {
                // the third packet arriving at the server
                byte[] receivedPacket2 = new byte[300];
                int bytesRead2 = inputStream.read(receivedPacket2);
                if (bytesRead2 == -1) {
                    return;
                }
                totalsize = bytesRead2;
                bodysize = totalsize - 8;
                byte[] headerBytes2 = new byte[8];
                System.arraycopy(receivedPacket2, 0, headerBytes2, 0, headerBytes2.length);
                long header2 = 0;
                for (int i = 0; i < headerBytes2.length; i++) {
                    header2 |= (headerBytes2[i] & 0xFFL) << (headerBytes2.length - 1 - i) * 8;
                }
                int queryType2 = (int) ((header2 >> 61) & 0x03);
                int timeStamp2 = (int) ((header2 >> 24) & 0xFFFFFFFFL);
                int hour2 = (timeStamp2 >> 27) & 0x1F;
                int minute2 = (timeStamp2 >> 21) & 0x3F;
                int second2 = (timeStamp2 >> 15) & 0x3F;
                int day2 = (timeStamp2 >> 10) & 0x1F;
                int month2 = (timeStamp2 >> 6) & 0x0F;
                int year2 = ((timeStamp2) & 0x3F) + 2000;
                int bodyStartIndex2 = headerBytes2.length;
                int bodyLengthfromClient2 = (int) ((header2 >> 8) & 0xFF);
                byte[] bodyBytes2 = new byte[bodyLengthfromClient2];
                System.arraycopy(receivedPacket2, bodyStartIndex2, bodyBytes2, 0, bodyLengthfromClient2);
                String bodyString2 = new String(bodyBytes2, StandardCharsets.UTF_8).replaceAll("\0", "");

                // actions taken according to the second package received
                if ((bodysize <= bodyLength)) {
                    if ((queryType2 == 1) || (queryType2 == 2)) {
                        boolean isFileExists = checkFileExistence(bodyString2);
                        System.out.println(bodyString2);
                        if (isFileExists) {
                            statusCodetoClient = 0b000; // EXIST
                        } else {
                            statusCodetoClient = 0b001; // NOT EXIST
                        }
                    } else if (queryType2 == 3) {
                        LocalDateTime targetDateTime = LocalDateTime.of(year2, month2, day2, hour2, minute2, second2);
                        String extension = bodyString2;
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

                file1 = bodyString2;
                // the third packet sent to the client
                long headerPacket2 = combine(reserved1, bodyLength, reserved2, statusCodetoClient, timeStamp, messageId, queryType, messageType);
                if (text.isEmpty()) {
                    bodyBytes2 = null;
                } else {
                    bodyBytes2 = text.getBytes(StandardCharsets.UTF_8);
                }
                byte[] paketBytes2 = combineHeaderBody(headerPacket2, bodyBytes2);
                outputStream.write(paketBytes2);
            }
            if (queryType == 2) {
                // the fourth packet arriving at the server
                byte[] receivedPacket3 = new byte[300];
                int bytesRead3 = inputStream.read(receivedPacket3);
                if (bytesRead3 == -1) {
                    return;
                }
                byte[] headerBytes3 = new byte[8];
                System.arraycopy(receivedPacket3, 0, headerBytes3, 0, headerBytes3.length);
                long header3 = 0;
                for (int i = 0; i < headerBytes3.length; i++) {
                    header3 |= (headerBytes3[i] & 0xFFL) << (headerBytes3.length - 1 - i) * 8;
                }
                int timeStamp3 = (int) ((header3 >> 24) & 0xFFFFFFFFL);
                int hour = (timeStamp3 >> 27) & 0x1F;
                int minute = (timeStamp3 >> 21) & 0x3F;
                int second = (timeStamp3 >> 15) & 0x3F;
                int day = (timeStamp3 >> 10) & 0x1F;
                int month = (timeStamp3 >> 6) & 0x0F;
                int year = ((timeStamp3) & 0x3F) + 2000;
                int bodyStartIndex3 = headerBytes3.length;
                int bodyLengthfromClient3 = (int) ((header3 >> 8) & 0xFF);
                byte[] bodyBytes3 = new byte[bodyLengthfromClient3];
                System.arraycopy(receivedPacket3, bodyStartIndex3, bodyBytes3, 0, bodyLengthfromClient3);
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
                long headerPacket3 = combine(reserved1, bodyLength, reserved2, statusCodetoClient, timeStamp, messageId, queryType, messageType);
                if (text.isEmpty()) {
                    bodyBytes3 = null;
                } else {
                    bodyBytes3 = text.getBytes(StandardCharsets.UTF_8);
                }
                byte[] paketBytes3 = combineHeaderBody(headerPacket3, bodyBytes3);
                outputStream.write(paketBytes3);
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
