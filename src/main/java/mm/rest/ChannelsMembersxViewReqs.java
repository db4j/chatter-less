
package mm.rest;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class ChannelsMembersxViewReqs {

    @SerializedName("channel_id")
    @Expose
    public String channelId;
    @SerializedName("prev_channel_id")
    @Expose
    public String prevChannelId;

}
