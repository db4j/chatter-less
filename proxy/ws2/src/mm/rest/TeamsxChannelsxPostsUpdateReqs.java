
package mm.rest;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class TeamsxChannelsxPostsUpdateReqs {

    @SerializedName("message")
    @Expose
    public String message;
    @SerializedName("id")
    @Expose
    public String id;
    @SerializedName("channel_id")
    @Expose
    public String channelId;

}
