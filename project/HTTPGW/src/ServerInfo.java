import java.net.InetAddress;
import java.net.InetSocketAddress;

public class ServerInfo
{
    private InetSocketAddress socket;
    private int time_ms;

    public ServerInfo(InetSocketAddress s)
    {
        this.time_ms = 0;
        this.socket = s;
    }

    public int getTime(){ return this.time_ms; }
    public InetSocketAddress getSocket(){ return this.socket; }
    public InetAddress getAddress(){ return this.socket.getAddress(); }
    public int getPort(){ return this.socket.getPort(); }

    public void resetTime(){ this.time_ms = 0; }
    public void addTime(int ms){ this.time_ms += ms; }
}
