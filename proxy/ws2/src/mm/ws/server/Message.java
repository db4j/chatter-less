
package mm.ws.server;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class Message {

    @SerializedName("event")
    @Expose
    public String event;
    @SerializedName("data")
    @Expose
    public Object data;
    @SerializedName("broadcast")
    @Expose
    public Broadcast broadcast;
    @SerializedName("seq")
    @Expose
    public long seq;

    /**
     * No args constructor for use in serialization
     * 
     */
    public Message() {
    }

    /**
     * 
     * @param broadcast
     * @param data
     * @param event
     * @param seq
     */
    public Message(String event, Object data, Broadcast broadcast, long seq) {
        super();
        this.event = event;
        this.data = data;
        this.broadcast = broadcast;
        this.seq = seq;
    }

}
