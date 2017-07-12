
package mm.rest;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class ChannelsxMembersxNotifyPropsReqs {

    @SerializedName("user_id")
    @Expose
    public String userId;
    @SerializedName("channel_id")
    @Expose
    public String channelId;
    @SerializedName("mark_unread")
    @Expose
    public String markUnread;

}
