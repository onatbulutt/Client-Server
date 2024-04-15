import java.io.*;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class client {
    public static long combine(byte reserved1, int bodyLength, byte reserved2, byte statusCode, int timestamp, int messageID, int queryType, byte messageType) {
        // header package is created
        long paket = 0L;
        paket |= (reserved1 & 0xFFL);
        paket |= ((bodyLength & 0xFFL) << 8);
        paket |= ((reserved2 & 0x1FL) << 16);
        paket |= ((statusCode & 0x07L) << 21);
        paket |= ((timestamp & 0xFFFFFFFFL) << 24);
        paket |= ((messageID & 0x1FL) << 56);
        paket |= ((queryType & 0x03L) << 61);
        paket |= (messageType & 0x01L)<<63;
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

    public static void main(String[] args) {

        String hostname = "127.0.0.1"; // hostname
        int port = 31369;
        byte reserved1=20;
        int bodyLength=200; // you can change body length. The number refers to byte
        byte reserved2=10;
        byte statusCode=0;
        int timestamp=0;
        int messageID;
        int queryType;
        byte messageType=0;
        int hour;
        int minute;
        int second;
        int day;
        int month;
        int year;
        String date;
        String text = " ";
        String fileName=" ";
        String directoryPath = "";

        try (Socket socket = new Socket(hostname, port)) {
            System.out.println("Connected to the server");
            DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream());
            DataOutputStream outputStream2 = new DataOutputStream(socket.getOutputStream());
            DataInputStream inputStream = new DataInputStream(socket.getInputStream());
            System.out.println("Before starting, status codes and meanings are here: 0b000: Exist, 0b001: Not Exist, 0b010: Changed, 0b011: Not Changed, 0b100: Bad Request, 0b101: Id Leasing, 0b110: Directory Needed, 0b111: Success  ");
            Scanner scanner = new Scanner(System.in);
            System.out.print("Enter your specific messageID between 0-31: ");
            messageID = scanner.nextInt();
            while(messageID>31 || messageID<0)
            {
                System.out.println("Unvalid number.Enter your specific messageID between 0-31: ");
                messageID = scanner.nextInt();
            }

            System.out.println("Please select one of the queryType and enter number: ");
            System.out.println("0 - Verify directory existence");
            System.out.println("1 - Check file existence");
            System.out.println("2 - Determine if an existing file has been modified after a specified timestamp");
            System.out.print("3 - Identify files with a specified extension that have been modified after a given timestamp  ");
            queryType = scanner.nextByte();
            if(queryType==0)
            {
                System.out.println("Please enter the path : ");
                text = scanner.next();
            }
            else if((queryType==1) || (queryType==2) || (queryType==3))
            {
                System.out.println("Please enter the path: ");
                text = scanner.next();
                directoryPath=text;
            }

            // first packet to the server
            long header = combine(reserved1, bodyLength, reserved2, statusCode, timestamp, messageID, queryType, messageType);
            byte[] bodyBytes = text.getBytes(StandardCharsets.UTF_8);
            byte[] paketBytes = combineHeaderBody(header, bodyBytes);
            outputStream.write(paketBytes);

            // the first packet from the server
            byte[] receivedPacket = new byte[300];
            int bytesRead = inputStream.read(receivedPacket);
            if (bytesRead == -1) {
                return;
            }
            byte[] headerBytes = new byte[8];
            System.arraycopy(receivedPacket, 0, headerBytes, 0, headerBytes.length);
            long headerFromsender = 0;
            for (int i = 0; i < headerBytes.length; i++) {
                headerFromsender |= (headerBytes[i] & 0xFFL) << (headerBytes.length - 1 - i) * 8;
            }
            int statusCodefromserver = (int) ((headerFromsender >> 21) & 0x07);
            String statusCodeFromServer = String.format("%3s", Integer.toBinaryString(statusCodefromserver)).replace(' ', '0');

            // actions taken according to the first packet coming from the server
            if (statusCodeFromServer.equals("100") || statusCodeFromServer.equals("101")) {
                System.out.println("Response from server: ");
                System.out.println("Status Code: 0b" + statusCodeFromServer);
                socket.close();
                socket.close();
            }


            // second packet to the server
            header = combine(reserved1, bodyLength, reserved2, statusCode, timestamp, messageID, queryType, messageType);
            bodyBytes = text.getBytes(StandardCharsets.UTF_8);
            paketBytes = combineHeaderBody(header, bodyBytes);
            outputStream.write(paketBytes);

            // the second packet from the server
            receivedPacket = new byte[300];
            bytesRead = inputStream.read(receivedPacket);
            if (bytesRead == -1) {
                return;
            }
            System.arraycopy(receivedPacket, 0, headerBytes, 0, headerBytes.length);
            headerFromsender = 0;
            for (int i = 0; i < headerBytes.length; i++) {
                headerFromsender |= (headerBytes[i] & 0xFFL) << (headerBytes.length - 1 - i) * 8;
            }
            statusCodefromserver = (int) ((headerFromsender >> 21) & 0x07);
            int querytypeFromServer = (int)  ((headerFromsender >> 61) & 0x03L);
            statusCodeFromServer = String.format("%3s", Integer.toBinaryString(statusCodefromserver)).replace(' ', '0');


            // data to be received from the user for any query type before the first submission
            if(querytypeFromServer==0)
            {
                System.out.println("Response from server: ");
                System.out.println("Status Code: 0b" + statusCodeFromServer);
                socket.close();
            }
            else if ((querytypeFromServer==1) || (querytypeFromServer==2))
            {
                if(statusCodeFromServer.equals("110") || statusCodeFromServer.equals("100"))
                {
                    System.out.println("Response from server: ");
                    System.out.println("Status Code: 0b" + statusCodeFromServer);
                }
                else if(statusCodeFromServer.equals("000"))
                {
                    header = combine(reserved1, bodyLength, reserved2, statusCode, timestamp, messageID, querytypeFromServer, messageType);
                    System.out.println("Path is available. Please enter the file name that want to query: ");
                    fileName = scanner.next();
                    String fullBody = directoryPath + "\\" + fileName;
                    bodyBytes = fullBody.getBytes(StandardCharsets.UTF_8);
                    paketBytes = combineHeaderBody(header, bodyBytes);
                    outputStream2.write(paketBytes); // second packet to the server
                }
            }
            else if(queryType==3)
            {
                if(statusCodeFromServer.equals("110") || statusCodeFromServer.equals("100"))
                {
                    System.out.println("Response from server: ");
                    System.out.println("Status Code: 0b" + statusCodeFromServer);
                }
                else if(statusCodeFromServer.equals("000"))
                {
                    System.out.println("Path is available.");
                    System.out.println("Enter the specific date with format: year/month/day/hour/minute/second");
                    System.out.println("You must enter the year after 2020. Maximum value of year is 2083");
                    date = scanner.next();
                    System.out.println("Enter the file extension  you want to query(ex: .txt , .pdf , .doc )");
                    String fileType = scanner.next();
                    String[] datePieces = date.split("/");
                    year = Integer.parseInt(datePieces[0]);
                    year = year % 100;
                    month = Integer.parseInt(datePieces[1]);
                    day = Integer.parseInt(datePieces[2]);
                    hour = Integer.parseInt(datePieces[3]);
                    minute = Integer.parseInt(datePieces[4]);
                    second = Integer.parseInt(datePieces[5]);
                    timestamp= (hour<<27) + (minute<<21) + (second<<15) + (day<<10) + (month<<6) + year;
                    // third packet to the server
                    header = combine(reserved1, bodyLength, reserved2, statusCode, timestamp, messageID, querytypeFromServer, messageType);
                    bodyBytes = fileType.getBytes(StandardCharsets.UTF_8);
                    paketBytes = combineHeaderBody(header, bodyBytes);
                    outputStream2.write(paketBytes);
                }
            }

            // Extra operations that need to be done for some query types
            if(((queryType==1)&&statusCodeFromServer.equals("000"))||((queryType==2)&&statusCodeFromServer.equals("000"))||((queryType==3)&&statusCodeFromServer.equals("000")))
            {
                // the third packet from the server
                receivedPacket = new byte[300];
                bytesRead = inputStream.read(receivedPacket);
                if (bytesRead == -1) {
                    return;
                }
                System.arraycopy(receivedPacket, 0, headerBytes, 0, headerBytes.length);
                for (int i = 0; i < headerBytes.length; i++) {
                    headerFromsender |= (headerBytes[i] & 0xFFL) << (headerBytes.length - 1 - i) * 8;
                }
                bodyBytes = new byte[receivedPacket.length - 8];
                System.arraycopy(receivedPacket, 8, bodyBytes, 0, bodyBytes.length);
                statusCodefromserver = (int) ((headerFromsender >> 21) & 0x07);
                querytypeFromServer = (int)  ((headerFromsender >> 61) & 0x03L);
                statusCodeFromServer = String.format("%3s", Integer.toBinaryString(statusCodefromserver)).replace(' ', '0');
                String bodyString = new String(bodyBytes, StandardCharsets.UTF_8).replaceAll("\0", "");

                // actions to be taken for each query type or parameters to be received from the user according to the data coming from the server
                if(querytypeFromServer==1)
                {
                    System.out.println("Response from server: ");
                    System.out.println("Status Code: 0b" + statusCodeFromServer);
                    socket.close();
                }
                else if(querytypeFromServer==2)
                {
                    if(statusCodeFromServer.equals("000"))
                    {
                        System.out.println("File is available.");
                        System.out.println("Enter the specific date with format: year/month/day/hour/minute/second");
                        System.out.println("You must enter the year after 2020. Maximum value of year is 2083");
                        date = scanner.next();
                        String[] datePieces = date.split("/");
                        year = Integer.parseInt(datePieces[0]);
                        year = year % 100;
                        month = Integer.parseInt(datePieces[1]);
                        day = Integer.parseInt(datePieces[2]);
                        hour = Integer.parseInt(datePieces[3]);
                        minute = Integer.parseInt(datePieces[4]);
                        second = Integer.parseInt(datePieces[5]);
                        timestamp= (hour<<27) + (minute<<21) + (second<<15) + (day<<10) + (month<<6) + year;
                        // fourth packet to the server
                        header = combine(reserved1, bodyLength, reserved2, statusCode, timestamp, messageID, querytypeFromServer, messageType);
                        String fullBody = " ";
                        bodyBytes = fullBody.getBytes(StandardCharsets.UTF_8);
                        paketBytes = combineHeaderBody(header, bodyBytes);
                        outputStream2.write(paketBytes);
                    }
                    else if(statusCodeFromServer.equals("001"))
                    {
                        System.out.println("Response from server: ");
                        System.out.println("Status Code: 0b" + statusCodeFromServer);
                        socket.close();
                    }
                }

                // Extra operations that need to be done for some query types
                if(querytypeFromServer==3)
                {
                    if(statusCodeFromServer.equals("001"))
                    {
                        System.out.println("Response from server: ");
                        System.out.println("Status Code: 0b" + statusCodeFromServer);
                        socket.close();
                    }
                    else if(statusCodeFromServer.equals("111"))
                    {
                        System.out.println("Response from server: ");
                        System.out.println("Status Code: 0b" + statusCodeFromServer);
                        System.out.println(bodyString);
                        socket.close();
                    }
                }
                if((querytypeFromServer==2)||(statusCodeFromServer.equals("000")))
                {
                    // the fourth packet from the server
                    receivedPacket = new byte[300];
                    bytesRead = inputStream.read(receivedPacket);
                    if (bytesRead == -1) {
                        return;
                    }
                    System.arraycopy(receivedPacket, 0, headerBytes, 0, headerBytes.length);
                    headerFromsender = 0;
                    for (int i = 0; i < headerBytes.length; i++) {
                        headerFromsender |= (headerBytes[i] & 0xFFL) << (headerBytes.length - 1 - i) * 8;
                    }
                    statusCodefromserver = (int) ((headerFromsender >> 21) & 0x07);
                    statusCodeFromServer = String.format("%3s", Integer.toBinaryString(statusCodefromserver)).replace(' ', '0');
                    System.out.println("Response from server: ");
                    System.out.println("Status Code: 0b" + statusCodeFromServer);
                }
            }
            scanner.close();
        } catch (UnknownHostException e) {
            System.out.println("Server not found: " + e.getMessage());
        } catch (IOException e) {
            System.out.println("Situation: " + e.getMessage());
        }
    }
}
