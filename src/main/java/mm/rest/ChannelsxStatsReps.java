
package mm.rest;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class ChannelsxStatsReps {

    @SerializedName("channel_id")
    @Expose
    public String channelId;
    @SerializedName("member_count")
    @Expose
    public int memberCount;

}
