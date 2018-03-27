
package mm.rest;

import java.util.ArrayList;
import java.util.List;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class ApiV3TeamsXxxChannelsXxxPostsCreatePOSTReqs {

    @SerializedName("file_ids")
    @Expose
    public List<String> fileIds = new ArrayList<String>();
    @SerializedName("message")
    @Expose
    public String message;
    @SerializedName("channel_id")
    @Expose
    public String channelId;
    @SerializedName("pending_post_id")
    @Expose
    public String pendingPostId;
    @SerializedName("user_id")
    @Expose
    public String userId;
    @SerializedName("create_at")
    @Expose
    public long createAt;

}
