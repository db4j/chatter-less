
package mm.rest;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class UsersStatusIdsRep {

    @SerializedName("user_id")
    @Expose
    public String userId;
    @SerializedName("status")
    @Expose
    public String status;
    @SerializedName("manual")
    @Expose
    public boolean manual;
    @SerializedName("last_activity_at")
    @Expose
    public long lastActivityAt;

}
