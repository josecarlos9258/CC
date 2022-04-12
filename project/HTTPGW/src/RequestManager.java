import java.net.Socket;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

class PendingRequest
{
    private final Socket clientSocket;
    private final String fileName;
    private int time_ms;
    private final int requestID;

    public PendingRequest(Socket s, String file, int id)
    {
        this.clientSocket = s;
        this.fileName = file;
        this.time_ms = 0;
        this.requestID = id;
    }

    public String getFileName(){ return this.fileName; }
    public Socket getClientSocket(){ return this.clientSocket; }
    public int getTime(){ return this.time_ms; }
    public void resetTime(){ this.time_ms = 0; }
    public void addTime(int ms){ this.time_ms += ms; }
    public int getRequestID(){ return this.requestID; }

    public void show()
    {
        System.out.println(
            this.requestID + " " +
            this.fileName + " " +
            this.clientSocket.getInetAddress() + " " +
            this.clientSocket.getPort() + " " +
            this.time_ms
        );
    }
}

public class RequestManager
{
    private final Map<Integer,PendingRequest> onHoldRequests; // <ID Request,PendingRequest>

    public RequestManager() { this.onHoldRequests = new ConcurrentHashMap<>(); }

    public PendingRequest getRequest(int id) { return this.onHoldRequests.get(id); }
    public void addTime(int ms) { this.onHoldRequests.forEach((k,v)->v.addTime(ms)); }

    public boolean pushRequest(int requestID,Socket clientSocket,String filename)
    {
        if(this.onHoldRequests.containsKey(requestID))
            return false;

        for(PendingRequest r: this.onHoldRequests.values())
            if(r.getClientSocket().getInetAddress()==clientSocket.getInetAddress()
                    && r.getClientSocket().getPort()==clientSocket.getPort())
                return false;

        this.onHoldRequests.put(requestID,new PendingRequest(clientSocket,filename,requestID));
        return true;
    }

    public void popRequest(int requestID)
    {
        if(this.onHoldRequests.containsKey(requestID))
            this.onHoldRequests.remove(requestID);
    }

    public void resetRequestTime(int id)
    {
        if(this.onHoldRequests.containsKey(id))
            this.onHoldRequests.get(id).resetTime();
    }

    public Set<Integer> getInactives(int ms)
    {
        Set<Integer> missing = new HashSet<>();

        for(PendingRequest r: this.onHoldRequests.values())
            if(r.getTime()>=ms)
                missing.add(r.getRequestID());
        return missing;
    }

    public void show()
    {
        System.out.println("================== [ REQUESTS ] ===================");
        for(PendingRequest r: this.onHoldRequests.values())
            r.show();
        System.out.println("===================================================");
    }
}
