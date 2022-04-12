import java.net.Socket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class FileManager
{
    private final Map<Integer,FileInfo> files; // <ID FIle,FileInfo>

    public FileManager()
        { this.files = new ConcurrentHashMap<>(); }

    public int fileCount()
        { return this.files.size(); }

    public ArrayList<FileInfo> getFiles()
        { return new ArrayList<>(this.files.values()); }

    public void pushFile(int id,int chunkBytes,int fileBytes)
    {
        if(this.files.containsKey(id))
            return;
        this.files.put(id,new FileInfo(id,chunkBytes,fileBytes));
    }

    public void pushClient(int id, Socket clientSocket)
    {
        if(!this.files.containsKey(id))
            return;
        this.files.get(id).pushClient(clientSocket);
    }

    public void pushChunk(int fileId,int chunkID,byte[] data)
    {
        if(!this.files.containsKey(fileId))
            return;
        this.files.get(fileId).pushChunk(chunkID,data);
    }

    public void addTime(int time_ms)
        { this.files.values().forEach(file->file.addTime(time_ms)); }

    public void addLast(int fileID)
    {
        if(this.files.containsKey(fileID))
            this.files.get(fileID).addLast();
    }

    public void popFiles()
    {
        for(Map.Entry<Integer,FileInfo> entry: this.files.entrySet())
            if( entry.getValue().getClientsSize()==0 )
                this.files.remove(entry.getKey());
    }

    public void sendAll()
    {
        for(FileInfo file: this.files.values())
            file.send();
        this.popFiles();
    }

    public void show()
    {
        System.out.println("==================== [ FILES ] ====================");
        for(FileInfo f: this.files.values())
            f.show();
        System.out.println("===================================================");
    }
}
