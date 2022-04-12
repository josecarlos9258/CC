import java.io.*;
import java.util.*;

public class FChunks
{
    private final List<byte[]> chunks;
    private final int chunkBytes;
    private final int nChunks;
    private int time_ms;

    public FChunks(String filename,int chunkBytes) throws IOException
    {
        byte[] fileBytes = this.readFile(filename);
        int fileSize = fileBytes.length;
        this.chunkBytes = chunkBytes;
        this.nChunks = (int) Math.ceil( (double) fileSize / this.chunkBytes );
        this.chunks = new ArrayList<>();
        this.makeChunks(fileBytes);
        this.time_ms = 0;
    }

    private byte[] readFile(String filename) throws IOException
    {
        BufferedInputStream bis = new BufferedInputStream(new FileInputStream(new File(filename)));
        return bis.readAllBytes();
    }

    private void makeChunks(byte[] bytes)
    {
        for(int id=0 ; id<this.nChunks ; id++)
        {
            int i = id*this.chunkBytes;
            int j = Math.min(i+chunkBytes,bytes.length);
            this.chunks.add(Arrays.copyOfRange(bytes,i,j));
        }
    }

    public List<byte[]> getDataChunks() { return this.chunks; }
    public int getTime(){ return this.time_ms; }
    public void addTime(int ms){ this.time_ms += ms; }
    public void resetTime(){ this.time_ms=0; }
}
