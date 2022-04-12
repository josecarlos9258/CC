import java.io.*;
import java.net.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class FastFileServer
{
    private static Map<Map.Entry<Integer,Integer>,FChunks> files;
    private static List<DatagramPacket> payloads;
    private static DatagramSocket serverSocket;
    private static InetAddress gwAddr;
    private static int gwPort;

    public FastFileServer(String address,int port,String gwAddr,int gwPort) throws UnknownHostException, SocketException
    {
        FastFileServer.serverSocket = new DatagramSocket(port, InetAddress.getByName(address));
        FastFileServer.gwAddr = InetAddress.getByName(gwAddr);
        FastFileServer.gwPort = gwPort;
        FastFileServer.payloads = Collections.synchronizedList(new ArrayList<>());
        FastFileServer.files = new ConcurrentHashMap<>();
    }

    private static void listen()
    {
        while(true)
        {
            System.out.println("Listening UDP...");
            try
            {
                DatagramPacket payload = new DatagramPacket(new byte[1024],1024);
                FastFileServer.serverSocket.receive(payload);
                FSMessage.viewInline(payload.getData());
                FastFileServer.payloads.add(payload);
            } catch (IOException e) { e.printStackTrace(); }
        }
    }

    private static void timer()
    {
        System.out.println("===> Timer Started...");
        int elapsedTime = 0;
        while(true)
        {
            try
            {
                Instant start = Instant.now();
                int finalElapsedTime = elapsedTime;
                FastFileServer.files.values().forEach(f->f.addTime(finalElapsedTime));
                Thread.sleep(1000);
                Instant end = Instant.now();
                elapsedTime = (int) Duration.between(start,end).toMillis();
            } catch (InterruptedException e) { e.printStackTrace(); }
        }
    }

    private static void sendBeacon()
    {
        try
        {
            byte[] beacon = FSMessage.build(4,0,0,0,0,"".getBytes());
            FastFileServer.serverSocket.send(new DatagramPacket(beacon,beacon.length,FastFileServer.gwAddr,FastFileServer.gwPort));
            System.out.println("Beacon out!");
        }
        catch (IOException e)
        {
            e.printStackTrace();
            System.out.println("Beacon Failed!");
        }
    }

    private static void beaconer()
    {
        while(true)
        {
            try
            {
                Thread.sleep(5000);
                FastFileServer.sendBeacon();
                FastFileServer.viewFileTimers();
            } catch (InterruptedException e) { e.printStackTrace(); }
        }
    }

    private static Map.Entry<Integer,Integer> getFileID(String fileName) throws IOException
    {
        Scanner database = new Scanner(new File("database"));

        for(int fileID=0 ; database.hasNextLine() ; fileID++)
        {
            if(database.nextLine().equals(fileName))
            {
                database.close();
                int bytes = FastFileServer.fileByteCount("./files/"+fileName);
                return new AbstractMap.SimpleEntry<>(fileID,bytes);
            }
        }
        database.close();
        return new AbstractMap.SimpleEntry<>(-1,0);
    }

    private static String getFileName(int fileID) throws FileNotFoundException
    {
        Scanner database = new Scanner(new File("database"));

        while(database.hasNextLine())
        {
            String filename = database.nextLine();
            if(fileID==0)
            {
                database.close();
                return "./files/" + filename;
            }
            fileID--;
        }
        database.close();
        return null;
    }

    private static int fileByteCount(String fileName) throws IOException
    {
        BufferedInputStream bis = new BufferedInputStream(new FileInputStream(new File(fileName)));
        return bis.readAllBytes().length;
    }

    private static void sendMetaData(String fileName,int requestID) throws IOException
    {
        Map.Entry<Integer,Integer> data = FastFileServer.getFileID(fileName);
        byte[] msg = FSMessage.build(0,data.getKey(),requestID,data.getValue(),0,"".getBytes());
        FastFileServer.serverSocket.send(new DatagramPacket(msg,msg.length,FastFileServer.gwAddr,FastFileServer.gwPort));
    }

    private static void sendChunk(int fileID,int chunkID,int chunkBytes) throws IOException
    {
        Map.Entry<Integer,Integer> key = new AbstractMap.SimpleEntry<>(fileID,chunkBytes);
        if(!FastFileServer.files.containsKey(key))
        {
            String fileName = FastFileServer.getFileName(fileID);
            if(fileName==null)
                return;
            FastFileServer.files.put(key,new FChunks(fileName,chunkBytes));
        }

        if(chunkID<0 || chunkID>=FastFileServer.files.get(key).getDataChunks().size())
            return;

        byte[] data = FastFileServer.files.get(key).getDataChunks().get(chunkID);
        byte[] msg = FSMessage.build(1,fileID,chunkID,data.length,1,data);
        FastFileServer.serverSocket.send(new DatagramPacket(msg,msg.length,FastFileServer.gwAddr,FastFileServer.gwPort));
        FastFileServer.files.get(key).resetTime();
    }

    private static void parsePayload() throws IOException
    {
        if(FastFileServer.payloads.isEmpty())
            return;

        byte[] data = FastFileServer.payloads.get(0).getData();
        FastFileServer.payloads.remove(0);

        switch (FSMessage.getType(data))
        {
            case 0:
                String httpRequest = new String(FSMessage.getData(data));
                String[] file = httpRequest.split("/");

                String fileName = file[file.length-1];
                System.out.println("REQUESTED FILE: " + fileName);
                FastFileServer.sendMetaData(fileName,FSMessage.getDataID(data));
                break;
            case 1:
                int fileID     = FSMessage.getFileID(data);
                int chunkID    = FSMessage.getDataID(data);
                int chunkBytes = FSMessage.getDataSize(data);
                FastFileServer.sendChunk(fileID,chunkID,chunkBytes);
                break;
        }
    }

    private static void removeInactives(int ms)
    {
        while(true)
        {
            try
            {
                Thread.sleep(10000);
                System.out.println(">> Removing Inactives...!");
                for(Map.Entry<Map.Entry<Integer,Integer>,FChunks> entry: FastFileServer.files.entrySet())
                    if(entry.getValue().getTime()>=ms)
                        FastFileServer.files.remove(entry.getKey());
            } catch (InterruptedException e) { e.printStackTrace(); }
        }
    }

    private static void viewFileTimers()
    {
        FastFileServer.files.forEach((k,v)-> System.out.println("ID: " + k.getKey() + "-> " + v.getTime() + " ms"));
    }

    public void start()
    {
        new Thread(FastFileServer::listen).start();
        new Thread(FastFileServer::timer).start();
        new Thread(() -> FastFileServer.removeInactives(30000)).start();
        new Thread(FastFileServer::beaconer).start();

        FastFileServer.sendBeacon();
        while(true)
        {
            try { FastFileServer.parsePayload(); }
                catch (IOException e)
                    { e.printStackTrace(); }
        }
    }

    public static void main(String[] args) throws SocketException, UnknownHostException
    {
        FastFileServer ffs;

        if(args.length!=3)
            ffs = new FastFileServer("127.0.0.1",2000,"127.0.0.1",3000);
        else
            ffs = new FastFileServer(args[2],2000,args[0], Integer.parseInt(args[1]) );

        ffs.start();
    }
}
