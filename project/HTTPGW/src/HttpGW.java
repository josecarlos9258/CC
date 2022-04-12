import java.io.*;
import java.net.*;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class HttpGW
{
    // ################################ V A R S #####################################

    private static int requestID;

    private static FileManager files;
    private static ServerManager servers;
    private static RequestManager idleQueue;
    private static List<DatagramPacket> payloads;

    private static ServerSocket tcpSocket;
    private static DatagramSocket udpSocket;

    // ############################## M E T H O D S ##################################

    public HttpGW(String address, int udpPort, int tcpPort) throws IOException
    {
        HttpGW.requestID = 0;
        HttpGW.files = new FileManager();
        HttpGW.servers = new ServerManager();
        HttpGW.idleQueue = new RequestManager();
        HttpGW.udpSocket = new DatagramSocket(udpPort, InetAddress.getByName(address));
        HttpGW.tcpSocket = new ServerSocket(tcpPort);
        HttpGW.payloads = Collections.synchronizedList(new ArrayList<>());
    }

    public static void parsePayload()
    {
        if(HttpGW.payloads.isEmpty())
            return;

        byte[] msg = HttpGW.payloads.get(0).getData();
        InetAddress serverAddr = HttpGW.payloads.get(0).getAddress();
        int serverPort = HttpGW.payloads.get(0).getPort();

        HttpGW.payloads.remove(0);

        int tipo = FSMessage.getType(msg);
        int fileID = FSMessage.getFileID(msg);
        int dataID = FSMessage.getDataID(msg);
        int dataBytes = FSMessage.getDataSize(msg);

        switch (tipo)
        {
            case 1: // Chunks
                byte[] data = FSMessage.getData(msg);
                HttpGW.files.pushChunk(fileID,dataID,data);
                break;
            case 0: // File ID
                Socket clientSocket = HttpGW.idleQueue.getRequest(dataID).getClientSocket();
                HttpGW.idleQueue.popRequest(dataID);

                try
                {
                    OutputStream out = clientSocket.getOutputStream();

                    if(fileID==-1)
                    {
                        out.write("HTTP/1.1 404 Not Found\r\n".getBytes());
                        out.flush();
                        clientSocket.close();
                        break;
                    }

                    out.write("HTTP/1.1 200 OK\r\nContent-Length: ".getBytes());
                    out.write(Integer.toString(dataBytes).getBytes());
                    out.write("\r\n\r\n".getBytes());
                    out.flush();
                } catch (IOException e) { e.printStackTrace(); break;}

                HttpGW.files.pushFile(fileID,1010,dataBytes);
                HttpGW.files.pushClient(fileID,clientSocket);
                break;
            case 4:
                System.out.println(">> Beacon In");
                HttpGW.servers.pushServer(new InetSocketAddress(serverAddr,serverPort));
                break;
        }
        HttpGW.servers.resetServerTime(new InetSocketAddress(serverAddr,serverPort));
    }

    public static void timer()
    {
        System.out.println("===> Timer Started...");
        int elapsedTime = 0;
        while(true)
        {
            try
            {
                Instant start = Instant.now();
                HttpGW.files.addTime(elapsedTime);
                HttpGW.servers.addTime(elapsedTime);
                HttpGW.idleQueue.addTime(elapsedTime);
                Thread.sleep(1000);
                Instant end = Instant.now();
                elapsedTime = (int) Duration.between(start,end).toMillis();
            } catch (InterruptedException e) { e.printStackTrace(); }
        }
    }

    public static void listenUDP()
    {
        System.out.println("===> Listening on UDP...");
        while(true)
        {
            try
            {
                byte[] msg = new byte[1024];
                DatagramPacket payload = new DatagramPacket(msg,msg.length);
                HttpGW.udpSocket.receive(payload);
                HttpGW.payloads.add(payload);
            }
            catch (IOException e)
            {
                if(e instanceof SocketTimeoutException){}
                    else e.printStackTrace();
            }
        }
    }

    public static void listenTCP()
    {
        System.out.println("===> LISTENING on TCP...");
        while(true)
        {
            try
            {
                Socket clientSocket = HttpGW.tcpSocket.accept();
                BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                String data = in.readLine();
                System.out.println("HTTP_Request: " + data);
                String fileName = data.split(" ")[1].substring(1);

                System.out.println(">> Received request for " + fileName);
                boolean isPushed = HttpGW.idleQueue.pushRequest(HttpGW.requestID,clientSocket,fileName);

                if(!isPushed)
                    continue;

                byte[] fileRequest = FSMessage.build(0,0,HttpGW.requestID,fileName.length(),1,fileName.getBytes());
                InetSocketAddress server = HttpGW.servers.chooseServer();

                if(server==null)
                    continue;

                HttpGW.udpSocket.send(new DatagramPacket(fileRequest,fileRequest.length,server.getAddress(),server.getPort()));
                HttpGW.requestID = HttpGW.requestID==Integer.MAX_VALUE? 0 : HttpGW.requestID+1;
            }
            catch (IOException e)
            {
                if(e instanceof SocketTimeoutException){}
                    else e.printStackTrace();
            }
        }
    }

    public static void parseUDPPayloads()
    {
        System.out.println("===> PARSER THREAD STARTED");
        while(true)
            HttpGW.parsePayload();
    }

    public static void fetcher()
    {
        System.out.println("===> FETCHER STARTED");
        while(true)
        {
            try
            {
                int nServers = HttpGW.servers.getPoolSize();

                if(HttpGW.files.fileCount()==0)
                    continue;

                for(FileInfo f: HttpGW.files.getFiles())
                {
                    if(f.getTime()>=10000 && f.getUniques()<f.getNChunks())
                        f.setLast(f.getMissingStart()-1);

                    for(int i=1 ; i<=nServers && f.getLast()<f.getNChunks() && f.getUniques()<f.getNChunks() ; i++)
                    {
                        byte[] msg = FSMessage.build(1,f.getFileID(),f.getLast()+1,f.getChunkBytes(),0,"".getBytes());
                        InetSocketAddress server = HttpGW.servers.chooseServer();
                        HttpGW.udpSocket.send(new DatagramPacket(msg,msg.length,server));
                        HttpGW.files.addLast(f.getFileID());
                        Thread.sleep(nServers*3);
                    }
                }
            } catch (InterruptedException | IOException e) { e.printStackTrace(); }
        }
    }

    public static void sendInactiveRequests(int ms)
    {
        for(Integer id: HttpGW.idleQueue.getInactives(ms))
        {
            try
            {
                String fileName = HttpGW.idleQueue.getRequest(id).getFileName();
                byte[] msg = FSMessage.build(0,0,id,fileName.length(),1,fileName.getBytes());
                InetSocketAddress server = HttpGW.servers.chooseServer();

                if(server==null)
                    continue;

                HttpGW.udpSocket.send(new DatagramPacket(msg,msg.length,server));
                HttpGW.idleQueue.resetRequestTime(id);
            } catch (IOException e)
                { e.printStackTrace(); }
        }
    }

    public void start()
    {
        new Thread(HttpGW::timer).start();
        new Thread(HttpGW::parseUDPPayloads).start();
        new Thread(HttpGW::listenUDP).start();
        new Thread(HttpGW::listenTCP).start();
        new Thread(HttpGW::fetcher).start();

        while(true)
        {
            HttpGW.servers.removeInactives(15000);
            HttpGW.sendInactiveRequests(10000);

            HttpGW.files.show();
            HttpGW.servers.show();
            HttpGW.idleQueue.show();
            System.out.println("\n\n");

            try
            {
                Thread.sleep(5000);
                HttpGW.files.sendAll();
            } catch (InterruptedException e)
                { e.printStackTrace(); }
        }
    }

    public static void main(String[] args) throws IOException
    {
        HttpGW gw;
        String localhost = "127.0.0.1";
        String ip = "10.1.1.1";

        if(args.length!=3)
            gw = new HttpGW(ip,3000,8080);
        else
            gw = new HttpGW(args[0],Integer.parseInt(args[1]),Integer.parseInt(args[2]));

        gw.start();
    }
}
