
package mm.ws.server;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class NewUserData {

    @SerializedName("user_id")
    @Expose
    public String userId;

    /**
     * No args constructor for use in serialization
     * 
     */
    public NewUserData() {
    }

    /**
     * 
     * @param userId
     */
    public NewUserData(String userId) {
        super();
        this.userId = userId;
    }

}
