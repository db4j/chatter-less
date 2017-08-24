
package mm.ws.server;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class ChannelCreatedData {

    @SerializedName("channel_id")
    @Expose
    public String channelId;
    @SerializedName("team_id")
    @Expose
    public String teamId;

    /**
     * No args constructor for use in serialization
     * 
     */
    public ChannelCreatedData() {
    }

    /**
     * 
     * @param teamId
     * @param channelId
     */
    public ChannelCreatedData(String channelId, String teamId) {
        super();
        this.channelId = channelId;
        this.teamId = teamId;
    }

}
