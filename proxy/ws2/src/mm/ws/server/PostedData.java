
package mm.ws.server;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class PostedData {

    @SerializedName("channel_display_name")
    @Expose
    public String channelDisplayName;
    @SerializedName("channel_name")
    @Expose
    public String channelName;
    @SerializedName("channel_type")
    @Expose
    public String channelType;
    @SerializedName("post")
    @Expose
    public String post;
    @SerializedName("sender_name")
    @Expose
    public String senderName;
    @SerializedName("team_id")
    @Expose
    public String teamId;
    @SerializedName("mentions")
    @Expose
    public String mentions;

    /**
     * No args constructor for use in serialization
     * 
     */
    public PostedData() {
    }

    /**
     * 
     * @param senderName
     * @param channelDisplayName
     * @param post
     * @param teamId
     * @param mentions
     * @param channelName
     * @param channelType
     */
    public PostedData(String channelDisplayName, String channelName, String channelType, String post, String senderName, String teamId, String mentions) {
        super();
        this.channelDisplayName = channelDisplayName;
        this.channelName = channelName;
        this.channelType = channelType;
        this.post = post;
        this.senderName = senderName;
        this.teamId = teamId;
        this.mentions = mentions;
    }

}
