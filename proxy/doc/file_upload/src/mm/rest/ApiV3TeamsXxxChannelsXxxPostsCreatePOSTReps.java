
package mm.rest;

import java.util.ArrayList;
import java.util.List;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class ApiV3TeamsXxxChannelsXxxPostsCreatePOSTReps {

    @SerializedName("id")
    @Expose
    public String id;
    @SerializedName("create_at")
    @Expose
    public long createAt;
    @SerializedName("update_at")
    @Expose
    public long updateAt;
    @SerializedName("edit_at")
    @Expose
    public long editAt;
    @SerializedName("delete_at")
    @Expose
    public long deleteAt;
    @SerializedName("is_pinned")
    @Expose
    public boolean isPinned;
    @SerializedName("user_id")
    @Expose
    public String userId;
    @SerializedName("channel_id")
    @Expose
    public String channelId;
    @SerializedName("root_id")
    @Expose
    public String rootId;
    @SerializedName("parent_id")
    @Expose
    public String parentId;
    @SerializedName("original_id")
    @Expose
    public String originalId;
    @SerializedName("message")
    @Expose
    public String message;
    @SerializedName("type")
    @Expose
    public String type;
    @SerializedName("props")
    @Expose
    public Props props;
    @SerializedName("hashtags")
    @Expose
    public String hashtags;
    @SerializedName("file_ids")
    @Expose
    public List<String> fileIds = new ArrayList<String>();
    @SerializedName("pending_post_id")
    @Expose
    public String pendingPostId;

}
