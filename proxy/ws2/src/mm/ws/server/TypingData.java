
package mm.ws.server;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class TypingData {

    @SerializedName("parent_id")
    @Expose
    public String parentId;
    @SerializedName("user_id")
    @Expose
    public String userId;

    /**
     * No args constructor for use in serialization
     * 
     */
    public TypingData() {
    }

    /**
     * 
     * @param userId
     * @param parentId
     */
    public TypingData(String parentId, String userId) {
        super();
        this.parentId = parentId;
        this.userId = userId;
    }

}
