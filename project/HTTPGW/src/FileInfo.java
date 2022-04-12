import java.io.*;
import java.net.Socket;
import java.util.*;
import java.util.stream.Collectors;

public class FileInfo
{
    private final int fileID;
    private final List<byte[]> chunks;
    private final Map<Socket,Integer> clients;
    private int time_ms;
    private int lastRequestedID;
    private final int chunkBytes;
    private final int nChunks;
    private int nUniqueChunks;
    private final int fileSize;


    public FileInfo(int fileID,int chunkBytes,int fileBytes)
    {
        this.fileSize = fileBytes;
        this.fileID = fileID;
        this.time_ms = 0;
        this.chunkBytes = chunkBytes;
        this.nChunks = (int) Math.ceil( (double) fileBytes / chunkBytes );
        this.chunks = new ArrayList<>(this.nChunks);
        this.clients = new HashMap<>();
        this.lastRequestedID = -1;
        this.nUniqueChunks = 0;

        for(int i=0; i<this.nChunks; i++)
            this.chunks.add(null);
    }


    public int getFileID(){ return this.fileID; }
    public int getNChunks(){ return this.nChunks; }
    public int getUniques(){ return this.nUniqueChunks; }
    public int getLast(){ return this.lastRequestedID; }
    public void addLast(){ if(this.lastRequestedID<this.nChunks) this.lastRequestedID++; }
    public void setLast(int i){ this.lastRequestedID= i;}
    public int getTime(){ return this.time_ms; }
    public void addTime(int ms){ this.time_ms += ms; }

    public int getClientsSize(){ return this.clients.size(); }
    public int getChunkBytes(){ return this.chunkBytes; }


    public void pushChunk(int id,byte[] data)
    {
        if( id<0 || id>=this.nChunks )
            return;

        if(this.chunks.get(id)==null)
        {
            this.chunks.set(id,data);
            this.nUniqueChunks++;
            this.time_ms = 0;
        }
    }

    public int getMissingStart()
    {
        int i=0;
        for(;this.chunks.get(i)!=null;i++);
        return i;
    }

    public Map.Entry<byte[],Integer> mergeChunksData(int chunksOwned) throws IOException
    {
        if(this.chunks.size()==0)
            return null;

        ByteArrayOutputStream msg = new ByteArrayOutputStream();

        int i;
        for(i = chunksOwned ; i<this.nChunks && this.chunks.get(i)!=null ; i++)
        {
            byte[] data = this.chunks.get(i);
            msg.write(data);
        }

        if(i==chunksOwned)
            return null;

        return new AbstractMap.SimpleEntry<>(msg.toByteArray(),i);
    }

    public void pushClient(Socket s)
    {
        if(!this.clients.containsKey(s))
            this.clients.put(s,0);
    }

    public void popClients()
    {
        if( this.nUniqueChunks!=this.nChunks || this.clients.size()==0 )
            return;

        Set<Map.Entry<Socket,Integer>> filteredMap
            = this.clients.entrySet().stream().filter( entry -> entry.getValue()>=this.nChunks )
            .collect(Collectors.toSet());

        for(Map.Entry<Socket,Integer> e: filteredMap)
        {
            try
            {
                e.getKey().close();
                this.clients.remove(e.getKey());
            } catch (IOException ioException) { ioException.printStackTrace(); }
        }
    }

    public void sendToClient(Socket socket)
    {
        try {
            Map.Entry<byte[], Integer> data = this.mergeChunksData(this.clients.get(socket));

            if(data==null)
                return;

            OutputStream out = socket.getOutputStream();
            out.write(data.getKey());
            out.flush();
            this.clients.replace(socket,data.getValue());
        } catch (IOException e) { e.printStackTrace(); }
    }

    public void send()
    {
        if(this.chunks==null || this.clients.isEmpty())
            return;

        this.clients.forEach( (socket,total) -> this.sendToClient(socket) );
        this.popClients();
    }

    public void show()
    {
        System.out.println(
            "[" + this.fileID + "] " +
            this.fileSize + "Bytes " +
            this.nUniqueChunks + "/" + this.nChunks +
            " Time:" + this.time_ms + " Last_Req.ID:" + this.lastRequestedID
        );

        for(Map.Entry<Socket,Integer> e: this.clients.entrySet())
        {
            System.out.println(
                e.getKey().getInetAddress() + " " +
                e.getKey().getPort() + " " +
                e.getValue()
            );
        }
        System.out.println("--");
    }
}
