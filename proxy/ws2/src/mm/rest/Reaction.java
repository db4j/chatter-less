
package mm.rest;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class Reaction {

    @SerializedName("create_at")
    @Expose
    public long createAt;
    /**
     * 
     * (Required)
     * 
     */
    @SerializedName("post_id")
    @Expose
    public String postId;
    /**
     * 
     * (Required)
     * 
     */
    @SerializedName("emoji_name")
    @Expose
    public String emojiName;
    /**
     * 
     * (Required)
     * 
     */
    @SerializedName("user_id")
    @Expose
    public String userId;

}
