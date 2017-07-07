
package mm.rest;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class ChannelsReps {

    @SerializedName("id")
    @Expose
    public String id;
    @SerializedName("create_at")
    @Expose
    public int createAt;
    @SerializedName("update_at")
    @Expose
    public int updateAt;
    @SerializedName("delete_at")
    @Expose
    public int deleteAt;
    @SerializedName("team_id")
    @Expose
    public String teamId;
    @SerializedName("type")
    @Expose
    public String type;
    @SerializedName("display_name")
    @Expose
    public String displayName;
    @SerializedName("name")
    @Expose
    public String name;
    @SerializedName("header")
    @Expose
    public String header;
    @SerializedName("purpose")
    @Expose
    public String purpose;
    @SerializedName("last_post_at")
    @Expose
    public int lastPostAt;
    @SerializedName("total_msg_count")
    @Expose
    public int totalMsgCount;
    @SerializedName("extra_update_at")
    @Expose
    public int extraUpdateAt;
    @SerializedName("creator_id")
    @Expose
    public String creatorId;

}
