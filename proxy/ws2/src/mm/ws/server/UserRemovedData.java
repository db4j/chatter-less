
package mm.ws.server;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class UserRemovedData {

    @SerializedName("channel_id")
    @Expose
    public String channelId;
    @SerializedName("remover_id")
    @Expose
    public String removerId;
    @SerializedName("user_id")
    @Expose
    public String userId;

    /**
     * No args constructor for use in serialization
     * 
     */
    public UserRemovedData() {
    }

    /**
     * 
     * @param removerId
     * @param userId
     * @param channelId
     */
    public UserRemovedData(String channelId, String removerId, String userId) {
        super();
        this.channelId = channelId;
        this.removerId = removerId;
        this.userId = userId;
    }

}
