
package mm.ws.client;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class ClientData {

    @SerializedName("channel_id")
    @Expose
    public String channelId;
    @SerializedName("parent_id")
    @Expose
    public String parentId;

    /**
     * No args constructor for use in serialization
     * 
     */
    public ClientData() {
    }

    /**
     * 
     * @param channelId
     * @param parentId
     */
    public ClientData(String channelId, String parentId) {
        super();
        this.channelId = channelId;
        this.parentId = parentId;
    }

}
