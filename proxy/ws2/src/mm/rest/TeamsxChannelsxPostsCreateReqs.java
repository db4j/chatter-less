
package mm.rest;

import java.util.ArrayList;
import java.util.List;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class TeamsxChannelsxPostsCreateReqs {

    @SerializedName("file_ids")
    @Expose
    public List<Object> fileIds = new ArrayList<Object>();
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
    @SerializedName("parent_id")
    @Expose
    public String parentId;
    @SerializedName("root_id")
    @Expose
    public String rootId;

}
