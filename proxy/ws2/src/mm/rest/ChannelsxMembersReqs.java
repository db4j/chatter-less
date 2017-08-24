
package mm.rest;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class ChannelsxMembersReqs {

    @SerializedName("user_id")
    @Expose
    public String userId;
    @SerializedName("channel_id")
    @Expose
    public String channelId;

}
