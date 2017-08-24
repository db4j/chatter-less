
package mm.ws.client;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class Client {

    @SerializedName("action")
    @Expose
    public String action;
    @SerializedName("seq")
    @Expose
    public long seq;
    @SerializedName("data")
    @Expose
    public ClientData data;

    /**
     * No args constructor for use in serialization
     * 
     */
    public Client() {
    }

    /**
     * 
     * @param data
     * @param action
     * @param seq
     */
    public Client(String action, long seq, ClientData data) {
        super();
        this.action = action;
        this.seq = seq;
        this.data = data;
    }

}
