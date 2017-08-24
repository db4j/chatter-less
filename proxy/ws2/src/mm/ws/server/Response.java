
package mm.ws.server;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class Response {

    @SerializedName("status")
    @Expose
    public String status;
    @SerializedName("seq_reply")
    @Expose
    public long seqReply;

    /**
     * No args constructor for use in serialization
     * 
     */
    public Response() {
    }

    /**
     * 
     * @param seqReply
     * @param status
     */
    public Response(String status, long seqReply) {
        super();
        this.status = status;
        this.seqReply = seqReply;
    }

}
