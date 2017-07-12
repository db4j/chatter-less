
package mm.rest;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class ChannelsxMembersReps {

    @SerializedName("channel_id")
    @Expose
    public String channelId;
    @SerializedName("user_id")
    @Expose
    public String userId;
    @SerializedName("roles")
    @Expose
    public String roles;
    @SerializedName("last_viewed_at")
    @Expose
    public int lastViewedAt;
    @SerializedName("msg_count")
    @Expose
    public int msgCount;
    @SerializedName("mention_count")
    @Expose
    public int mentionCount;
    @SerializedName("notify_props")
    @Expose
    public NotifyProps notifyProps;
    @SerializedName("last_update_at")
    @Expose
    public int lastUpdateAt;

}
