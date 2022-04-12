import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ServerManager
{
    private final Map<InetSocketAddress,ServerInfo> serverPool;

    public ServerManager()
        { this.serverPool = new ConcurrentHashMap<>(); }

    public int getPoolSize()
        { return this.serverPool.size(); }

    public void pushServer(InetSocketAddress serverSocket)
    {
        if(this.serverPool.containsKey(serverSocket))
            return;

        this.serverPool.put(serverSocket,new ServerInfo(serverSocket));
    }

    public void addTime(int ms)
        { this.serverPool.values().forEach(server->server.addTime(ms)); }

    public void resetServerTime(InetSocketAddress serverSocket)
    {
        if(this.serverPool.containsKey(serverSocket))
            this.serverPool.get(serverSocket).resetTime();
    }

    public InetSocketAddress chooseServer()
    {
        if(this.serverPool.isEmpty())
            return null;

        return this.serverPool.values().stream().sorted((s1,s2) -> (s2.getTime()-s1.getTime())).
            iterator().next().getSocket();
    }

    public void removeInactives(int time_ms)
    {
        for(Map.Entry<InetSocketAddress,ServerInfo> entry: this.serverPool.entrySet())
        {
            if(entry.getValue().getTime()>=time_ms)
                this.serverPool.remove(entry.getKey());
        }
    }

    public void show()
    {
        System.out.println("================= [ SERVER POOL ] =================");
        for(ServerInfo s: this.serverPool.values())
        {
            System.out.println(
                s.getAddress() + " " +
                s.getPort() + " " +
                s.getTime()
            );
        }
        System.out.println("===================================================");
    }
}
