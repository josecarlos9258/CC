import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class FSMessage
{
    private static int intValueOfByteChar(byte c)
        { return Integer.parseInt(Character.toString(c)); }

    private static int convert4ByteToInt(byte[] n)
        { return ByteBuffer.wrap(n).getInt(); }

    public static int getType(byte[] msg)
        { return FSMessage.intValueOfByteChar(msg[0]); }

    public static int getFileID(byte[] msg)
    {
        byte[] n = Arrays.copyOfRange(msg,1,5);
        return FSMessage.convert4ByteToInt(n);
    }

    public static int getDataID(byte[] msg)
    {
        byte[] n = Arrays.copyOfRange(msg,5,9);
        return FSMessage.convert4ByteToInt(n);
    }

    public static int getDataSize(byte[] msg)
    {
        byte[] n = Arrays.copyOfRange(msg,9,13);
        return FSMessage.convert4ByteToInt(n);
    }

    public static int getFlag(byte[] msg)
        { return FSMessage.intValueOfByteChar(msg[13]); }

    public static byte[] getData(byte[] msg)
    {
        int dataSize = getDataSize(msg);
        return Arrays.copyOfRange(msg,14,14+dataSize);
    }

    public static byte[] build(int type,int fileID,int dataID,int dataBytes,int flag,byte[] msg) throws IOException
    {
        byte[] b_type = Integer.toString(type).getBytes();
        byte[] b_fileID = ByteBuffer.allocate(4).putInt(fileID).array();
        byte[] b_dataID = ByteBuffer.allocate(4).putInt(dataID).array();
        byte[] b_dataBytes = ByteBuffer.allocate(4).putInt(dataBytes).array();
        byte[] b_flag = Integer.toString(flag).getBytes();
        byte[] b_msg = msg;

        if(msg.length>1011)
            b_msg = Arrays.copyOfRange(msg,0,1010);

        ByteArrayOutputStream fsMsg = new ByteArrayOutputStream();
        fsMsg.write(b_type);
        fsMsg.write(b_fileID);
        fsMsg.write(b_dataID);
        fsMsg.write(b_dataBytes);
        fsMsg.write(b_flag);
        fsMsg.write(b_msg);

        return fsMsg.toByteArray();
    }

    public static void viewInline(byte[] payload)
    {
        System.out.print(
                "|" + Integer.toString(FSMessage.getType(payload))     +
                "|" + Integer.toString(FSMessage.getFileID(payload))   +
                "|" + Integer.toString(FSMessage.getDataID(payload))   +
                "|" + Integer.toString(FSMessage.getDataSize(payload)) +
                "|" + Integer.toString(FSMessage.getFlag(payload)) + "|"
        );

        if(FSMessage.getFlag(payload)==1)
            System.out.println(Arrays.toString(FSMessage.getData(payload)));
        else
            System.out.println("");
    }
}