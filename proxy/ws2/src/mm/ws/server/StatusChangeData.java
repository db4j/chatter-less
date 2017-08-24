
package mm.ws.server;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class StatusChangeData {

    @SerializedName("status")
    @Expose
    public String status;
    @SerializedName("user_id")
    @Expose
    public String userId;

    /**
     * No args constructor for use in serialization
     * 
     */
    public StatusChangeData() {
    }

    /**
     * 
     * @param userId
     * @param status
     */
    public StatusChangeData(String status, String userId) {
        super();
        this.status = status;
        this.userId = userId;
    }

}
