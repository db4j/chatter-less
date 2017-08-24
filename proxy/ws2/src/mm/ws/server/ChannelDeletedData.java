
package mm.ws.server;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class ChannelDeletedData {

    @SerializedName("channel_id")
    @Expose
    public String channelId;

    /**
     * No args constructor for use in serialization
     * 
     */
    public ChannelDeletedData() {
    }

    /**
     * 
     * @param channelId
     */
    public ChannelDeletedData(String channelId) {
        super();
        this.channelId = channelId;
    }

}
